(ns com.ruoyi.frontend.api.errors-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.api.errors :as errors]))

(deftest error-message-test
  (testing "401 交给会话过期流程,不提示"
    (is (nil? (errors/error-message {:status 401}))))
  (testing "网络断开 / 超时"
    (is (= "网络错误,请稍后重试" (errors/error-message {:status 0 :failure :failed})))
    (is (= "网络错误,请稍后重试" (errors/error-message {:status -1 :failure :timeout}))))
  (testing "服务端给了 msg 就用它"
    (is (= "没有操作权限" (errors/error-message {:status 403 :response {:code 403 :msg "没有操作权限"}})))
    (is (= "非法的表名" (errors/error-message {:status 400 :response {:code 400 :msg "非法的表名"}}))))
  (testing "没有 msg 时按状态给通用文案"
    (is (= "没有操作权限" (errors/error-message {:status 403 :response "Forbidden"})))
    (is (= "接口不存在" (errors/error-message {:status 404})))
    (is (= "请求参数不合法" (errors/error-message {:status 400 :response {:humanized {:id ["should be an int"]}}})))
    (is (= "服务器错误,请稍后重试" (errors/error-message {:status 502})))
    (is (= "请求失败(409)" (errors/error-message {:status 409}))))
  (testing "响应不是 JSON"
    (is (= "响应格式错误" (errors/error-message {:status 200 :failure :parse})))))

(deftest business-message-test
  (is (nil? (errors/business-message {:code 200 :data 1})))
  (is (nil? (errors/business-message {:code 401 :msg "x"})) "401 交给会话过期流程")
  (is (nil? (errors/business-message {:status "up"})) "没有业务码的响应不处理")
  (is (nil? (errors/business-message "csv,text")))
  (is (= "用户名已存在" (errors/business-message {:code 500 :msg "用户名已存在"})))
  (is (= "操作失败" (errors/business-message {:code 500 :msg ""}))))
