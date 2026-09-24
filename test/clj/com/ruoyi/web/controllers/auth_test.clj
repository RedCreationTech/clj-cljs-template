(ns com.ruoyi.web.controllers.auth-test
  "认证控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.infra.kv :as kv]
   [com.ruoyi.infra.login-guard :as guard]
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.security :as security]
   [com.ruoyi.web.controllers.auth :as auth]
   [com.ruoyi.web.controllers.captcha :as captcha]))

(def hashed-password (security/hash-password "admin123"))

(def base-user
  {:user_id 1
   :user_name "admin"
   :password hashed-password
   :status "0"
   :nick_name "Admin"
   :avatar ""
   :email ""
   :phonenumber ""
   :sex "0"})

(def base-roles
  [{:role_id 1 :role_key "admin" :perms "system:user:list"}])

(def base-menus
  [{:menu_id 1 :menu_name "System" :parent_id 0 :perms "system:user:list" :status "0"}])

(defn- make-query-fn
  ([] (make-query-fn base-user))
  ([user]
   (fn [q p]
     (case q
       :find-user-by-name user
       :find-user-by-id (when (= (:user_id p) (:user_id user))
                          (dissoc user :password))
       :list-roles-by-user-id base-roles
       :list-posts-by-user-id []
       :list-menus-by-role-id base-menus
       :list-menus-by-role-ids base-menus
       :list-user-role-perms [{:role_key "admin" :perms nil}]
       :create-login-log! nil
       :create-online-user! nil
       :delete-online-user! nil
       nil))))

(defn- make-user-service
  ([] (make-user-service base-user))
  ([user]
   {:query-fn (make-query-fn user)}))

(def mock-menu-service
  {:query-fn (fn [q _]
               (case q
                 :list-menus-by-role-ids base-menus
                 nil))})

(use-fixtures :each
  (fn [test-fn]
    (online/set-query-fn! (fn [_q _p] nil))
    (kv/use-store! (kv/memory-store))
    (guard/reset-all!)
    (test-fn)
    (guard/reset-all!)))

(defn- login [request & {:keys [user config] :or {user base-user}}]
  (auth/login {:user-service (make-user-service user) :auth-config config} request))

(deftest test-login-success
  (testing "使用正确用户名密码登录成功"
    (let [request {:body-params {:username "admin" :password "admin123"}
                   :remote-addr "127.0.0.1"}
          response (auth/login {:user-service (make-user-service)
                                :log-service (make-user-service)}
                               request)]
      (is (= 200 (-> response :body :code)))
      (is (string? (-> response :body :data :token))))))

(deftest test-login-success-with-captcha
  (testing "开启验证码且验证码正确时登录成功"
    (let [uuid "test-uuid"]
      (captcha/store-code! uuid "abcd")
      (let [request {:body-params {:username "admin" :password "admin123"
                                   :captcha "AbCd" :uuid uuid}
                     :remote-addr "127.0.0.1"}
            response (login request :config {:captcha-enabled? true})]
        (is (= 200 (-> response :body :code)))
        (is (string? (-> response :body :data :token)))))))

(deftest test-login-invalid-captcha
  (testing "开启验证码时验证码错误返回 400,且验证码被作废"
    (let [uuid "bad-uuid"]
      (captcha/store-code! uuid "abcd")
      (let [request {:body-params {:username "admin" :password "admin123"
                                   :captcha "wrong" :uuid uuid}
                     :remote-addr "127.0.0.1"}
            response (login request :config {:captcha-enabled? true})]
        (is (= 400 (-> response :body :code)))
        (is (= "验证码错误或已过期" (-> response :body :msg)))
        (is (nil? (captcha/take-code! uuid)) "验证码已作废")))))

(deftest test-login-captcha-required-when-enabled
  (testing "开启验证码时不能靠留空绕过"
    (let [response (login {:body-params {:username "admin" :password "admin123"}}
                          :config {:captcha-enabled? true})]
      (is (= "验证码错误或已过期" (-> response :body :msg)))))
  (testing "关闭验证码时不需要验证码"
    (let [response (login {:body-params {:username "admin" :password "admin123"}})]
      (is (= 200 (-> response :body :code))))))

(deftest test-login-blank-credentials
  (testing "用户名或密码为空返回 400"
    (let [request {:body-params {:username "" :password "admin123"}
                   :remote-addr "127.0.0.1"}
          response (auth/login {:user-service (make-user-service)
                                :log-service (make-user-service)}
                               request)]
      (is (= 400 (-> response :body :code)))
      (is (= "用户名和密码不能为空" (-> response :body :msg))))))

(deftest test-login-user-not-found
  (testing "用户不存在返回 400"
    (let [request {:body-params {:username "nobody" :password "admin123"}
                   :remote-addr "127.0.0.1"}
          response (auth/login {:user-service (make-user-service nil)
                                :log-service (make-user-service nil)}
                               request)]
      (is (= 400 (-> response :body :code)))
      (is (= "用户名或密码错误" (-> response :body :msg)) "不区分用户不存在与密码错误,防止探测账号"))))

(deftest test-login-wrong-password
  (testing "密码错误返回 400"
    (let [request {:body-params {:username "admin" :password "wrongpass"}
                   :remote-addr "127.0.0.1"}
          response (auth/login {:user-service (make-user-service)
                                :log-service (make-user-service)}
                               request)]
      (is (= 400 (-> response :body :code)))
      (is (= "用户名或密码错误" (-> response :body :msg))))))

