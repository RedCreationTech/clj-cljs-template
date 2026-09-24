(ns com.ruoyi.web.middleware.core-test
  "CORS 白名单与静态资源缓存中间件测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.middleware.core :as core]))

(def ^:private ok-handler (constantly {:status 200 :headers {} :body "ok"}))

(defn- call [allowed request]
  ((core/wrap-cors ok-handler allowed) request))

(deftest parse-origins-test
  (is (= #{} (core/parse-origins nil)))
  (is (= #{"http://a.com" "http://b.com"} (core/parse-origins " http://a.com, http://b.com ,"))))

(deftest whitelist-test
  (testing "白名单内的 Origin 回显,带 Vary"
    (let [resp (call #{"http://a.com"} {:request-method :get :headers {"origin" "http://a.com"}})]
      (is (= "http://a.com" (get-in resp [:headers "Access-Control-Allow-Origin"])))
      (is (= "Origin" (get-in resp [:headers "Vary"])))))
  (testing "白名单外的 Origin 不加任何 CORS 头"
    (let [resp (call #{"http://a.com"} {:request-method :get :headers {"origin" "http://evil.com"}})]
      (is (nil? (get-in resp [:headers "Access-Control-Allow-Origin"])))))
  (testing "默认空白名单:同源请求不受影响,跨域请求不放行"
    (is (= {} (:headers (call #{} {:request-method :get :headers {"origin" "http://a.com"}}))))
    (is (= "ok" (:body (call #{} {:request-method :get :headers {}}))))))

(deftest preflight-test
  (testing "白名单内的预检请求直接 204"
    (let [resp (call #{"*"} {:request-method :options :headers {"origin" "http://x.com"}})]
      (is (= 204 (:status resp)))
      (is (= "http://x.com" (get-in resp [:headers "Access-Control-Allow-Origin"])))))
  (testing "白名单外的预检请求交给后续处理"
    (is (= 200 (:status (call #{} {:request-method :options :headers {"origin" "http://x.com"}}))))))

(deftest revalidate-static-test
  (let [h (core/wrap-revalidate-static (constantly {:status 200 :headers {} :body "x"}))]
    (testing "静态资源加 no-cache"
      (is (= "no-cache" (get-in (h {:request-method :get :uri "/js/app.js"}) [:headers "Cache-Control"]))))
    (testing "API 与非 GET 不处理"
      (is (nil? (get-in (h {:request-method :get :uri "/api/health"}) [:headers "Cache-Control"])))
      (is (nil? (get-in (h {:request-method :post :uri "/x"}) [:headers "Cache-Control"]))))))
