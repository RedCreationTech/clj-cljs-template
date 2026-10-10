(ns com.ruoyi.frontend.subs.users
  "re-frame 订阅：用户管理（列表 / 表单 / 详情 / 重置密码 / 导入 / 分配角色 / 部门树）。"
  (:require
   [re-frame.core :as rf]))

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

(rf/reg-sub :users/detail-loading?
            (fn [db _]
              (get-in db [:users :detail-loading?] false)))

(rf/reg-sub :users/detail-error?
            (fn [db _]
              (get-in db [:users :detail-error?] false)))

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

