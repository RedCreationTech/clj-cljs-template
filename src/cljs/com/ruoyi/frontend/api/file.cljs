(ns com.ruoyi.frontend.api.file
  "文件管理接口。"
  (:require
   [ajax.core :as ajax]
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
    (ajax/ajax-request
     {:method :post :uri (str t/api-base "/system/file") :body form-data
      :headers (when-let [token (t/get-token)] {"Authorization" (str "Bearer " token)})
      :response-format (ajax/json-response-format {:keywords? true})
      :handler (fn [[ok result]] (if ok (on-success result) (on-error result)))})))

(defn file-download
  "下载文件。"
  [filename]
  (let [url (str t/api-base "/system/file/" filename)
        token (t/get-token)]
    (if token
      (let [link (.createElement js/document "a")]
        (set! (.-href link) (str url "?token=" (js/encodeURIComponent token)))
        (.setAttribute link "download" filename)
        (.appendChild js/document.body link) (.click link) (.removeChild js/document.body link))
      (set! (.-location js/window) url))))

(defn file-delete
  "删除文件。"
  [filename on-success on-error]
  (t/request {:method :delete :uri (str "/system/file/" filename)
              :on-success on-success :on-error on-error}))
