(ns com.ruoyi.web.controllers.system.file
  "文件管理控制器 — 上传、列表、下载、删除。目录与类型限制都来自 :upload-config(见 infra.files)。"
  (:require
   [clojure.java.io :as io]
   [com.ruoyi.infra.files :as files]
   [com.ruoyi.web.response :as res]
   [ring.util.response :as response])
  (:import
   [java.util Date]))

(defn- ensure-dir! [upload-config]
  (let [dir (io/file (files/upload-dir upload-config))]
    (when-not (.exists dir) (.mkdirs dir))))

(defn- file-entries [^java.io.File dir]
  (when (.exists dir)
    (->> (.listFiles dir)
         (filter #(.isFile ^java.io.File %))
         (sort-by #(.lastModified ^java.io.File %) >)
         (mapv (fn [^java.io.File f]
                 {:name (.getName f)
                  :size (.length f)
                  :modified (Date. (.lastModified f))})))))

(defn list-files
  "获取上传文件列表。修改时间给 java.util.Date,由 infra.json 统一编码成本地 yyyy-MM-dd HH:mm:ss。"
  [{:keys [upload-config]} _]
  (ensure-dir! upload-config)
  (res/ok (file-entries (io/file (files/upload-dir upload-config)))))

(defn upload-file
  "上传文件:先按 :upload-config 校验类型与大小;文件名只保留安全字符,同名不覆盖。
   写盘失败属于意外错误,交给异常中间件走 5xx,不把异常信息返回给前端。"
  [{:keys [upload-config]} request]
  (let [{:keys [tempfile filename] :as file} (get-in request [:params :file])]
    (if-let [err (files/upload-error (files/policy upload-config) file)]
      (res/ok 400 err nil)
      (let [target (files/store! (files/upload-dir upload-config) tempfile filename)]
        (res/ok {:name (.getName target) :size (.length target)})))))

(defn- existing-file [upload-config request]
  (let [f (files/resolve-in (files/upload-dir upload-config)
                            (get-in request [:path-params :filename]))]
    (when (and f (.isFile f)) f)))

(defn download-file
  "下载文件(只能是上传目录里的文件)。"
  [{:keys [upload-config]} request]
  (if-let [file (existing-file upload-config request)]
    (-> (response/response file)
        (response/header "Content-Disposition" (str "attachment; filename=\"" (.getName file) "\""))
        (response/content-type "application/octet-stream"))
    (res/ok 404 "文件不存在" nil)))

(defn delete-file
  "删除文件(只能是上传目录里的文件)。"
  [{:keys [upload-config]} request]
  (if-let [file (existing-file upload-config request)]
    (do (.delete file) (res/ok "删除成功"))
    (res/ok 404 "文件不存在" nil)))
