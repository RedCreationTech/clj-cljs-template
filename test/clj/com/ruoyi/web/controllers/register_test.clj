(ns com.ruoyi.web.controllers.register-test
  "用户注册控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controllers.register :as register]
   [com.ruoyi.web.controller-test-helper :as eh]))

(def mock-user-service
  "模拟用户领域服务，支持注册成功场景所需的查询。"
  {:query-fn (fn [q _p]
               (case q
                 :find-user-by-name nil
                 :create-user! nil
                 :last-insert-rowid {:last_insert_rowid 1}
                 nil))})

(def enabled {:register-enabled? true})

(defn- register! [service body & [config]]
  (register/register {:user-service service :auth-config (or config enabled)} {:body-params body}))

(deftest test-register-success
  (testing "新用户注册成功"
    (let [response (register! mock-user-service {:username "newuser" :password "123456"})]
      (is (= 200 (:status response)))
      (is (= 200 (get-in response [:body :code])))
      (is (= "注册成功" (get-in response [:body :msg]))))))

(deftest test-register-disabled-by-default
  (testing "默认关闭注册"
    (let [response (register/register {:user-service mock-user-service}
                                      {:body-params {:username "newuser" :password "123456"}})]
      (is (= 403 (get-in response [:body :code]))))))

(deftest test-register-ignores-privileged-fields
  (testing "请求里带角色、状态等字段也不会生效"
    (let [calls (atom [])
          service {:query-fn (fn [q p] (swap! calls conj [q p])
                               (case q :last-insert-rowid {:last_insert_rowid 9} nil))}
          response (register! service {:username "evil" :password "123456"
                                       :roles [1] :posts [1] :status "0" :dept_id 100})]
      (is (= 200 (get-in response [:body :code])))
      (is (empty? (filter #(#{:insert-user-role! :insert-user-post!} (first %)) @calls)))
      (is (nil? (:dept_id (second (first (filter #(= :create-user! (first %)) @calls)))))))))

(deftest test-register-validation
  (testing "用户名与密码格式"
    (is (= 400 (get-in (register! mock-user-service {:username "a" :password "123456"}) [:body :code])))
    (is (= 400 (get-in (register! mock-user-service {:username "good_name" :password "123"}) [:body :code])))))

(deftest test-register-existing-user
  (testing "注册账号已存在"
    (let [service (assoc mock-user-service :query-fn
                         (fn [q _p]
                           (case q
                             :find-user-by-name {:user_id 1 :user_name "existing"}
                             nil)))
          response (register! service {:username "existing" :password "123456"})]
      (is (= 200 (:status response)))
      (is (= 500 (get-in response [:body :code])))
      (is (= "注册账号已存在" (get-in response [:body :msg]))))))

(deftest test-register-exception
  (testing "创建用户时抛出的意外异常走 5xx 通用文案"
    (let [service (assoc mock-user-service :query-fn
                         (fn [q _p]
                           (case q
                             :find-user-by-name nil
                             :create-user! (throw (RuntimeException. "数据库错误"))
                             :last-insert-rowid {:last_insert_rowid 1}
                             nil)))
          response (eh/call register/register {:user-service service :auth-config enabled}
                            {:body-params {:username "newuser" :password "123456"}})]
      (is (= 500 (:status response)))
      (is (= {:code 500 :msg "服务器内部错误,请稍后重试"} (:body response))
          "异常细节只进服务端日志"))))
