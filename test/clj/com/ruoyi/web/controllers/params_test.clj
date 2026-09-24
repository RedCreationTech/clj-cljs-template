(ns com.ruoyi.web.controllers.params-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.ruoyi.web.controllers.params :as params]))

(deftest query-test
  (is (= {:role_name "管理" :status "0" :page-num 2 :page-size 20}
         (params/query {:query-params {"role_name" "管理" "status" "0" "page" "2" "size" "20" "role_key" ""}})))
  (is (= {:page-num 3 :page-size 5} (params/query {:query-params {"pageNum" "3" "pageSize" "5"}})))
  (is (= {} (params/query {:query-params {"page" "abc"}})) "非数字的分页参数忽略")
  (is (= {} (params/query {}))))
