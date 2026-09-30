(ns com.ruoyi.frontend.api.users
  "用户管理相关接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-users
  "获取用户列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/user" :params params
              :on-success on-success :on-error on-error}))

(defn get-user
  "获取用户详情。"
  [user-id on-success on-error]
  (t/request {:method :get :uri (str "/system/user/" user-id)
              :on-success on-success :on-error on-error}))

(defn create-user
  "新增用户。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/user" :params params
              :on-success on-success :on-error on-error}))

(defn update-user
  "更新用户。"
  [user-id params on-success on-error]
  (t/request {:method :put :uri (str "/system/user/" user-id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-user
  "删除用户。"
  [user-id on-success on-error]
  (t/request {:method :delete :uri (str "/system/user/" user-id)
              :on-success on-success :on-error on-error}))

(defn change-user-status
  "修改用户状态。"
  [user-id status on-success on-error]
  (t/request {:method :put :uri (str "/system/user/" user-id "/status/" status)
              :on-success on-success :on-error on-error}))

(defn reset-user-password
  "重置用户密码。"
  [user-id password on-success on-error]
  (t/request {:method :put :uri (str "/system/user/" user-id "/resetPwd")
              :params {:password password}
              :on-success on-success :on-error on-error}))

(defn get-user-roles
  "获取用户已分配角色。"
  [user-id on-success on-error]
  (t/request {:method :get :uri (str "/system/user/" user-id "/authRole")
              :on-success on-success :on-error on-error}))

(defn update-user-roles
  "更新用户角色。"
  [user-id role-ids on-success on-error]
  (t/request {:method :put :uri (str "/system/user/" user-id "/authRole")
              :params {:role_ids role-ids}
              :on-success on-success :on-error on-error}))
