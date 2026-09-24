(ns com.ruoyi.web.controllers.system.file
  "文件管理控制器 — 上传、列表、下载、删除。"
  (:require
   [clojure.java.io :as io]
   [com.ruoyi.infra.files :as files]
   [ring.util.response :as response]))

(def upload-dir "uploads/")

(defn- ensure-dir! []
  (let [dir (io/file upload-dir)]
    (when-not (.exists dir) (.mkdirs dir))))

(defn- ok
  ([data] (ok 200 "操作成功" data))
  ([code msg data]
   (-> (response/response {:code code :msg msg :data data})
       (response/content-type "application/json"))))

(defn list-files
  "获取上传文件列表。"
  [_ _]
  (ensure-dir!)
  (let [dir (io/file upload-dir)
        files (when (.exists dir)
                (->> (.listFiles dir)
                     (filter #(.isFile %))
                     (mapv (fn [f]
                             {:name (.getName f)
                              :size (.length f)
                              :modified (.lastModified f)}))
                     (sort-by :modified)
                     reverse))]
    (ok files)))

(defn upload-file
  "上传文件:先按 :upload-config 校验类型与大小;文件名只保留安全字符,同名不覆盖。"
  [{:keys [upload-config]} request]
  (let [{:keys [tempfile filename] :as file} (get-in request [:params :file])]
    (if-let [err (files/upload-error (files/policy upload-config) file)]
      (ok 400 err nil)
      (try
        (let [target (files/store! upload-dir tempfile filename)]
          (ok {:name (.getName target) :size (.length target)}))
        (catch Exception e
          (ok 500 (.getMessage e) nil))))))

(defn- existing-file [request]
  (let [f (files/resolve-in upload-dir (get-in request [:path-params :filename]))]
    (when (and f (.isFile f)) f)))

(defn download-file
  "下载文件(只能是上传目录里的文件)。"
  [_ request]
  (if-let [file (existing-file request)]
    (-> (response/response file)
        (response/header "Content-Disposition" (str "attachment; filename=\"" (.getName file) "\""))
        (response/content-type "application/octet-stream"))
    (ok 404 "文件不存在" nil)))

(defn delete-file
  "删除文件(只能是上传目录里的文件)。"
  [_ request]
  (if-let [file (existing-file request)]
    (do (.delete file) (ok "删除成功"))
    (ok 404 "文件不存在" nil)))
