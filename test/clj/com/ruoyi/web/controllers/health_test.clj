(ns com.ruoyi.web.controllers.health-test
  "健康检查控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controllers.health :as health]
   [next.jdbc :as jdbc]))

(deftest test-health-check
  (testing "没有数据源时只报告进程存活"
    (let [response (health/healthcheck! {} {})]
      (is (= 200 (:status response)))
      (is (= "unknown" (get-in response [:body :db :status])))))
  (testing "数据库可用"
    (with-redefs [jdbc/execute-one! (fn [_ _] {:ok 1})]
      (let [response (health/healthcheck! {:datasource :ds} {})]
        (is (= 200 (:status response)))
        (is (= "up" (get-in response [:body :db :status]))))))
  (testing "数据库不可用返回 503,异常细节只进日志"
    (with-redefs [jdbc/execute-one! (fn [_ _] (throw (Exception. "Connection refused")))]
      (let [response (health/healthcheck! {:datasource :ds} {})]
        (is (= 503 (:status response)))
        (is (= "down" (get-in response [:body :app :status])))
        (is (not (contains? (:db (:body response)) :message))
            "/api/health 不带鉴权,不能把 JDBC 异常信息(主机、路径、SQL)回给调用方")))))
