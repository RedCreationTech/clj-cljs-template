(ns com.ruoyi.frontend.api.roles
  "角色管理相关接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-roles
  "获取角色列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/role" :params params
              :on-success on-success :on-error on-error}))

(defn create-role
  "新增角色。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/role" :params params
              :on-success on-success :on-error on-error}))

(defn update-role
  "更新角色。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/role/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-role
  "删除角色。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/role/" id)
              :on-success on-success :on-error on-error}))

(defn change-role-status
  "修改角色状态。"
  [id status on-success on-error]
  (t/request {:method :put :uri (str "/system/role/" id) :params {:status status}
              :on-success on-success :on-error on-error}))

(defn get-role-dept-tree
  "获取角色部门树。"
  [role-id on-success on-error]
  (t/request {:method :get :uri (str "/system/role/deptTree/" role-id)
              :on-success on-success :on-error on-error}))

(defn set-role-data-scope
  "设置角色数据权限。"
  [params on-success on-error]
  (t/request {:method :put :uri "/system/role/dataScope" :params params
              :on-success on-success :on-error on-error}))

(defn list-role-allocated-users
  "获取角色已分配用户。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/role/authUser/allocatedList" :params params
              :on-success on-success :on-error on-error}))

(defn list-role-unallocated-users
  "获取角色未分配用户。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/role/authUser/unallocatedList" :params params
              :on-success on-success :on-error on-error}))

(defn cancel-role-auth-user
  "取消用户角色授权。"
  [params on-success on-error]
  (t/request {:method :put :uri "/system/role/authUser/cancel" :params params
              :on-success on-success :on-error on-error}))

(defn cancel-role-auth-user-all
  "批量取消用户角色授权。"
  [params on-success on-error]
  (t/request {:method :put :uri "/system/role/authUser/cancelAll" :params params
              :on-success on-success :on-error on-error}))

(defn select-role-auth-user-all
  "批量授权用户角色。"
  [params on-success on-error]
  (t/request {:method :put :uri "/system/role/authUser/selectAll" :params params
              :on-success on-success :on-error on-error}))

(defn get-role
  "获取角色详情。"
  [id on-success on-error]
  (t/request {:method :get :uri (str "/system/role/" id)
              :on-success on-success :on-error on-error}))
