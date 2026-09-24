(ns com.ruoyi.web.middleware.auth-test
  "认证与授权中间件测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.security :as security]
   [com.ruoyi.web.middleware.auth :as auth]))

(deftest test-wrap-jwt-auth-with-active-session
  (testing "令牌合法且会话在线:附加 identity(会话检查即心跳)"
    (let [calls (atom [])]
      (with-redefs [online/active? (fn [jti] (swap! calls conj jti) true)]
        (let [token (security/generate-token 1 "admin" [1] :jti "s-1")
              handler (auth/wrap-jwt-auth (fn [req] {:identity (:identity req)}))
              response (handler {:headers {"authorization" (str "Bearer " token)}})]
          (is (= 1 (get-in response [:identity :user-id])))
          (is (= "s-1" (get-in response [:identity :jti])))
          (is (= ["s-1"] @calls)))))))

(deftest test-wrap-jwt-auth-with-ended-session
  (testing "令牌签名有效但会话已结束(登出/强退/空闲超时):不附加 identity"
    (with-redefs [online/active? (fn [_] false)]
      (let [token (security/generate-token 1 "admin" [1])
            handler (auth/wrap-jwt-auth (fn [req] {:identity (:identity req)}))]
        (is (nil? (:identity (handler {:headers {"authorization" (str "Bearer " token)}}))))))))

(deftest test-wrap-jwt-auth-with-expired-token
  (testing "过期令牌不附加 identity,也不查会话"
    (with-redefs [online/active? (fn [_] (throw (Exception. "不应调用")))]
      (let [token (security/generate-token 1 "admin" [1] :ttl-minutes -1)
            handler (auth/wrap-jwt-auth (fn [req] {:identity (:identity req)}))]
        (is (nil? (:identity (handler {:headers {"authorization" (str "Bearer " token)}}))))))))

(deftest test-wrap-jwt-auth-with-invalid-token
  (testing "无效 Token 不附加 identity"
    (with-redefs [online/active? (fn [_] (throw (Exception. "不应调用")))]
      (let [handler (auth/wrap-jwt-auth (fn [req] {:identity (:identity req)}))
            response (handler {:headers {"authorization" "Bearer invalid-token"}})]
        (is (nil? (:identity response)))))))

(deftest test-wrap-jwt-auth-without-token
  (testing "无 Token 时不附加 identity"
    (with-redefs [online/active? (fn [_] (throw (Exception. "不应调用")))]
      (let [handler (auth/wrap-jwt-auth (fn [req] {:identity (:identity req)}))
            response (handler {})]
        (is (nil? (:identity response)))))))

;; ── authorize:按路由数据鉴权 ─────────────────────────────────────

(defn- query-fn-with
  "模拟 query-fn:按用户返回 :list-user-role-perms 的行。"
  [rows-by-user]
  (fn [query params]
    (is (= :list-user-role-perms query))
    (get rows-by-user (:user_id params) [])))

(def ^:private qf
  (query-fn-with {1 [{:role_key "admin" :perms nil}]
                  2 [{:role_key "viewer" :perms "system:user:list"}
                     {:role_key "viewer" :perms "system:user:query,system:user:export"}]}))

(defn- request-as [user-id]
  (cond-> {:components {:query-fn qf}}
    user-id (assoc :identity {:user-id user-id})))

(defn- compile-authorize
  "按 reitit 的方式编译 authorize:返回包装后的 handler,未声明规则时返回 nil。"
  [data]
  (when-let [mw ((:compile auth/authorize) data {})]
    (mw (fn [_] {:status 200 :body :ok}))))

(deftest test-authorize-skips-routes-without-rules
  (testing "没有 :auth? / :perms 的路由不包装(匿名接口零开销)"
    (is (nil? (compile-authorize {})))
    (is (nil? (compile-authorize {:summary "登录"})))))

(deftest test-authorize-requires-login
  (let [h (compile-authorize {:auth? true})]
    (testing "未登录 401"
      (let [response (h (request-as nil))]
        (is (= 401 (:status response)))
        (is (= {:code 401 :msg "未登录或令牌已过期"} (:body response)))
        (is (= "application/json" (get-in response [:headers "Content-Type"])))))
    (testing "登录即可,不查权限"
      (is (= 200 (:status (h {:identity {:user-id 9}})))))))

(deftest test-authorize-checks-perms
  (let [h (compile-authorize {:auth? true :perms "system:user:add"})]
    (testing "缺少权限 403"
      (let [response (h (request-as 2))]
        (is (= 403 (:status response)))
        (is (= {:code 403 :msg "没有操作权限"} (:body response)))))
    (testing "admin 角色拥有全部权限"
      (is (= 200 (:status (h (request-as 1))))))
    (testing "声明了 :perms 但未登录:先返回 401"
      (is (= 401 (:status (h (request-as nil))))))))

(deftest test-authorize-any-of
  (testing ":perms 为集合时满足任一即可;一个菜单的 perms 可用逗号写多个"
    (is (= 200 (:status ((compile-authorize {:perms ["system:role:list" "system:user:export"]}) (request-as 2)))))
    (is (= 403 (:status ((compile-authorize {:perms ["system:role:list" "system:user:add"]}) (request-as 2)))))))
