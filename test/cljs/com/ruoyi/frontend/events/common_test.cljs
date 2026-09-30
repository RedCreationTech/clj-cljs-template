(ns com.ruoyi.frontend.events.common-test
  "Tab 管理与部门树等纯函数。"
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.events.common :as ec]))

(deftest activate-page-tab-test
  (let [db {:tabs {:items [{:key :dashboard :closable false}] :active :dashboard}}
        db1 (ec/activate-page-tab db :user)
        db2 (ec/activate-page-tab db1 :user)]
    (testing "新页面追加 Tab 并激活"
      (is (= :user (:page db1)))
      (is (= :user (get-in db1 [:tabs :active])))
      (is (= [:dashboard :user] (mapv :key (get-in db1 [:tabs :items]))))
      (is (= "用户管理" (:label (last (get-in db1 [:tabs :items]))))))
    (testing "已打开的页面不重复追加"
      (is (= 2 (count (get-in db2 [:tabs :items])))))))

(deftest build-dept-tree-test
  (let [items [{:dept_id 1 :parent_id 0} {:dept_id 2 :parent_id 1} {:dept_id 3 :parent_id 1} {:dept_id 4 :parent_id 2}]
        tree (ec/build-dept-tree items 0)]
    (is (= 1 (count tree)))
    (is (= [2 3] (mapv :dept_id (:children (first tree)))))
    (is (= [4] (mapv :dept_id (:children (first (:children (first tree)))))))
    (is (not (contains? (second (:children (first tree))) :children)) "叶子节点没有 :children")))

(deftest stop-all-loading-test
  (testing "只复位正在 loading 的模块,其它数据不动"
    (let [db {:configs {:loading? true :items [1]}
              :users {:loading? false}
              :auth {:token "t"}
              :page :config}]
      (is (= {:configs {:loading? false :items [1]}
              :users {:loading? false}
              :auth {:token "t"}
              :page :config}
             (ec/stop-all-loading db))))))

(deftest fetch-with-query-test
  (let [db {:configs {:loading? false :query-params {:page 2 :size 10 :config_name "用户"}}}]
    (testing "翻页只覆盖页码,筛选条件保留"
      (let [effects (ec/fetch-with-query db :configs :api/list-configs {:page 3})]
        (is (= {:page 3 :size 10 :config_name "用户"} (:api/list-configs effects)))
        (is (true? (get-in effects [:db :configs :loading?])))
        (is (= {:page 3 :size 10 :config_name "用户"} (get-in effects [:db :configs :query-params])))))
    (testing "不带参数(新增后重新取数)沿用当前页与条件"
      (is (= {:page 2 :size 10 :config_name "用户"}
             (:api/list-configs (ec/fetch-with-query db :configs :api/list-configs nil)))))
    (testing "模块还没有条件时用覆盖值本身"
      (is (= {:page 1}
             (:api/list-jobs (ec/fetch-with-query {} :jobs :api/list-jobs {:page 1})))))))