(deftest test-login-lockout
  (testing "连续失败达到上限后锁定,锁定期间正确密码也不能登录"
    (let [config {:max-failures 3 :lock-minutes 10}
          attempt #(login {:body-params {:username "Admin " :password %}} :config config)]
      (is (= "用户名或密码错误,还可尝试 2 次" (-> (attempt "x") :body :msg)))
      (is (= "用户名或密码错误,还可尝试 1 次" (-> (attempt "x") :body :msg)))
      (is (= 429 (-> (attempt "x") :body :code)))
      (let [locked (attempt "admin123")]
        (is (= 429 (-> locked :body :code)))
        (is (re-find #"10 分钟" (-> locked :body :msg))))))
  (testing "登录成功清零失败计数"
    (guard/reset-all!)
    (let [config {:max-failures 5}]
      (login {:body-params {:username "admin" :password "x"}} :config config)
      (login {:body-params {:username "admin" :password "x"}} :config config)
      (is (= 200 (-> (login {:body-params {:username "admin" :password "admin123"}} :config config) :body :code)))
      (is (= "用户名或密码错误" (-> (login {:body-params {:username "admin" :password "x"}} :config config) :body :msg))))))

(deftest test-token-claims
  (testing "登录签发的令牌带 jti,exp 为 Unix 秒且符合配置的有效期"
    (let [token (-> (login {:body-params {:username "admin" :password "admin123"}} :config {:token-ttl-minutes 60})
                    :body :data :token)
          {:keys [jti iat exp]} (security/parse-token token)]
      (is (string? jti))
      (is (= 3600 (- exp iat)))
      (is (< (Math/abs (- iat (quot (System/currentTimeMillis) 1000))) 5)))))

(deftest test-refresh
  (testing "续期签发新令牌,会话改挂到新 jti"
    (let [calls (atom [])]
      (online/set-query-fn! (fn [q p] (swap! calls conj [q p]) (if (= q :rename-online-session!) 1 nil)))
      (let [old (security/generate-token 1 "admin" [1] :jti "old-jti")
            resp (auth/refresh {:auth-config {:token-ttl-minutes 30}} {:identity (security/parse-token old)})
            claims (security/parse-token (-> resp :body :data :token))]
        (is (= 200 (-> resp :body :code)))
        (is (= 1800 (-> resp :body :data :expiresIn)))
        (is (= [1] (:roles claims)))
        (is (not= "old-jti" (:jti claims)))
        (is (= {:old_session_id "old-jti" :session_id (:jti claims)}
               (select-keys (second (first (filter #(= :rename-online-session! (first %)) @calls)))
                            [:old_session_id :session_id])))
        (is (online/in-grace? "old-jti")))))
  (testing "会话已不存在时拒绝续期"
    (online/set-query-fn! (fn [q _] (when (= q :rename-online-session!) 0)))
    (let [resp (auth/refresh {} {:identity {:user-id 1 :jti "gone"}})]
      (is (= 401 (-> resp :body :code))))))

(deftest test-login-config
  (testing "开发环境给出演示账号,生产环境不给"
    (is (= "admin" (-> (auth/login-config {:auth-config {:demo-account? true}} {}) :body :data :demoAccount :username)))
    (is (nil? (-> (auth/login-config {:auth-config {:demo-account? false :captcha-enabled? true}} {}) :body :data :demoAccount)))
    (is (true? (-> (auth/login-config {:auth-config {:captcha-enabled? true}} {}) :body :data :captchaEnabled)))))

(deftest test-login-disabled
  (testing "停用用户返回 403"
    (let [request {:body-params {:username "admin" :password "admin123"}
                   :remote-addr "127.0.0.1"}
          response (auth/login {:user-service (make-user-service (assoc base-user :status "1"))
                                :log-service (make-user-service (assoc base-user :status "1"))}
                               request)]
      (is (= 403 (-> response :body :code)))
      (is (= "用户已被停用" (-> response :body :msg))))))

(deftest test-get-info-success
  (testing "获取当前登录用户信息成功"
    (let [request {:identity {:user-id 1}}
          response (auth/get-info {:user-service (make-user-service)
                                   :menu-service mock-menu-service}
                                  request)]
      (is (= 200 (-> response :body :code)))
      (is (= "admin" (-> response :body :data :user :user_name)))
      (is (seq (-> response :body :data :roles)))
      (is (= ["*:*:*"] (-> response :body :data :permissions)) "admin 角色返回通配权限")
      (is (vector? (-> response :body :data :menus))))))

(deftest test-get-info-user-not-found
  (testing "获取信息时用户不存在返回 401"
    (let [request {:identity {:user-id 999}}
          response (auth/get-info {:user-service (make-user-service)
                                   :menu-service mock-menu-service}
                                  request)]
      (is (= 401 (-> response :body :code)))
      (is (= "用户不存在" (-> response :body :msg))))))

(deftest test-logout-with-token
  (testing "登出删除当前会话"
    (let [calls (atom [])]
      (online/set-query-fn! (fn [q p] (swap! calls conj [q p]) nil))
      (let [response (auth/logout {:identity {:user-id 1 :jti "s-1"}})]
        (is (= 200 (-> response :body :code)))
        (is (= [[:delete-online-user! {:session_id "s-1"}]] @calls))))))

(deftest test-logout-without-token
  (testing "未携带 token 登出也返回成功"
    (let [request {}
          response (auth/logout request)]
      (is (= 200 (-> response :body :code))))))
