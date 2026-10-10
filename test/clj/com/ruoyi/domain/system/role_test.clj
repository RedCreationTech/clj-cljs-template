(ns com.ruoyi.domain.system.role-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.domain.system.role :as role]))

(def mock-roles
  [{:role_id 1 :role_name "管理员" :role_key "admin" :role_sort 1 :status "0"}
   {:role_id 2 :role_name "普通用户" :role_key "user" :role_sort 2 :status "0"}])

(def mock-menus
  [{:menu_id 1 :menu_name "系统管理" :parent_id 0}
   {:menu_id 2 :menu_name "用户管理" :parent_id 1}])

(defn- mock-query-fn [q _p & _rest]
  (case q
    :list-roles mock-roles
    :find-role-by-id (first mock-roles)
    :list-menus-by-role-id mock-menus
    :create-role! [{:role_id 3}]
    :last-insert-rowid {:last_insert_rowid 3}
    :update-role! nil
    :delete-role! nil
    :delete-role-menus! nil
    :insert-role-menu! nil
    :list-users-by-role [{:user_id 1 :user_name "admin"}]
    :list-users-not-in-role [{:user_id 2 :user_name "user1"}]
    :delete-user-role! nil
    :insert-user-role! nil
    []))

(def mock-service {:query-fn mock-query-fn})

(deftest test-list-roles
  (testing "查询角色列表"
    (let [result (role/list-roles mock-service {})]
      (is (seq result))
      (is (= 2 (count result))))))

(deftest test-find-role-by-id
  (testing "根据ID查询角色"
    (let [result (role/find-role-by-id mock-service 1)]
      (is (some? result))
      (is (= "管理员" (:role_name result)))
      (is (contains? result :menu-ids)))))

(deftest test-create-role
  (testing "创建角色"
    (let [captured (atom nil)
          query-fn (fn [q p]
                     (case q
                       :create-role! (do (reset! captured p) nil)
                       :last-insert-rowid {:last_insert_rowid 3}
                       :insert-role-menu! nil
                       []))
          result (role/create-role! {:query-fn query-fn}
                                    {:role_name "新角色"
                                     :role_key "new"
                                     :menu-ids [1 2]})]
      (is (= 3 result))
      (is (= "1" (:data_scope @captured)))
      (is (= true (:menu_check_strictly @captured)))
      (is (= true (:dept_check_strictly @captured)))
      (is (= "0" (:status @captured)))
      (is (contains? @captured :create_by)))))

(deftest test-update-role
  (testing "更新角色"
    (let [result (role/update-role! mock-service {:role-id 1 :role_name "更新后的角色" :menu-ids [1]})]
      (is (= 1 result)))))

(deftest test-update-role-with-menu-only
  (testing "只更新角色菜单"
    (let [result (role/update-role! mock-service {:role-id 1 :menu-ids [1 2 3]})]
      (is (= 1 result)))))

(deftest test-delete-role
  (testing "删除角色"
    (let [result (role/delete-role! mock-service 1)]
      (is (nil? result)))))

(deftest test-list-allocated-users
  (testing "查询已分配该角色的用户"
    (let [result (role/list-allocated-users mock-service {:role-id 1})]
      (is (seq result))
      (is (= 1 (count result))))))

(deftest test-list-unallocated-users
  (testing "查询未分配该角色的用户"
    (let [result (role/list-unallocated-users mock-service {:role-id 1})]
      (is (seq result))
      (is (= 1 (count result))))))

(deftest test-cancel-auth-user
  (testing "取消单个用户角色授权"
    (is (nil? (role/cancel-auth-user! mock-service {:role-id 1 :user-id 2})))))

(deftest test-cancel-auth-user-all
  (testing "批量取消用户角色授权"
    (is (nil? (role/cancel-auth-user-all! mock-service {:role-id 1 :user-ids [2 3]})))))

(deftest test-select-auth-user-all
  (testing "批量授权用户角色"
    (is (nil? (role/select-auth-user-all! mock-service {:role-id 1 :user-ids [2 3]})))))

(deftest test-dept-tree-by-role
  (testing "全部部门 + 角色已勾选的自定义部门"
    (let [svc {:query-fn (fn [q _] (case q
                                     :list-depts [{:dept_id 1 :parent_id 0} {:dept_id 2 :parent_id 1}]
                                     :list-role-dept-ids [{:dept_id 2}]
                                     nil))}]
      (is (= {:depts [{:dept_id 1 :parent_id 0} {:dept_id 2 :parent_id 1}] :checked-keys [2]}
             (role/dept-tree-by-role svc 1))))))

(deftest test-set-data-scope
  (let [calls (atom [])
        svc {:query-fn (fn [q p] (swap! calls conj [q p]) 1)}]
    (testing "自定义范围:清空后写入所选部门"
      (role/set-data-scope! svc 3 "2" [4 5 4])
      (is (= [[:delete-role-depts! {:role_id 3}]
              [:insert-role-dept! {:role_id 3 :dept_id 4}]
              [:insert-role-dept! {:role_id 3 :dept_id 5}]]
             (filterv #(not= :update-role! (first %)) @calls))))
    (testing "其它范围:只清空自定义部门"
      (reset! calls [])
      (role/set-data-scope! svc 3 "4" [4])
      (is (= [[:delete-role-depts! {:role_id 3}]]
             (filterv #(not= :update-role! (first %)) @calls))))))

(deftest test-get-role-perms
  (testing "获取角色权限标识"
    (let [result (role/get-role-perms mock-service 1)]
      (is (set? result)))))

(deftest integer-boundaries-test
  (let [calls (atom [])
        query (fn [operation params]
                (swap! calls conj [operation params])
                (when (= operation :last-insert-rowid) {:last_insert_rowid 3}))
        service {:query-fn query}]
    (testing "创建角色的顺序和关联菜单 ID 接受浏览器字符串"
      (role/create-role! service {:role_name "测试" :role_key "test" :role_sort "7" :menu-ids ["1"]})
      (is (= 7 (:role_sort (second (first @calls)))))
      (is (= [:insert-role-menu! {:role_id 3 :menu_id 1}] (last @calls))))
    (testing "部分更新保留 nil，角色 ID 与关联 ID 规范为整数"
      (reset! calls [])
      (role/update-role! service {:role-id "3" :status "1"})
      (is (= 3 (:role_id (second (first @calls)))))
      (is (nil? (:role_sort (second (first @calls)))))
      (role/select-auth-user-all! service {:role-id "3" :user-ids ["8"]})
      (is (= [:insert-user-role! {:role_id 3 :user_id 8}] (last @calls)))
      (role/set-data-scope! service "3" "2" ["100"])
      (is (= [:insert-role-dept! {:role_id 3 :dept_id 100}] (last @calls))))
    (testing "非法整数返回业务错误而不是数据库异常"
      (doseq [value ["bad" "1.5" 1.5 "2147483648"]]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"必须是整数"
                             (role/update-role! service {:role-id 3 :role_sort value})))))))
