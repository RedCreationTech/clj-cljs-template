(ns com.ruoyi.web.middleware.exception
  "所有异常的出口。领域 / 控制器不要把异常包成响应,抛带 :type 的 ex-info 就行
   (业务错误用 `com.ruoyi.infra.errors/fail!`),由这里统一渲染:

     :system.exception/business  200 + {:code 500 :msg}   业务规则不通过,消息原样给前端
     :system.exception/not-found 404、unauthorized 401、forbidden 403、internal 500
     其它未注册类型               500 + 通用文案

   业务错误必须回 HTTP 200:前端 transport 按 body 的 :code 判断成败,
   控制器历来也是这么返回的(见 com.ruoyi.web.response/fail)。"
  (:require
   [clojure.tools.logging :as log]
   [com.ruoyi.infra.errors :as errors]
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

(defn business-handler
  "业务规则不通过 → 前端约定的失败响应:HTTP 200 + {:code 500 :msg},与控制器 fail 完全一致。
   消息是领域层写的中文提示,可以直接展示;意外错误不会走到这里(它们没有 business 类型)。"
  [^Exception exception _request]
  (log/debug exception "业务校验未通过")
  {:status  200
   :headers {"content-type" "application/json;charset=utf-8"}
   :body    (json/write-str {:code 500
                             :msg  (or (not-empty (ex-message exception)) "操作失败")})})

(def wrap-exception
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {errors/business-type          business-handler
     :system.exception/internal     (partial handler "internal exception" 500)
     :system.exception/not-found    (partial handler "not found" 404)
     :system.exception/unauthorized (partial handler "unauthorized" 401)
     :system.exception/forbidden    (partial handler "forbidden" 403)

       ;; override the default handler
     ::exception/default            (partial handler "default" 500)

       ;; print stack-traces for all exceptions
     ::exception/wrap               (fn [handler e request]
                                      (handler e request))})))
