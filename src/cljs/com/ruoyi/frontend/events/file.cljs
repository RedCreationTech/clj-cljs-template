(ns com.ruoyi.frontend.events.file
  "文件管理:上传目录的列表、上传、下载、删除。
   后端返回的是数组(上传目录不做分页),所以这里不用 fetch-with-query。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.file :as file-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :file/set-list
                 (fn [db [_ items]]
                   (-> db
                       (assoc-in [:file :items] items)
                       (assoc-in [:file :loading?] false))))

(rf/reg-event-fx :file/fetch
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:file :loading?] true)
                    :api/list-files nil}))

(rf/reg-fx :api/list-files
           (fn [_]
             (file-api/file-list (fn [result]
                                   (when (= 200 (:code result))
                                     (rf/dispatch [:file/set-list (:data result)])))
                                 (fn [_]))))

(rf/reg-event-fx :file/upload
                 (fn [_ [_ file]]
                   {:api/upload-file file}))

(rf/reg-fx :api/upload-file
           (fn [file]
             (file-api/file-upload file
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "上传成功")
                                       (rf/dispatch [:file/fetch])))
                                   (fn [_]))))

(rf/reg-event-fx :file/download
                 (fn [_ [_ filename]]
                   ;; 下载走 <a download>,没有响应可判断,不需要收尾
                   {:api/download-file filename}))

(rf/reg-fx :api/download-file
           (fn [filename] (file-api/file-download filename)))

(rf/reg-event-fx :file/delete
                 (fn [_ [_ filename]]
                   {:api/delete-file filename}))

(rf/reg-fx :api/delete-file
           (fn [filename]
             (file-api/file-delete filename
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "删除成功")
                                       (rf/dispatch [:file/fetch])))
                                   (fn [_]))))
