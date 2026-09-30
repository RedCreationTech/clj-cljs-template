(ns com.ruoyi.web.middleware.exception
  (:require
   [clojure.tools.logging :as log]
   [com.ruoyi.infra.json :as json]
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
  "异常 → JSON 响应。body 与控制器的约定一致(:code / :msg)。
   4xx 另附异常消息、类型、ex-data 与 URI 便于前端定位;
   5xx 只回通用文案 —— 异常类名、URI、堆栈都属于服务端,不能出现在响应里(日志会记全)。"
  [message status exception request]
  (when (>= status 500)
    (log/error exception (str "Exception: " (.getMessage exception) " uri=" (:uri request))))
  {:status  status
   :headers {"content-type" "application/json;charset=utf-8"}
   :body    (json/write-str
             (if (< status 500)
               {:code      status
                :msg       (user-message status exception)
                :message   message
                :exception (.getName (.getClass exception))
                :uri       (:uri request)
                :data      (ex-data exception)}
               {:code status
                :msg  (user-message status exception)}))})

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
