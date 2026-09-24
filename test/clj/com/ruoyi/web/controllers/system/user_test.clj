(ns com.ruoyi.web.controllers.system.user-test
  "用户管理控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controllers.system.user :as user]))

(def admin-identity
  {:user-id 1 :user-name "admin" :roles [1]})

(def mock-user-service
  {:query-fn (fn [q _p]
               (case q
                 :list-users [{:user_id 1 :user_name "admin" :nick_name "管理员"
                               :email "admin@ruoyi.vip" :phonenumber "13800138000"
                               :sex "0" :status "0" :dept_id 1 :remark ""}]
                 :count-users {:total 1}
                 :find-user-by-id {:user_id 1 :user_name "admin" :nick_name "管理员"
                                   :dept_id 1 :user_type "00" :email "admin@ruoyi.vip"
                                   :phonenumber "13800138000" :sex "0" :avatar ""
                                   :status "0" :remark "" :password "hashed"}
                 :create-user! nil
                 :last-insert-rowid {:last_insert_rowid 2}
                 :update-user! nil
                 :delete-user! nil
                 :list-roles-by-user-id [{:role_id 1 :role_name "管理员"}]
                 :list-posts-by-user-id [{:post_id 1 :post_name "董事长"}]
                 :insert-user-role! nil
                 :insert-user-post! nil
                 :delete-user-roles! nil
                 :delete-user-posts! nil
                 ;; 数据范围:admin 角色不过滤
                 :list-user-data-scopes [{:role_id 1 :role_key "admin" :data_scope "1"}]
                 nil))})

(defn- scoped-service
  "只有「本部门」数据范围(部门 2)的用户;目标用户在部门 1。"
  []
  {:query-fn (fn [q p]
               (case q
                 :list-user-data-scopes [{:role_id 2 :role_key "dept" :data_scope "3"}]
                 :find-user-by-id (if (= 9 (:user_id p))
                                    {:user_id 9 :user_name "me" :dept_id 2}
                                    {:user_id 1 :user_name "admin" :dept_id 1})
                 nil))})

(deftest test-list-users
  (testing "查询用户列表"
    (let [request {:query-params {} :identity admin-identity}
          response (user/list-users {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-get-user
  (testing "获取用户详情"
    (let [request {:path-params {:id "1"} :identity admin-identity}
          response (user/get-user {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-get-user-not-found
  (testing "获取用户详情不存在"
    (let [service {:query-fn (fn [q _p] (case q
                                          :find-user-by-id nil
                                          :list-roles-by-user-id []
                                          :list-posts-by-user-id []
                                          nil))}
          request {:path-params {:id "999"} :identity admin-identity}
          response (user/get-user {:user-service service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-create-user
  (testing "创建用户"
    (let [request {:body-params {:user_name "test" :nick_name "测试" :password "123456"}
                   :identity admin-identity}
          response (user/create-user {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-create-user-validation
  (testing "创建用户缺少用户名"
    (let [request {:body-params {:nick_name "测试" :password "123456"}
                   :identity admin-identity}
          response (user/create-user {:user-service mock-user-service} request)]
      (is (= 200 (:status response)))
      (is (= "用户名不能为空" (-> response :body :msg))))))

(deftest test-data-scope-guards
  (let [request {:path-params {:id "1"} :identity {:user-id 9 :user-name "me"}}]
    (testing "范围外的用户:详情、修改、删除、重置密码都返回 403"
      (doseq [f [user/get-user user/update-user user/delete-user user/reset-password user/auth-role]]
        (is (= 403 (:status (f {:user-service (scoped-service)} request))))))
    (testing "新增到范围外的部门返回 403"
      (is (= 403 (:status (user/create-user {:user-service (scoped-service)}
                                            {:identity {:user-id 9}
                                             :body-params {:user_name "x" :nick_name "x" :password "x" :dept_id 1}})))))
    (testing "范围内(本人)可以查看"
      (is (= 200 (:status (user/get-user {:user-service (scoped-service)}
                                         {:path-params {:id "9"} :identity {:user-id 9}})))))))

(deftest test-update-user
  (testing "更新用户"
    (let [request {:path-params {:id "1"}
                   :body-params {:nick_name "更新后" :email "new@ruoyi.vip"}
                   :identity admin-identity}
          response (user/update-user {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-update-user-with-roles-posts
  (testing "更新用户带角色岗位"
    (let [request {:path-params {:id "1"}
                   :body-params {:nick_name "更新后" :roles [1 2] :posts [1]}
                   :identity admin-identity}
          response (user/update-user {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-delete-user
  (testing "删除用户"
    (let [request {:path-params {:id "1"} :identity admin-identity}
          response (user/delete-user {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-change-status
  (testing "修改用户状态"
    (let [request {:path-params {:id "1" :status "1"} :identity admin-identity}
          response (user/change-status {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-reset-password
  (testing "重置用户密码"
    (let [request {:path-params {:id "1"}
                   :body-params {:password "admin123"}
                   :identity admin-identity}
          response (user/reset-password {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-auth-role
  (testing "获取用户角色列表"
    (let [request {:path-params {:id "1"} :identity admin-identity}
          response (user/auth-role {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))

(deftest test-update-auth-role
  (testing "分配用户角色"
    (let [request {:path-params {:id "1"}
                   :body-params {:role_ids [1 2]}
                   :identity admin-identity}
          response (user/update-auth-role {:user-service mock-user-service} request)]
      (is (map? response))
      (is (= 200 (:status response))))))
