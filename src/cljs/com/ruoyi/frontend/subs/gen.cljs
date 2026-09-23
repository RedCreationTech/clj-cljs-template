(ns com.ruoyi.frontend.subs.gen
  "re-frame 订阅：代码生成、文件、表单构建、表单模板。"
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub :gen/tables (fn [db _] (get-in db [:gen :tables])))
(rf/reg-sub :gen/tables-loading? (fn [db _] (get-in db [:gen :tables-loading?] false)))
(rf/reg-sub :gen/selected-tables (fn [db _] (get-in db [:gen :selected-tables] [])))
(rf/reg-sub :gen/preview-data (fn [db _] (get-in db [:gen :preview-data])))
(rf/reg-sub :gen/preview-loading? (fn [db _] (get-in db [:gen :preview-loading?] false)))
(rf/reg-sub :gen/preview-visible? (fn [db _] (get-in db [:gen :preview-visible?] false)))
(rf/reg-sub :file/items (fn [db _] (get-in db [:file :items] [])))
(rf/reg-sub :file/loading? (fn [db _] (get-in db [:file :loading?] false)))

(rf/reg-sub :fb/items (fn [db _] (get-in db [:fb :items] [])))
(rf/reg-sub :fb/selected-id (fn [db _] (get-in db [:fb :selected-id])))
(rf/reg-sub :fb/code-visible? (fn [db _] (get-in db [:fb :code-visible?] false)))

(rf/reg-sub :form-template/list
            (fn [db _]
              (get-in db [:form-templates :items] [])))

(rf/reg-sub :form-template/loading?
            (fn [db _]
              (get-in db [:form-templates :loading?] false)))

(rf/reg-sub :form-template/modal-visible?
            (fn [db _]
              (get-in db [:form-templates :modal-visible?] false)))

(rf/reg-sub :form-template/drawer-visible?
            (fn [db _]
              (get-in db [:form-templates :drawer-visible?] false)))

(rf/reg-sub :form-template/saving?
            (fn [db _]
              (get-in db [:form-templates :saving?] false)))

(rf/reg-sub :gen/preview-table-name (fn [db _] (get-in db [:gen :preview-table-name])))
(rf/reg-sub :gen/config-visible? (fn [db _] (get-in db [:gen :config-visible?] false)))
(rf/reg-sub :gen/config (fn [db _] (get-in db [:gen :config] {:package-path "com.ruoyi" :module-name "system" :author "ruoyi" :table-prefix "sys_"})))
