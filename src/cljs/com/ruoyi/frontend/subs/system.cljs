(ns com.ruoyi.frontend.subs.system
  "re-frame 订阅：菜单 / 部门 / 岗位 / 文件管理（用户与角色各自成 ns）。"
  (:require
   [re-frame.core :as rf]))

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

;; ─── 文件管理 ──────────────────────────────────────────────────────

(rf/reg-sub :file/items
            (fn [db _]
              (get-in db [:file :items] [])))

(rf/reg-sub :file/loading?
            (fn [db _]
              (get-in db [:file :loading?] false)))
