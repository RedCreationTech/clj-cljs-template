(ns com.ruoyi.web.controllers.common
  "通用控制器 — 文件上传、下载。路径都经过 infra.files 校验,不能跳出上传目录。"
  (:require
   [com.ruoyi.infra.files :as files]
   [ring.util.response :as response]))

(def upload-dir "uploads/")
(def resource-dir "uploads/")

(defn- ok
  ([data] (ok 200 "操作成功" data))
  ([code msg data]
   (-> (response/response {:code code :msg msg :data data})
       (response/content-type "application/json"))))

(defn- attachment [^java.io.File file]
  (-> (response/response file)
      (response/header "Content-Disposition" (str "attachment; filename=\"" (.getName file) "\""))
      (response/content-type "application/octet-stream")))

(defn upload
  "通用文件上传。文件名只保留安全字符,同名不覆盖。"
  [_ request]
  (try
    (let [{:keys [tempfile filename]} (get-in request [:params :file])]
      (if-let [target (and tempfile (files/store! upload-dir tempfile filename))]
        (ok {:fileName (.getName target) :url (str "/uploads/" (.getName target))})
        (ok 500 "上传失败" nil)))
    (catch Exception e (ok 500 (.getMessage e) nil))))

(defn download
  "通用文件下载:只能下载上传目录里的文件。"
  [_ request]
  (if-let [file (some-> (files/resolve-in upload-dir (get-in request [:query-params :fileName]))
                        (#(when (.isFile ^java.io.File %) %)))]
    (attachment file)
    (ok 404 "文件不存在" nil)))

(defn download-resource
  "下载资源文件:资源目录(resource-dir)下的文件(可含子目录),不能跳出该目录。"
  [_ request]
  (if-let [file (some-> (files/resolve-under resource-dir (get-in request [:query-params :resource]))
                        (#(when (.isFile ^java.io.File %) %)))]
    (attachment file)
    (ok 404 "资源不存在" nil)))
