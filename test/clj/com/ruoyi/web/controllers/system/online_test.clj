(ns com.ruoyi.web.controllers.system.online-test
  "在线用户控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controllers.system.online :as online]))

(def mock-online-service
  {:list-online (fn [_params] {:rows [{:tokenId "1" :userName "admin"}]
                               :total 1})
   :force-logout (fn [token-id] (is (= "1" token-id)))})

(deftest test-list-online
  (testing "查询在线用户列表"
    (let [request {:query-params {}}
          response (online/list-online {:online-service mock-online-service} request)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= 200 (:code body)))
      (is (= 1 (:total (:data body))))
      (is (seq (:rows (:data body)))))))

(def ^:private capturing-service
  "记录 controller 传给 service 的参数。"
  (let [seen (atom nil)]
    {:seen seen
     :ctx {:online-service
           {:list-online (fn [p] (reset! seen p) {:rows [] :total 0})
            :force-logout (constantly nil)}}}))

(deftest test-list-online-with-params
  (testing "带分页参数查询在线用户:page/size 与筛选条件都要传到 service"
    (let [response (online/list-online (:ctx capturing-service)
                                       {:query-params {"page" "2" "size" "20"
                                                       "user_name" "admin" "ipaddr" "127.0.0.1"}})]
      (is (= 200 (:status response)))
      (is (= {:page-num 2 :page-size 20 :login-name "admin" :ipaddr "127.0.0.1"}
             @(:seen capturing-service))))))

(deftest test-list-online-without-params
  (testing "没填的筛选条件不能以 nil 传给 service:list-online 的 :or 默认值只对缺失的键生效,nil 会让 offset 计算抛 NPE"
    (online/list-online (:ctx capturing-service) {:query-params {}})
    (is (= {:page-num 1 :page-size 10} @(:seen capturing-service)))))

(deftest test-force-logout
  (testing "强退在线用户"
    (let [request {:path-params {:token-id "1"}}
          response (online/force-logout {:online-service mock-online-service} request)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= 200 (:code body)))
      (is (= "操作成功" (:msg body))))))
