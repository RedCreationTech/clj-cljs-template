(ns com.ruoyi.frontend.components.pagination-test
  "列表分页属性:服务端分页必须带 current 与 onChange,否则页码会跳、数据不换。"
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.components.pagination :as p]))

(deftest table-pagination-test
  (let [changed (atom [])
        props (p/table-pagination {:total 42 :page 3 :page-size 20
                                   :on-change #(swap! changed conj %)})]
    (testing "页码/总数/每页条数都传给 antd"
      (is (= 42 (:total props)))
      (is (= 3 (:current props)))
      (is (= 20 (:pageSize props))))
    (testing "翻页回调必须存在,并且带上页码"
      (is (fn? (:onChange props)))
      ((:onChange props) "2" "10")
      (is (= [2] @changed)))))

(deftest table-pagination-defaults-test
  (let [props (p/table-pagination {:on-change #(constantly nil)})]
    (is (= 0 (:total props)))
    (is (= 1 (:current props)))
    (is (= 10 (:pageSize props)))))

(deftest client-pagination-test
  (testing "本地翻页不发请求,也就没有 current/onChange"
    (let [props (p/client-pagination)]
      (is (nil? (:onChange props)))
      (is (nil? (:current props)))
      (is (= 10 (:pageSize props)))
      (is (= 30 (:pageSize (p/client-pagination {:page-size 30})))))))
