(ns com.ruoyi.web.middleware.exception
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [reitit.ring.middleware.exception :as exception]))

(def ^:private default-msgs
  {400 "请求参数错误"
   401 "未登录或令牌已过期"
   403 "没有操作权限"
   404 "资源不存在"})

(defn user-message
  "给前端展示的提示:4xx 用异常消息(业务代码抛出的中文提示),5xx 一律用通用文案,不泄露内部细节。"
  [status exception]
  (if (>= status 500)
    "服务器内部错误,请稍后重试"
    (or (not-empty (ex-message exception)) (default-msgs status) "请求失败")))

(defn handler
  "异常 → JSON 响应。body 与控制器的约定一致(:code / :msg),另附异常类型便于排查;
   ex-data 只在 4xx 时返回(5xx 可能含内部信息)。"
  [message status exception request]
  (when (>= status 500)
    (log/error exception "Exception:" (.getMessage exception)))
  {:status  status
   :headers {"content-type" "application/json;charset=utf-8"}
   :body    (json/generate-string
             (cond-> {:code      status
                      :msg       (user-message status exception)
                      :message   message
                      :exception (.getName (.getClass exception))
                      :uri       (:uri request)}
               (< status 500) (assoc :data (ex-data exception))))})

(def wrap-exception
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {:system.exception/internal     (partial handler "internal exception" 500)
     :system.exception/business     (partial handler "bad request" 400)
     :system.exception/not-found    (partial handler "not found" 404)
     :system.exception/unauthorized (partial handler "unauthorized" 401)
     :system.exception/forbidden    (partial handler "forbidden" 403)

       ;; override the default handler
     ::exception/default            (partial handler "default" 500)

       ;; print stack-traces for all exceptions
     ::exception/wrap               (fn [handler e request]
                                      (handler e request))})))
