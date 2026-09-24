(ns com.ruoyi.domain.system.data-scope-test
  "数据范围计算(纯函数)。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.domain.system.data-scope :as ds]))

;; 1 若依科技 ─┬─ 2 深圳总公司 ─┬─ 4 研发部门
;;             │                └─ 5 市场部门
;;             └─ 3 长沙分公司 ─── 6 财务部门
(def depts [{:dept_id 1 :parent_id 0} {:dept_id 2 :parent_id 1} {:dept_id 3 :parent_id 1}
            {:dept_id 4 :parent_id 2} {:dept_id 5 :parent_id 2} {:dept_id 6 :parent_id 3}])

(def ctx {:user-id 9 :dept-id 2
          :custom-depts {7 [6] 8 [3 6]}
          :all-depts (constantly depts)})

(defn- role [id scope & [k]] {:role_id id :role_key (or k (str "r" id)) :data_scope scope})

(deftest descendant-dept-ids-test
  (is (= #{2 4 5} (ds/descendant-dept-ids depts 2)))
  (is (= #{6} (ds/descendant-dept-ids depts 6)))
  (is (= #{1 2} (ds/descendant-dept-ids [{:dept_id 1 :parent_id 2} {:dept_id 2 :parent_id 1}] 1))
      "脏数据里的环不会死循环"))

(deftest resolve-scope-test
  (testing "admin 角色或「全部数据」不过滤"
    (is (= {:all? true} (ds/resolve-scope [(role 1 "5" "admin")] ctx)))
    (is (= {:all? true} (ds/resolve-scope [(role 2 "3") (role 3 "1")] ctx))))
  (testing "各范围"
    (is (= {:dept-ids #{6} :self-id nil} (ds/resolve-scope [(role 7 "2")] ctx)) "自定义")
    (is (= {:dept-ids #{2} :self-id nil} (ds/resolve-scope [(role 2 "3")] ctx)) "本部门")
    (is (= {:dept-ids #{2 4 5} :self-id nil} (ds/resolve-scope [(role 2 "4")] ctx)) "本部门及以下")
    (is (= {:dept-ids #{} :self-id 9} (ds/resolve-scope [(role 2 "5")] ctx)) "仅本人"))
  (testing "多个角色取并集"
    (is (= {:dept-ids #{2 3 6} :self-id 9}
           (ds/resolve-scope [(role 2 "3") (role 8 "2") (role 5 "5")] ctx))))
  (testing "没有启用角色时只能看自己"
    (is (= {:dept-ids #{} :self-id 9} (ds/resolve-scope [] ctx))))
  (testing "用户没有部门时,本部门类范围为空"
    (is (= {:dept-ids #{} :self-id nil} (ds/resolve-scope [(role 2 "4")] (assoc ctx :dept-id nil))))))

(deftest sql-params-test
  (is (= {:scope_all 1 :scope_dept_ids [-1] :scope_user_id nil} (ds/sql-params {:all? true})))
  (is (= {:scope_all 0 :scope_dept_ids [2 4 5] :scope_user_id nil} (ds/sql-params {:dept-ids #{5 2 4}})))
  (is (= {:scope_all 0 :scope_dept_ids [-1] :scope_user_id 9} (ds/sql-params {:dept-ids #{} :self-id 9}))
      "IN 列表不能为空,用不存在的 ID 占位"))

(deftest allows?-test
  (is (ds/allows? {:all? true} {:dept_id 99 :user_id 1}))
  (is (ds/allows? {:dept-ids #{4}} {:dept_id 4 :user_id 1}))
  (is (not (ds/allows? {:dept-ids #{4}} {:dept_id 5 :user_id 1})))
  (is (ds/allows? {:dept-ids #{} :self-id 9} {:dept_id 5 :user_id 9}))
  (is (not (ds/allows? {:dept-ids #{} :self-id nil} {:dept_id 5 :user_id nil}))))
