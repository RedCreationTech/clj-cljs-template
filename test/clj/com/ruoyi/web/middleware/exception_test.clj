(ns com.ruoyi.web.middleware.exception-test
  "异常处理中间件测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.errors :as errors]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.web.middleware.exception :as exception]))

(defn- parse-json-body [response]
  (json/read-str (:body response)))

(deftest test-handler-builds-json-response
  (testing "handler 根据传入状态码和消息构建 JSON 响应"
    (let [ex (ex-info "boom" {:detail "x"})
          request {:uri "/api/test" :request-method :get}
          response (exception/handler "test message" 418 ex request)]
      (is (= 418 (:status response)))
      (is (= "application/json;charset=utf-8" (get-in response [:headers "content-type"])))
      (let [body (parse-json-body response)]
        (is (= "test message" (:message body)))
        (is (= 418 (:code body)))
        (is (= "boom" (:msg body)) "4xx 把异常消息作为提示")
        (is (= "clojure.lang.ExceptionInfo" (:exception body)))
        (is (= {:detail "x"} (:data body)))
        (is (= "/api/test" (:uri body)))))))

(deftest test-handler-logs-server-errors
  (testing "5xx 只回通用文案:异常类名、URI、ex-data 都留在服务端日志里"
    (let [ex (RuntimeException. "server error")
          response (exception/handler "internal" 500 ex {:uri "/error"})]
      (is (= 500 (:status response)))
      (let [body (parse-json-body response)]
        (is (= 500 (:code body)))
        (is (= "服务器内部错误,请稍后重试" (:msg body)) "5xx 不泄露异常消息")
        (is (not (contains? body :exception)) "5xx 不回显异常类名")
        (is (not (contains? body :uri)) "5xx 不回显请求路径")
        (is (not (contains? body :data)) "5xx 不回显 ex-data")))))

(defn- make-throwing-handler [e]
  (fn [_request]
    (throw e)))

(defn- wrap-handler [handler]
  ((:wrap exception/wrap-exception) handler))

(deftest test-wrap-exception-passes-through-normal-response
  (testing "无异常时中间件透传原始响应"
    (let [handler (wrap-handler (fn [_] {:status 200 :body "ok"}))
          response (handler {:uri "/ok"})]
      (is (= 200 (:status response)))
      (is (= "ok" (:body response))))))

(deftest test-wrap-exception-business-exception
  (testing "业务异常按前端约定回 HTTP 200 + {:code 500 :msg},不泄露内部字段"
    (is (= errors/business-type (:type (ex-data (try (errors/fail! "x") (catch Exception e e)))))
        "fail! 抛的异常带中间件认的 :type")
    (let [ex (ex-info "登录账号不能重复" {:type              errors/business-type
                                  :user_name "admin"})
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/users"})
          body (parse-json-body response)]
      (is (= 200 (:status response)) "业务失败不是 HTTP 错误")
      (is (= {:code 500 :msg "登录账号不能重复"} body)
          "body 形状与控制器 fail 完全一致,不多带字段")
      (is (not (contains? body :uri)) "不回显请求路径")
      (is (not (contains? body :exception)) "不回显异常类名"))))

(deftest test-wrap-exception-not-found-exception
  (testing "资源不存在异常映射为 404"
    (let [ex (ex-info "找不到" {:type :system.exception/not-found})
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/missing"})]
      (is (= 404 (:status response)))
      (is (= "not found" (:message (parse-json-body response)))))))

(deftest test-wrap-exception-unauthorized-exception
  (testing "未认证异常映射为 401"
    (let [ex (ex-info "未登录" {:type :system.exception/unauthorized})
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/secret"})]
      (is (= 401 (:status response)))
      (is (= "unauthorized" (:message (parse-json-body response)))))))

(deftest test-wrap-exception-forbidden-exception
  (testing "无权限异常映射为 403"
    (let [ex (ex-info "无权限" {:type :system.exception/forbidden})
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/admin"})]
      (is (= 403 (:status response)))
      (is (= "forbidden" (:message (parse-json-body response)))))))

(deftest test-wrap-exception-internal-exception
  (testing "内部异常映射为 500,响应只有通用文案"
    (let [ex (ex-info "内部错误" {:type :system.exception/internal})
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/fail"})
          body (parse-json-body response)]
      (is (= 500 (:status response)))
      (is (= 500 (:code body)))
      (is (= "服务器内部错误,请稍后重试" (:msg body)))
      (is (not (contains? body :message)) "5xx 不回显内部消息"))))

(deftest test-wrap-exception-default-exception
  (testing "未注册类型异常使用默认 500 处理器"
    (let [ex (ex-info "未知错误" {:unknown true})
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/unknown"})
          body (parse-json-body response)]
      (is (= 500 (:status response)))
      (is (= "服务器内部错误,请稍后重试" (:msg body)) "异常消息不外泄")
      (is (not (contains? body :data)) "5xx 不返回 ex-data")
      (is (not (contains? body :uri)) "5xx 不回显请求路径"))))

(deftest test-wrap-exception-runtime-exception
  (testing "普通 RuntimeException 使用默认 500 处理器"
    (let [ex (RuntimeException. "boom")
          handler (wrap-handler (make-throwing-handler ex))
          response (handler {:uri "/api/crash"})
          body (parse-json-body response)]
      (is (= 500 (:status response)))
      (is (= 500 (:code body)))
      (is (not (contains? body :exception)) "5xx 不回显异常类名")
      (is (not (contains? body :uri)) "5xx 不回显请求路径"))))
