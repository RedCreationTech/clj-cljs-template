(ns com.ruoyi.frontend.subs.formbuilder
  "re-frame 订阅：表单构建设计器与表单模板。"
  (:require
   [re-frame.core :as rf]))

;; ─── 设计器 ────────────────────────────────────────────────────────

(rf/reg-sub :fb/items
            (fn [db _]
              (get-in db [:fb :items] [])))

(rf/reg-sub :fb/selected-id
            (fn [db _]
              (get-in db [:fb :selected-id])))

(rf/reg-sub :fb/code-visible?
            (fn [db _]
              (get-in db [:fb :code-visible?] false)))

;; ─── 表单模板 ──────────────────────────────────────────────────────

(rf/reg-sub :form-template/list
            (fn [db _]
              (get-in db [:form-templates :items] [])))

(rf/reg-sub :form-template/loading?
            (fn [db _]
              (get-in db [:form-templates :loading?] false)))

(rf/reg-sub :form-template/saving?
            (fn [db _]
              (get-in db [:form-templates :saving?] false)))

(rf/reg-sub :form-template/modal-visible?
            (fn [db _]
              (get-in db [:form-templates :modal-visible?] false)))

(rf/reg-sub :form-template/drawer-visible?
            (fn [db _]
              (get-in db [:form-templates :drawer-visible?] false)))
