(ns com.ruoyi.web.controller-test-helper
  "控制器单元测试的调用助手。控制器只做参数与权限判断,业务规则由领域层抛错,
   所以断言要经过真正的异常中间件,才能看到接口实际返回的响应形状。"
  (:require
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.web.middleware.exception :as exception]))

(defn- parse-body [body]
  (if (string? body) (json/read-str body) body))

(defn call
  "经过异常中间件调用控制器函数 f(形如 `(f ctx request)`),返回 {:status :body}。
   业务错误 -> HTTP 200 + {:code 500 :msg};意外错误 -> HTTP 5xx + 通用文案。"
  [f ctx request]
  (let [handler ((:wrap exception/wrap-exception) (fn [_] (f ctx request)))]
    (-> (handler (assoc request :uri (or (:uri request) "/api/test")))
        (update :body parse-body))))

(defn business-msg
  "调用并取出业务失败的提示文案。"
  [f ctx request]
  (:msg (:body (call f ctx request))))
