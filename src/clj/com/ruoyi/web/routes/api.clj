(ns com.ruoyi.web.routes.api
  (:require
   [com.ruoyi.web.controllers.health :as health]
   [com.ruoyi.web.middleware.exception :as exception]
   [com.ruoyi.web.middleware.formats :as formats]
   [com.ruoyi.web.routes.auth :as auth]
   [com.ruoyi.web.routes.captcha :as captcha]
   [com.ruoyi.web.routes.common :as common]
   [com.ruoyi.web.routes.gen :as gen]
   [com.ruoyi.web.routes.system :as system]
   [integrant.core :as ig]
   [reitit.coercion.malli :as malli]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]))

(def route-data
  {:coercion   malli/coercion
   :muuntaja   formats/instance
   :swagger    {:id ::api}
   :middleware [parameters/parameters-middleware
                muuntaja/format-negotiate-middleware
                muuntaja/format-response-middleware
                coercion/coerce-exceptions-middleware
                muuntaja/format-request-middleware
                coercion/coerce-response-middleware
                coercion/coerce-request-middleware
                exception/wrap-exception]})

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
