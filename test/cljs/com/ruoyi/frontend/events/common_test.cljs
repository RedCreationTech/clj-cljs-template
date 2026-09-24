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
