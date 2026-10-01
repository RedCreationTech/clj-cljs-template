(ns com.ruoyi.frontend.subs.roles
  "re-frame 订阅：角色管理（列表 / 表单 / 菜单权限树 / 数据权限 / 分配用户）。"
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub :roles/items
            (fn [db _]
              (get-in db [:roles :items])))

(rf/reg-sub :roles/total
            (fn [db _]
              (get-in db [:roles :total])))

(rf/reg-sub :roles/loading?
            (fn [db _]
              (get-in db [:roles :loading?] false)))

(rf/reg-sub :roles/query-params
            (fn [db _]
              (get-in db [:roles :query-params] {})))

(rf/reg-sub :roles/modal-visible?
            (fn [db _]
              (get-in db [:roles :modal-visible?] false)))

(rf/reg-sub :roles/editing?
            (fn [db _]
              (boolean (get-in db [:roles :editing]))))

(rf/reg-sub :roles/editing
            (fn [db _]
              (get-in db [:roles :editing])))

(rf/reg-sub :roles/form-data
            (fn [db _]
              (get-in db [:roles :form-data] {})))

(rf/reg-sub :roles/permission-visible?
            (fn [db _]
              (get-in db [:roles :permission-visible?] false)))

(rf/reg-sub :roles/permission-role
            (fn [db _]
              (get-in db [:roles :permission-role])))

(rf/reg-sub :roles/menu-tree
            (fn [db _]
              (get-in db [:roles :menu-tree])))

(rf/reg-sub :roles/checked-keys
            (fn [db _]
              (get-in db [:roles :checked-keys] [])))

(rf/reg-sub :roles/data-scope-visible?
            (fn [db _]
              (get-in db [:roles :data-scope-visible?] false)))

(rf/reg-sub :roles/data-scope-role
            (fn [db _]
              (get-in db [:roles :data-scope-role] {})))

(rf/reg-sub :roles/data-scope
            (fn [db _]
              (get-in db [:roles :data-scope] "1")))

(rf/reg-sub :roles/data-scope-dept-tree
            (fn [db _]
              (get-in db [:roles :data-scope-dept-tree] [])))

(rf/reg-sub :roles/data-scope-checked-keys
            (fn [db _]
              (get-in db [:roles :data-scope-checked-keys] [])))

(rf/reg-sub :roles/user-alloc-visible?
            (fn [db _]
              (get-in db [:roles :user-alloc-visible?] false)))

(rf/reg-sub :roles/user-alloc-role
            (fn [db _]
              (get-in db [:roles :user-alloc-role] {})))

(rf/reg-sub :roles/user-alloc-active-tab
            (fn [db _]
              (get-in db [:roles :user-alloc-active-tab] "allocated")))

(rf/reg-sub :roles/allocated-items
            (fn [db _]
              (get-in db [:roles :allocated-items] [])))

(rf/reg-sub :roles/allocated-total
            (fn [db _]
              (get-in db [:roles :allocated-total] 0)))

(rf/reg-sub :roles/allocated-loading?
            (fn [db _]
              (get-in db [:roles :allocated-loading?] false)))

(rf/reg-sub :roles/allocated-query
            (fn [db _]
              (get-in db [:roles :allocated-query] {})))

(rf/reg-sub :roles/allocated-selected
            (fn [db _]
              (get-in db [:roles :allocated-selected] [])))

(rf/reg-sub :roles/unallocated-items
            (fn [db _]
              (get-in db [:roles :unallocated-items] [])))

(rf/reg-sub :roles/unallocated-total
            (fn [db _]
              (get-in db [:roles :unallocated-total] 0)))

(rf/reg-sub :roles/unallocated-loading?
            (fn [db _]
              (get-in db [:roles :unallocated-loading?] false)))

(rf/reg-sub :roles/unallocated-query
            (fn [db _]
              (get-in db [:roles :unallocated-query] {})))

(rf/reg-sub :roles/unallocated-selected
            (fn [db _]
              (get-in db [:roles :unallocated-selected] [])))

