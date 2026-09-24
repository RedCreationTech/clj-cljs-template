(ns com.ruoyi.infra.online-test
  "在线会话管理测试。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.infra.kv :as kv]
   [com.ruoyi.infra.online :as online]))

(use-fixtures :each
  (fn [f]
    (kv/use-store! (kv/memory-store))
    (reset! @#'online/query-fn-atom nil)
    (f)
    (reset! @#'online/query-fn-atom nil)))

(defn- make-mock-query-fn
  "返回 [query-fn calls-atom]。query-fn 根据 query key 返回预设值，
   并将每次调用记录到 calls-atom。"
  [& {:keys [list-return count-return update-return rename-return]
      :or {list-return [] count-return {:total 0} update-return 1 rename-return 1}}]
  (let [calls (atom [])]
    [(fn [q params]
       (swap! calls conj [q params])
       (case q
         :create-online-user! 1
         :update-online-user! update-return
         :rename-online-session! rename-return
         :delete-online-user! 1
         :delete-idle-online-users! 0
         :list-online-users list-return
         :count-online-users count-return
         nil))
     calls]))

(defn- called [calls q] (filter #(= q (first %)) @calls))

(deftest test-set-query-fn!
  (testing "注入 query-fn 后查询生效"
    (let [[mock-fn calls] (make-mock-query-fn :count-return {:total 0})]
      (online/set-query-fn! mock-fn)
      (online/list-online)
      (is (= 1 (count (filter #(= :list-online-users (first %)) @calls))))
      (is (= 1 (count (filter #(= :count-online-users (first %)) @calls)))))))

(deftest test-register!
  (testing "注册在线用户写入数据库"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      ;; 避免测试启动真实调度线程
      (with-redefs [online/cleanup-executor (delay nil)]
        (is (nil? (online/register! "session-1" "admin" "192.168.1.1")))
        (is (= 1 (count (filter #(= :create-online-user! (first %)) @calls))))
        (let [[_ params] (first (filter #(= :create-online-user! (first %)) @calls))]
          (is (= "session-1" (:session_id params)))
          (is (= "admin" (:login_name params)))
          (is (= "192.168.1.1" (:ipaddr params)))
          (is (= "on_line" (:status params)))
          (is (number? (:start_timestamp params)))
          (is (number? (:last_access_time params)))
          (is (number? (:expire_time params))))))))

(deftest test-heartbeat!
  (testing "更新心跳时间,命中会话返回 true"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      (is (true? (online/heartbeat! "session-1")))
      (let [[_ params] (first (called calls :update-online-user!))]
        (is (= "session-1" (:session_id params)))
        (is (number? (:last_access_time params)))
        (is (nil? (:status params)))
        (is (nil? (:expire_time params))))))
  (testing "会话已被删除(空闲超时/登出/强退)返回 false"
    (online/set-query-fn! (first (make-mock-query-fn :update-return 0)))
    (is (false? (online/heartbeat! "gone"))))
  (testing "数据库异常时放行,避免抖动把所有人踢下线"
    (online/set-query-fn! (fn [_ _] (throw (Exception. "db down"))))
    (is (true? (online/heartbeat! "session-1")))))

(deftest test-active-and-rotate
  (testing "续期后旧会话在宽限期内有效,不再依赖心跳"
    (let [[mock-fn calls] (make-mock-query-fn :update-return 0)]
      (online/set-query-fn! mock-fn)
      (is (true? (online/rotate! "old" "new")))
      (is (= {:old_session_id "old" :session_id "new"}
             (select-keys (second (first (called calls :rename-online-session!))) [:old_session_id :session_id])))
      (is (true? (online/active? "old")))
      (is (false? (online/active? "other")))))
  (testing "旧会话不存在时续期失败"
    (online/set-query-fn! (first (make-mock-query-fn :rename-return 0)))
    (is (false? (online/rotate! "missing" "new")))
    (is (not (online/in-grace? "missing")))))

(deftest test-heartbeat-nil-session
  (testing "没有 session-id 不触发数据库操作且视为无效"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      (is (false? (online/heartbeat! nil)))
      (is (empty? @calls)))))

(deftest test-unregister!
  (testing "注销删除会话"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      (is (nil? (online/unregister! "s-1")))
      (is (= [[:delete-online-user! {:session_id "s-1"}]] @calls)))))

(deftest test-unregister-nil-session
  (testing "nil 不触发任何操作"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      (is (nil? (online/unregister! nil)))
      (is (empty? @calls)))))

(deftest test-cleanup-expired-sessions!
  (testing "按空闲阈值一条 SQL 删除过期会话"
    (let [[mock-fn calls] (make-mock-query-fn)
          before (System/currentTimeMillis)]
      (online/set-query-fn! mock-fn)
      (is (nil? (online/cleanup-expired-sessions!)))
      (let [[_ {:keys [last_access_time]}] (first (called calls :delete-idle-online-users!))]
        (is (<= (- before online/idle-timeout-ms) last_access_time (- (System/currentTimeMillis) online/idle-timeout-ms)))))))

(deftest test-list-online
  (testing "查询在线用户列表并映射字段"
    (let [row {:session_id "s1"
               :login_name "admin"
               :ipaddr "127.0.0.1"
               :start_timestamp 1000
               :last_access_time 2000}
          [mock-fn calls] (make-mock-query-fn
                           :list-return [row]
                           :count-return {:total 1})]
      (online/set-query-fn! mock-fn)
      (let [result (online/list-online
                    :login-name "admin"
                    :ipaddr "127"
                    :page-num 1
                    :page-size 10)]
        (is (= 1 (:total result)))
        (is (= 1 (count (:rows result))))
        (let [mapped (first (:rows result))]
          (is (= "s1" (:token-id mapped)))
          (is (= "s1" (:token mapped)))
          (is (= "admin" (:user-id mapped)))
          (is (= "admin" (:user-name mapped)))
          (is (= "127.0.0.1" (:login-ip mapped)))
          (is (= 1000 (:login-time mapped)))
          (is (= 2000 (:last-access mapped))))
        (let [[_ list-params] (first (filter #(= :list-online-users (first %)) @calls))
              [_ count-params] (first (filter #(= :count-online-users (first %)) @calls))]
          (is (= "admin" (:login_name list-params)))
          (is (= "127" (:ipaddr list-params)))
          (is (= 10 (:page_size list-params)))
          (is (= 0 (:offset list-params)))
          (is (= "admin" (:login_name count-params)))
          (is (= "127" (:ipaddr count-params))))))))

(deftest test-force-logout!
  (testing "强退删除会话"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      (is (= {:success true} (online/force-logout! "s-1")))
      (is (= [[:delete-online-user! {:session_id "s-1"}]] @calls)))))

(deftest test-force-logout-nil-token
  (testing "nil token 强退无操作但返回成功"
    (let [[mock-fn calls] (make-mock-query-fn)]
      (online/set-query-fn! mock-fn)
      (is (= {:success true} (online/force-logout! nil)))
      (is (empty? @calls)))))
