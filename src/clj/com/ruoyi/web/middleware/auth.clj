(ns com.ruoyi.web.middleware.auth
  "认证与授权。两者都挂在 API 路由的顶层(routes/api.clj 的 route-data),由路由数据驱动:

     [\"/system\" {:auth? true}                                ; 整组要求登录
      [\"/user\" {:get  {:perms \"system:user:list\" ...}        ; 要求登录且拥有该权限
                :post {:perms [\"system:user:add\" \"...\"] ...}}]] ; 集合:拥有任一即可

   - wrap-jwt-auth:解析 Bearer 令牌、校验会话(见 infra.online),通过时写入 :identity;
   - authorize:reitit :compile 中间件,只包装声明了 :auth? / :perms 的路由;
     未登录 401,缺少权限 403。权限按请求实时计算(domain.system.permission)。"
  (:require
   [com.ruoyi.domain.system.permission :as permission]
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.security :as security]
   [ring.util.response :as response]))

(defn wrap-jwt-auth
  "解析 Bearer 令牌并校验会话:签名与有效期通过、且会话仍在线时,
   把 claims 放进 :identity 并刷新心跳;否则继续执行但 :identity 为 nil,由 authorize 决定是否拒绝。"
  [handler]
  (fn [request]
    (let [claims (some-> (security/extract-token request) security/parse-token)
          identity (when (and claims (online/active? (:jti claims))) claims)]
      (handler (cond-> request identity (assoc :identity identity))))))

(defn deny
  "401 / 403 响应(HTTP 状态与业务码一致,前端据此跳登录页或提示无权限)。"
  [status msg]
  (-> (response/response {:code status :msg msg})
      (response/status status)
      (response/content-type "application/json")))

(defn request-permissions
  "当前请求用户的权限集合;query-fn 来自 wrap-base 注入的 [:components :query-fn]。"
  [request]
  (permission/user-permissions (get-in request [:components :query-fn])
                               (get-in request [:identity :user-id])))

(defn check
  "按路由声明检查请求:返回 nil 表示放行,否则返回拒绝响应。"
  [{:keys [auth? perms]} request]
  (cond
    (not (or auth? perms)) nil
    (nil? (:identity request)) (deny 401 "未登录或令牌已过期")
    (and perms (not (permission/permitted? (request-permissions request) perms)))
    (deny 403 "没有操作权限")
    :else nil))

(def authorize
  "路由数据驱动的鉴权中间件(见命名空间说明)。"
  {:name ::authorize
   :compile (fn [{:keys [auth? perms] :as data} _opts]
              (when (or auth? perms)
                (let [rule (select-keys data [:auth? :perms])]
                  (fn [handler]
                    (fn [request]
                      (or (check rule request)
                          (handler request)))))))})
