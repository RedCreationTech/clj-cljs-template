(ns com.ruoyi.frontend.api.file
  "文件管理接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn file-list
  "获取文件列表。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/file"
              :on-success on-success :on-error on-error}))

(defn file-upload
  "上传文件。"
  [file on-success on-error]
  (let [form-data (js/FormData.)]
    (.append form-data "file" file)
    (t/request {:method :post :uri "/system/file" :body form-data
                :on-success on-success :on-error on-error})))

(defn file-download
  "下载文件。"
  [filename]
  (t/download! (str "/system/file/" (js/encodeURIComponent filename)) filename))

(defn file-delete
  "删除文件。"
  [filename on-success on-error]
  (t/request {:method :delete :uri (str "/system/file/" filename)
              :on-success on-success :on-error on-error}))
