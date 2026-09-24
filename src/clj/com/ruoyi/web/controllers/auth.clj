(ns com.ruoyi.web.controllers.auth
  "认证控制器:登录(验证码开关、失败限流)、令牌续期、登出、当前用户信息、登录页公开配置。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.system.log :as log-domain]
   [com.ruoyi.domain.system.menu :as menu-service]
   [com.ruoyi.domain.system.role :as role-service]
   [com.ruoyi.domain.system.user :as user-service]
   [com.ruoyi.infra.login-guard :as guard]
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.security :as security]
   [com.ruoyi.web.controllers.captcha :as captcha]
   [ring.util.response :as response]))

(def default-config
  "认证配置的缺省值;实际值来自 system.edn 的 :auth-config(按 profile 与环境变量覆盖)。"
  {:token-ttl-minutes security/default-token-ttl-minutes
   :captcha-enabled? false
   :register-enabled? false
   :max-failures 5
   :lock-minutes 10
   :demo-account? false})

(def ^:private bad-credentials "用户名或密码错误")

(defn- success
  "构造成功响应。"
  [data]
  (-> (response/response {:code 200 :msg "操作成功" :data data})
      (response/content-type "application/json")))

(defn- error
  "构造错误响应(业务码放在 body,HTTP 状态只在 5xx 时跟随)。"
  [code msg]
  (-> (response/response {:code code :msg msg})
      (response/status (if (>= code 500) 500 200))
      (response/content-type "application/json")))

(defn config-of
  "合并缺省值后的认证配置。"
  [auth-config]
  (merge default-config auth-config))

