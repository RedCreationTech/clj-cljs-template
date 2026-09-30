(ns com.ruoyi.domain.paging-test
  "分页样板的单元测试:offset 换算、默认值与 :filters-fn 加工。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.domain.paging :as paging]))

(defn- recorder
  "返回 {:query-fn, :calls};:query-fn 记录每次查询的关键字与参数。"
  []
  (let [calls (atom {})]
    {:calls calls
     :query-fn (fn [kw params]
                 (swap! calls assoc kw params)
                 (case kw
                   :count-things {:total 42}
                   [{:id 1}]))}))

(deftest paginate-offset-test
  (testing "第 3 页、每页 7 条 → offset 14,分页键不进筛选条件"
    (let [{:keys [query-fn calls]} (recorder)
          out (paging/paginate query-fn :list-things :count-things
                               {:name nil} {:page-num 3 :page-size 7 :name "a"})]
      (is (= [{:id 1}] (:rows out)))
      (is (= 42 (:total out)))
      (is (= {:name "a" :offset 14 :page_size 7} (:list-things @calls)))
      (is (= (:list-things @calls) (:count-things @calls))))))

(deftest paginate-defaults-test
  (testing "不带分页键时用第 1 页 / 每页 10 条"
    (let [{:keys [query-fn calls]} (recorder)]
      (paging/paginate query-fn :list-things :count-things {:name nil} {:name "x"})
      (is (= {:name "x" :offset 0 :page_size 10} (:list-things @calls)))))
  (testing "count 查询没返回行时 total 记 0"
    (let [{:keys [query-fn]} (recorder)]
      (is (= 0 (:total (paging/paginate query-fn :list-things :count-nothing {} {})))))))

(deftest paginate-filters-fn-test
  (testing ":filters-fn 在分页换算之后加工,用户列表靠它补 dept_ids"
    (let [{:keys [query-fn calls]} (recorder)]
      (paging/paginate query-fn :list-things :count-things {} {}
                       :filters-fn #(assoc % :dept_ids [-1]))
      (is (= [-1] (:dept_ids (:list-things @calls))))
      (is (= [-1] (:dept_ids (:count-things @calls)))))))
