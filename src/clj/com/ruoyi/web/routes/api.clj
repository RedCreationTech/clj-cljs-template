(ns com.ruoyi.web.routes.api
  (:require
   [com.ruoyi.web.controllers.health :as health]
   [com.ruoyi.web.middleware.auth :as auth-mw]
   [com.ruoyi.web.middleware.exception :as exception]
   [com.ruoyi.web.middleware.formats :as formats]
   [com.ruoyi.web.routes.auth :as auth]
   [com.ruoyi.web.routes.captcha :as captcha]
   [com.ruoyi.web.routes.common :as common]
   [com.ruoyi.web.routes.gen :as gen]
   [com.ruoyi.web.routes.monitor :as monitor]
   [com.ruoyi.web.routes.system :as system]
   [integrant.core :as ig]
   [reitit.coercion.malli :as malli]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]))

(def route-data
  "API 路由公共数据。中间件从外到内:参数与内容协商 → 异常兜底(之后的步骤抛出的异常都转成 JSON)
   → 认证(写 :identity)→ 按路由的 :auth? / :perms 鉴权 → 请求体解码与 malli 强制转换 → handler。
   鉴权在参数校验之前:未登录/无权限的请求先得到 401/403,而不是参数错误。"
  {:coercion   malli/coercion
   :muuntaja   formats/instance
   :swagger    {:id ::api}
   :middleware [parameters/parameters-middleware
                muuntaja/format-negotiate-middleware
                muuntaja/format-response-middleware
                exception/wrap-exception
                auth-mw/wrap-jwt-auth
                auth-mw/authorize
                coercion/coerce-exceptions-middleware
                muuntaja/format-request-middleware
                coercion/coerce-response-middleware
                coercion/coerce-request-middleware]})

(defn api-routes [opts]
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title "Admin API"}}
           :handler (swagger/create-swagger-handler)}}]
   ["/health"
    {:get {:summary "健康检查(含数据库),数据库不可用时 503"
           :handler (partial #'health/healthcheck! {:datasource (:datasource opts)})}}]
   (auth/auth-routes opts)
   (system/system-routes opts)
   (monitor/monitor-routes opts)
   (common/common-routes opts)
   (gen/gen-routes opts)
   (captcha/captcha-routes opts)
   ;; 业务模块的路由组追加在标记之前(bb new-module 自动插入;标记行请保留)
   ;; [new-module] routes
   ])

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  (fn [] [base-path route-data (api-routes opts)]))