(defn client-ip
  "客户端 IP:优先取反向代理写入的 X-Forwarded-For 第一段。"
  [request]
  (or (some-> (get-in request [:headers "x-forwarded-for"]) (str/split #",") first str/trim not-empty)
      (:remote-addr request)
      "127.0.0.1"))

(defn captcha-ok?
  "验证码开启时必须提交且与缓存一致(不区分大小写、未过期);关闭时不校验。
   验证码一次性:只要带了 uuid,无论对错都立即作废。"
  [enabled? {:keys [captcha uuid]}]
  (let [stored (when (seq uuid) (get @captcha/captcha-store uuid))]
    (when (seq uuid) (swap! captcha/captcha-store dissoc uuid))
    (or (not enabled?)
        (boolean (and stored (seq captcha)
                      (<= (System/currentTimeMillis) (:expire stored))
                      (= (str/upper-case captcha) (str/upper-case (:code stored))))))))

(def ^:private dummy-hash
  "用户不存在时也做一次密码校验,使响应时间与「密码错误」一致,无法借此探测账号。"
  (delay (security/hash-password (str (random-uuid)))))

(defn- authenticate
  "校验用户名、密码与账号状态:成功返回 {:user u},失败返回 {:code c :msg m}。"
  [user-service username password]
  (let [user (user-service/find-user-by-name user-service username)
        ok? (security/verify-password password (or (:password user) @dummy-hash))]
    (cond
      (not (and user ok?)) {:code 400 :msg bad-credentials}
      (not= "0" (:status user)) {:code 403 :msg "用户已被停用"}
      :else {:user user})))

(defn- login-log! [user-service username ip ok? msg]
  (log-domain/create-login-log! user-service {:user_name username :ipaddr ip :login_location ""
                                              :browser "" :os "" :status (if ok? "0" "1") :msg msg}))

(defn- issue-token!
  "签发令牌并登记在线会话(会话 ID = 令牌里的 :jti)。"
  [user-service user ip ttl-minutes]
  (let [role-ids (mapv :role_id (:roles (user-service/find-user-by-id user-service (:user_id user))))
        jti (str (random-uuid))]
    (online/register! jti (:user_name user) ip)
    (security/generate-token (:user_id user) (:user_name user) role-ids :ttl-minutes ttl-minutes :jti jti)))

(defn- locked-msg [{:keys [lock-minutes]}]
  (str "密码错误次数过多,账号已锁定,请 " lock-minutes " 分钟后再试"))

(defn- failed-login
  "登录失败:记日志;用户名或密码错误时计入限流,剩余次数不多时提示,达到上限提示锁定。"
  [cfg user-service username ip {:keys [code msg]}]
  (login-log! user-service username ip false msg)
  (if (= 400 code)
    (let [left (guard/record-failure! cfg username (System/currentTimeMillis))]
      (cond
        (zero? left) (error 429 (locked-msg cfg))
        (<= left 2) (error 400 (str msg ",还可尝试 " left " 次"))
        :else (error 400 msg)))
    (error code msg)))

(defn login
  "用户登录:验证码 → 非空 → 锁定检查 → 密码与状态 → 签发令牌并登记会话。"
  [{:keys [user-service auth-config]} request]
  (let [cfg (config-of auth-config)
        {:keys [username password] :as body} (:body-params request)
        ip (client-ip request)]
    (cond
      (not (captcha-ok? (:captcha-enabled? cfg) body)) (error 400 "验证码错误或已过期")
      (or (str/blank? username) (str/blank? password)) (error 400 "用户名和密码不能为空")
      (guard/locked-until username (System/currentTimeMillis))
      (do (login-log! user-service username ip false "账号已锁定")
          (error 429 (locked-msg cfg)))
      :else
      (let [{:keys [user] :as result} (authenticate user-service username password)]
        (if-not user
          (failed-login cfg user-service username ip result)
          (do (guard/record-success! username)
              (login-log! user-service username ip true "登录成功")
              (success {:token (issue-token! user-service user ip (:token-ttl-minutes cfg))
                        :expiresIn (* 60 (:token-ttl-minutes cfg))})))))))

(defn refresh
  "令牌续期:用仍有效的令牌换一个新令牌。会话改挂到新令牌,旧令牌在短暂宽限期后失效。
   角色取自旧令牌,角色变更需要重新登录才生效。"
  [{:keys [auth-config]} request]
  (let [{:keys [user-id user-name roles jti]} (:identity request)
        ttl (:token-ttl-minutes (config-of auth-config))
        new-jti (str (random-uuid))]
    (if (online/rotate! jti new-jti)
      (success {:token (security/generate-token user-id user-name roles :ttl-minutes ttl :jti new-jti)
                :expiresIn (* 60 ttl)})
      (error 401 "会话已失效,请重新登录"))))

(defn get-info
  "获取当前登录用户信息及权限菜单。"
  [{:keys [user-service menu-service]} request]
  (let [identity (:identity request)
        user-id (:user-id identity)]
    (if-let [user (user-service/find-user-by-id user-service user-id)]
      (let [roles (:roles user)
            role-ids (mapv :role_id roles)
            perms (->> (mapcat #(role-service/get-role-perms {:query-fn (:query-fn user-service)} %) role-ids)
                       (into #{})
                       (vec))
            menus (menu-service/menu-tree-by-roles menu-service role-ids)]
        (success {:user (select-keys user [:user_id :user_name :nick_name :avatar :email :phonenumber :sex])
                  :roles (mapv :role_key roles)
                  :permissions perms
                  :menus menus}))
      (error 401 "用户不存在"))))

(defn logout
  "用户登出:删除会话,令牌立即失效。令牌无效或缺失时同样返回成功。"
  [request]
  (online/unregister! (get-in request [:identity :jti]))
  (success {}))

(defn login-config
  "登录页需要的公开配置:是否显示验证码、是否开放注册;开发/测试环境额外给出演示账号用于预填。"
  [{:keys [auth-config]} _request]
  (let [cfg (config-of auth-config)]
    (success (cond-> {:captchaEnabled (:captcha-enabled? cfg)
                      :registerEnabled (:register-enabled? cfg)}
               (:demo-account? cfg) (assoc :demoAccount {:username "admin" :password "admin123"})))))
