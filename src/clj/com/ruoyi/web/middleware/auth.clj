(ns com.ruoyi.web.middleware.auth
  "认证与授权中间件，提供 JWT 校验、在线心跳和权限拦截。"
  (:require
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.security :as security]
   [ring.util.response :as response]))

(defn wrap-jwt-auth
  "解析 Bearer 令牌并校验会话(见 infra.online):签名与有效期通过、且会话仍在线时,
   把 claims 放进 :identity 并刷新心跳;否则继续执行但 :identity 为 nil,由 require-auth 决定是否拒绝。"
  [handler]
  (fn [request]
    (let [claims (some-> (security/extract-token request) security/parse-token)
          identity (when (and claims (online/active? (:jti claims))) claims)]
      (handler (cond-> request identity (assoc :identity identity))))))

(defn require-auth
  "要求请求必须通过认证，否则返回 401。"
  [handler]
  (fn [request]
    (if (:identity request)
      (handler request)
      (-> (response/response {:code 401 :msg "未登录或令牌已过期"})
          (response/status 401)
          (response/content-type "application/json")))))

(defn require-perms
  "要求当前用户拥有指定权限中的任意一个，否则返回 403。"
  [perms]
  (let [required (set (if (sequential? perms) perms [perms]))]
    (fn [handler]
      (fn [request]
        (let [user-perms (set (get-in request [:identity :perms] []))]
          (if (some required user-perms)
            (handler request)
            (-> (response/response {:code 403 :msg "没有操作权限"})
                (response/status 403)
                (response/content-type "application/json"))))))))

(defn auth-middleware
  "组合中间件：JWT 解析 + 在线心跳 + 可选认证要求。"
  ([] (auth-middleware {}))
  ([{:keys [required? perms]}]
   (fn [handler]
     (let [h (if required? (require-auth handler) handler)
           h (if perms ((require-perms perms) h) h)
           h (wrap-jwt-auth h)]
       h))))
