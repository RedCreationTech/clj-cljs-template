(ns com.ruoyi.frontend.subs.system
  "re-frame 订阅：用户/角色/菜单/部门/岗位管理。"
  (:require
   [re-frame.core :as rf]))

;; ─── 用户管理 ──────────────────────────────────────────────────────

(rf/reg-sub :users/items
            (fn [db _]
              (get-in db [:users :items])))

(rf/reg-sub :users/total
            (fn [db _]
              (get-in db [:users :total])))

(rf/reg-sub :users/loading?
            (fn [db _]
              (get-in db [:users :loading?] false)))

(rf/reg-sub :users/query-params
            (fn [db _]
              (get-in db [:users :query-params] {})))

(rf/reg-sub :users/page
            (fn [db _]
              (get-in db [:users :page] 1)))

(rf/reg-sub :users/page-size
            (fn [db _]
              (get-in db [:users :page-size] 10)))

(rf/reg-sub :users/selected-ids
            (fn [db _]
              (get-in db [:users :selected-ids] [])))

(rf/reg-sub :users/selected-empty?
            (fn [db _]
              (empty? (get-in db [:users :selected-ids] []))))

(rf/reg-sub :users/modal-visible?
            (fn [db _]
              (get-in db [:users :modal-visible?] false)))

(rf/reg-sub :users/editing?
            (fn [db _]
              (boolean (get-in db [:users :editing]))))

(rf/reg-sub :users/editing
            (fn [db _]
              (get-in db [:users :editing])))

(rf/reg-sub :users/form-data
            (fn [db _]
              (get-in db [:users :form-data] {})))

(rf/reg-sub :users/role-options
            (fn [db _]
              (get-in db [:users :role-options] [])))

(rf/reg-sub :users/post-options
            (fn [db _]
              (get-in db [:users :post-options] [])))

(rf/reg-sub :users/form-errors
            (fn [db _]
              (get-in db [:users :form-errors] {})))

(rf/reg-sub :users/detail-visible?
            (fn [db _]
              (get-in db [:users :detail-visible?] false)))

(rf/reg-sub :users/detail-data
            (fn [db _]
              (get-in db [:users :detail-data])))

(rf/reg-sub :users/reset-pwd-visible?
            (fn [db _]
              (get-in db [:users :reset-pwd-visible?] false)))

(rf/reg-sub :users/reset-pwd-username
            (fn [db _]
              (get-in db [:users :reset-pwd-username] "")))

(rf/reg-sub :users/reset-pwd-value
            (fn [db _]
              (get-in db [:users :reset-pwd-value] "")))

(rf/reg-sub :users/selected-dept-id
            (fn [db _]
              (get-in db [:users :query-params :dept_id])))

(rf/reg-sub :users/show-search?
            (fn [db _]
              (get-in db [:users :show-search?] true)))

(rf/reg-sub :users/columns
            (fn [db _]
              (get-in db [:users :columns])))

(rf/reg-sub :users/import-visible?
            (fn [db _]
              (get-in db [:users :import-visible?] false)))

(rf/reg-sub :users/import-loading?
            (fn [db _]
              (get-in db [:users :import-loading?] false)))

(rf/reg-sub :users/import-file
            (fn [db _]
              (get-in db [:users :import-file] nil)))

(rf/reg-sub :users/auth-role-visible?
            (fn [db _]
              (get-in db [:users :auth-role-visible?] false)))

(rf/reg-sub :users/auth-role-user
            (fn [db _]
              (get-in db [:users :auth-role-user])))

(rf/reg-sub :users/auth-role-ids
            (fn [db _]
              (get-in db [:users :auth-role-ids] [])))

(rf/reg-sub :users/expanded-dept-ids
            (fn [db _]
              (get-in db [:users :expanded-dept-ids] #{})))

;; ─── 角色管理 ──────────────────────────────────────────────────────

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

;; ─── 菜单管理 ──────────────────────────────────────────────────────

(rf/reg-sub :menus/items
            (fn [db _]
              (get-in db [:menus :items])))

(rf/reg-sub :menus/loading?
            (fn [db _]
              (get-in db [:menus :loading?] false)))

(rf/reg-sub :menus/modal-visible?
            (fn [db _]
              (get-in db [:menus :modal-visible?] false)))

(rf/reg-sub :menus/editing?
            (fn [db _]
              (boolean (get-in db [:menus :editing]))))

(rf/reg-sub :menus/editing
            (fn [db _]
              (get-in db [:menus :editing])))

(rf/reg-sub :menus/form-data
            (fn [db _]
              (get-in db [:menus :form-data] {})))

(rf/reg-sub :menus/tree-data
            (fn [db _]
              (get-in db [:menus :tree-data] [])))

;; ─── 部门管理 ──────────────────────────────────────────────────────

(rf/reg-sub :depts/items
            (fn [db _]
              (get-in db [:depts :items])))

(rf/reg-sub :depts/loading?
            (fn [db _]
              (get-in db [:depts :loading?] false)))

(rf/reg-sub :depts/tree
            (fn [db _]
              (get-in db [:depts :tree] [])))

(rf/reg-sub :depts/expanded-keys
            (fn [db _]
              (get-in db [:depts :expanded-keys] [])))

(rf/reg-sub :depts/modal-visible?
            (fn [db _]
              (get-in db [:depts :modal-visible?] false)))

(rf/reg-sub :depts/editing?
            (fn [db _]
              (boolean (get-in db [:depts :editing]))))

(rf/reg-sub :depts/editing
            (fn [db _]
              (get-in db [:depts :editing])))

(rf/reg-sub :depts/form-data
            (fn [db _]
              (get-in db [:depts :form-data] {})))

;; ─── 岗位管理 ──────────────────────────────────────────────────────

(rf/reg-sub :posts/items
            (fn [db _]
              (get-in db [:posts :items])))

(rf/reg-sub :posts/total
            (fn [db _]
              (get-in db [:posts :total])))

(rf/reg-sub :posts/loading?
            (fn [db _]
              (get-in db [:posts :loading?] false)))

(rf/reg-sub :posts/query-params
            (fn [db _]
              (get-in db [:posts :query-params] {})))

(rf/reg-sub :posts/modal-visible?
            (fn [db _]
              (get-in db [:posts :modal-visible?] false)))

(rf/reg-sub :posts/editing?
            (fn [db _]
              (boolean (get-in db [:posts :editing]))))

(rf/reg-sub :posts/editing
            (fn [db _]
              (get-in db [:posts :editing])))

(rf/reg-sub :posts/form-data
            (fn [db _]
              (get-in db [:posts :form-data] {})))

(rf/reg-sub :file/items
            (fn [db _]
              (get-in db [:file :items] [])))

(rf/reg-sub :file/loading?
            (fn [db _]
              (get-in db [:file :loading?] false)))
