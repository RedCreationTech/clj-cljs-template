(ns com.ruoyi.web.controllers.common
  "通用控制器 — 文件上传、下载。路径都经过 infra.files 校验,不能跳出上传目录。
   目录本身来自 :upload-config(环境变量 UPLOAD_DIR),见 infra.files/upload-dir。"
  (:require
   [com.ruoyi.infra.files :as files]
   [ring.util.mime-type :as mime]
   [ring.util.response :as response]))

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
  "通用文件上传:先按 :upload-config 校验类型与大小;文件名只保留安全字符,同名不覆盖。
   返回的 url 是下载接口(需要登录)。"
  [{:keys [upload-config]} request]
  (let [{:keys [tempfile filename] :as file} (get-in request [:params :file])]
    (if-let [err (files/upload-error (files/policy upload-config) file)]
      (ok 400 err nil)
      (try
        (let [target (files/store! (files/upload-dir upload-config) tempfile filename)]
          (ok {:fileName (.getName target)
               :url (str "/api/common/download?fileName=" (.getName target))}))
        (catch Exception e (ok 500 (.getMessage e) nil))))))

(defn avatar
  "头像图片(公开访问,<img> 带不了令牌):只读头像目录里的图片。"
  [{:keys [upload-config]} request]
  (let [name (get-in request [:path-params :name])
        f (files/resolve-in (files/avatar-dir upload-config) name)]
    (if (and f (.isFile f) (contains? files/image-extensions (files/extension name)))
      (-> (response/response f)
          (response/content-type (mime/ext-mime-type name))
          (response/header "Cache-Control" "public, max-age=86400"))
      (ok 404 "文件不存在" nil))))

(defn download
  "通用文件下载:只能下载上传目录里的文件。"
  [{:keys [upload-config]} request]
  (if-let [file (some-> (files/resolve-in (files/upload-dir upload-config)
                                          (get-in request [:query-params :fileName]))
                        (#(when (.isFile ^java.io.File %) %)))]
    (attachment file)
    (ok 404 "文件不存在" nil)))

(defn download-resource
  "下载资源文件:资源目录(见 :upload-config)下的文件(可含子目录),不能跳出该目录。"
  [{:keys [upload-config]} request]
  (if-let [file (some-> (files/resolve-under (files/resource-dir upload-config)
                                             (get-in request [:query-params :resource]))
                        (#(when (.isFile ^java.io.File %) %)))]
    (attachment file)
    (ok 404 "资源不存在" nil)))
