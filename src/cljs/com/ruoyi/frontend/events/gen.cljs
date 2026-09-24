(ns com.ruoyi.frontend.events.gen
  "代码生成与文件管理事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api :as api]
   [re-frame.core :as rf]))

(rf/reg-event-db :gen/set-tables
                 (fn [db [_ data]]
                   (-> db (assoc-in [:gen :tables] data) (assoc-in [:gen :tables-loading?] false))))

(rf/reg-event-fx :gen/fetch-tables
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:gen :tables-loading?] true) :api/gen-tables nil}))

(rf/reg-fx :api/gen-tables
           (fn [_] (api/gen-tables (fn [r] (when (= 200 (:code r)) (rf/dispatch [:gen/set-tables (:data r)]))) (fn [_]))))

(rf/reg-event-db :gen/set-selected-tables
                 (fn [db [_ tables]]
                   (assoc-in db [:gen :selected-tables] tables)))

(rf/reg-event-db :gen/set-preview
                 (fn [db [_ data table-name]]
                   (-> db (assoc-in [:gen :preview-data] data) (assoc-in [:gen :preview-table-name] table-name)
                       (assoc-in [:gen :preview-loading?] false) (assoc-in [:gen :preview-visible?] true))))

(rf/reg-event-fx :gen/preview
                 (fn [{:keys [db]} [_ table-name]]
                   {:db (-> db (assoc-in [:gen :preview-loading?] true) (assoc-in [:gen :preview-visible?] true)
                            (assoc-in [:gen :preview-table-name] table-name))
                    :api/gen-preview table-name}))

(rf/reg-fx :api/gen-preview
           (fn [table-name] (api/gen-preview table-name (fn [r] (when (= 200 (:code r)) (rf/dispatch [:gen/set-preview (:data r) table-name]))) (fn [_]))))

(rf/reg-event-db :gen/close-preview
                 (fn [db _] (assoc-in db [:gen :preview-visible?] false)))

(rf/reg-event-fx :gen/generate
                 (fn [{:keys [db]} [_ tables]]
                   {:db db :api/gen-generate tables}))

(rf/reg-fx :api/gen-generate
           (fn [tables] (api/gen-generate tables (fn [r] (when (= 200 (:code r)) (antd/success! "代码生成成功"))) (fn [_] (antd/error! "生成失败")))))

(rf/reg-event-db :gen/open-config
                 (fn [db _]
                   (assoc-in db [:gen :config-visible?] true)))

(rf/reg-event-db :gen/close-config
                 (fn [db _]
                   (assoc-in db [:gen :config-visible?] false)))

(rf/reg-event-db :gen/update-config
                 (fn [db [_ key value]]
                   (assoc-in db [:gen :config key] value)))

(rf/reg-event-fx :gen/download
                 (fn [{:keys [db]} [_ tables]]
                   {:db db :api/gen-download tables}))

(rf/reg-fx :api/gen-download
           (fn [tables]
             (when (seq tables)
               (api/gen-download tables))))

(rf/reg-event-fx :gen/deploy
                 (fn [{:keys [db]} [_ tables]]
                   {:db db :api/gen-deploy (first tables)}))

(rf/reg-fx :api/gen-deploy
           (fn [table-name]
             (api/gen-deploy table-name
                             (fn [r]
                               (when (= 200 (:code r))
                                 (antd/success! (str "部署成功: " (get-in r [:data :message])))))
                             (fn [_] (antd/error! "部署失败")))))

(rf/reg-event-db :file/set-list
                 (fn [db [_ data]]
                   (-> db (assoc-in [:file :items] data) (assoc-in [:file :loading?] false))))

(rf/reg-event-fx :file/fetch
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:file :loading?] true) :api/file-list nil}))

(rf/reg-fx :api/file-list
           (fn [_] (api/file-list (fn [r] (when (= 200 (:code r)) (rf/dispatch [:file/set-list (:data r)]))) (fn [_]))))

(rf/reg-event-fx :file/upload
                 (fn [_ [_ file]] {:api/file-upload file}))

(rf/reg-fx :api/file-upload
           (fn [file]
             (api/file-upload file
                              (fn [r] (when (= 200 (:code r)) (antd/success! "上传成功") (rf/dispatch [:file/fetch])))
                              (fn [_] (antd/error! "上传失败")))))

(rf/reg-event-fx :file/download
                 (fn [_ [_ filename]]
                   (api/file-download filename) {}))

(rf/reg-event-fx :file/delete
                 (fn [_ [_ filename]] {:api/file-delete filename}))

(rf/reg-fx :api/file-delete
           (fn [filename]
             (api/file-delete filename
                              (fn [r] (when (= 200 (:code r)) (antd/success! "删除成功") (rf/dispatch [:file/fetch])))
                              (fn [_] (antd/error! "删除失败")))))
