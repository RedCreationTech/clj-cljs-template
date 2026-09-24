(ns com.ruoyi.web.controllers.gen
  "代码生成器控制器。"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.ruoyi.domain.gen :as gen-service]
   [ring.util.response :as response])
  (:import
   [java.io File FileOutputStream]
   [java.time LocalDateTime]
   [java.time.format DateTimeFormatter]
   [java.util.zip ZipEntry ZipOutputStream]))

(defn- ok
  ([data] (ok 200 "操作成功" data))
  ([code msg data]
   (-> (response/response {:code code :msg msg :data data})
       (response/content-type "application/json"))))

(defn list-tables
  "查询数据库中的所有表。"
  [{:keys [gen-service]} _request]
  (ok (gen-service/list-tables gen-service)))

(defn table-columns
  "查询指定表列信息。"
  [{:keys [gen-service]} request]
  (let [table-name (get-in request [:query-params "tableName"])]
    (when (seq table-name)
      (ok (gen-service/table-columns gen-service table-name)))))

(defn preview-code
  "生成并预览代码。"
  [{:keys [gen-service]} request]
  (let [table-name (get-in request [:query-params "tableName"])]
    (when (seq table-name)
      (ok (gen-service/generate-code gen-service table-name)))))

(defn batch-generate
  "批量生成代码。"
  [{:keys [gen-service]} request]
  (let [table-names (get-in request [:body-params :tables] [])]
    (ok (mapv #(gen-service/generate-code gen-service %) table-names))))

(defn- write-file! [root path content]
  (when (seq content)
    (let [file (io/file root path)]
      (io/make-parents file)
      (spit file content)
      path)))

(defn- upsert-generated-sql!
  "将 HugSQL 写入 generated.sql(已在 system.edn 登记),按表名替换旧块,避免重复定义。"
  [root table-name hugsql]
  (let [path "resources/sql/generated.sql"
        file (io/file root path)
        marker (str "-- == generated " table-name " ==")
        existing (if (.exists file) (slurp file) "")
        lines (str/split-lines existing)
        ;; 找到本表块起止行（简单行匹配）
        start (first (keep-indexed #(when (str/starts-with? %2 marker) %1) lines))
        end (when start
              (first (keep-indexed #(when (and (> %1 start)
                                               (str/starts-with? %2 "-- == generated ")) %1)
                                   lines)))
        before (if start (str/join "\n" (take start lines)) existing)
        after (if end (str/join "\n" (drop end lines)) "")
        block (str marker "\n" hugsql)]
    (io/make-parents file)
    (spit file (str (str/trim before) "\n\n" block "\n\n" (str/trim after) "\n"))
    path))

(defn- timestamp []
  (.format (DateTimeFormatter/ofPattern "yyyyMMddHHmmss") (LocalDateTime/now)))

(def ^:private ns-path
  "项目根命名空间的路径形式(如 com/ruoyi),由本命名空间名推出,bb rename 改名后自动跟随。"
  (-> (str (ns-name *ns*))
      (str/replace #"\.web\.controllers\.gen$" "")
      (str/replace "-" "_")
      (str/replace "." "/")))

(def ^:private src-paths
  {:clj (str "src/clj/" ns-path) :cljs (str "src/cljs/" ns-path "/frontend")})

(defn- deploy-files
  "要写入的 [相对路径 内容];两套迁移目录各一对 up/down。"
  [{:keys [clj cljs]} {:keys [kebab-name table-name] :as code}]
  (let [ts (timestamp)
        mig (str ts "-create-" kebab-name)
        down (str "DROP TABLE IF EXISTS " table-name ";")
        ;; 命名空间里的 - 在文件名里是 _
        f (str/replace kebab-name "-" "_")]
    [[(str clj "/domain/system/" f ".clj") (:backend-domain code)]
     [(str clj "/web/controllers/system/" f ".clj") (:backend-controller code)]
     [(str cljs "/api/" f ".cljs") (:frontend-api code)]
     [(str cljs "/pages/" f ".cljs") (:frontend-page code)]
     [(str cljs "/events/" f ".cljs") (:frontend-events code)]
     [(str cljs "/subs/" f ".cljs") (:frontend-subs code)]
     [(str "resources/migrations-sqlite/" mig ".up.sql") (:migration-up code)]
     [(str "resources/migrations-sqlite/" mig ".down.sql") down]
     [(str "resources/migrations/" mig ".up.sql") (:migration-up-mysql code)]
     [(str "resources/migrations/" mig ".down.sql") down]]))

(defn deploy-code
  "把生成的代码写进项目源码目录。只在 :dev profile 可用:生产包里没有源码目录,
   也不应允许经 HTTP 往服务器写文件。:root 为项目根目录(测试时指向临时目录)。"
  [{:keys [gen-service env root] :or {root "."}} request]
  (let [table-name (get-in request [:body-params :tableName])]
    (if (not= :dev env)
      (ok 403 "代码部署只在开发环境可用" {})
      (let [code (gen-service/generate-code gen-service table-name)]
        (if (seq (:kebab-name code))
          (let [written (->> (deploy-files src-paths code)
                             (keep (fn [[path content]] (write-file! root path content)))
                             (cons (upsert-generated-sql! root table-name (:backend-sql-queries code)))
                             vec)]
            (ok {:message (str "代码部署成功: " table-name)
                 :written written
                 :routes (:backend-routes code)}))
          (ok 500 "生成失败" {:error "无法生成代码"}))))))

(defn- zip-directory!
  "将目录打包成 zip 文件。"
  [src-dir ^File zip-file]
  (with-open [zos (ZipOutputStream. (FileOutputStream. zip-file))]
    (doseq [file (file-seq (io/file src-dir))
            :when (.isFile file)]
      (let [entry-name (.replace (.getPath file) (str src-dir File/separator) "")]
        (.putNextEntry zos (ZipEntry. entry-name))
        (io/copy file zos)
        (.closeEntry zos))))
  zip-file)

(defn- write-generated-to-dir!
  "把单个表的生成代码按项目目录结构写入 base-dir(解压到项目根目录即可使用)。"
  [base-dir code]
  (doseq [[path content] (conj (deploy-files src-paths code)
                               [(str "resources/sql/" (:kebab-name code) ".sql") (:backend-sql-queries code)])]
    (write-file! base-dir path content)))

(defn download-code
  "批量生成代码并打包成 ZIP 下载。"
  [{:keys [gen-service]} request]
  (let [table-names (get-in request [:body-params :tables] [])
        codes (mapv #(gen-service/generate-code gen-service %) table-names)
        temp-dir (io/file (System/getProperty "java.io.tmpdir") (str "gen-download-" (timestamp)))
        zip-file (io/file (str (.getPath temp-dir) ".zip"))]
    (if (empty? codes)
      (ok 400 "请选择要生成的表" {})
      (do
        (.mkdirs temp-dir)
        (doseq [code codes]
          (write-generated-to-dir! (.getPath temp-dir) code))
        (zip-directory! (.getPath temp-dir) zip-file)
        (.deleteOnExit zip-file)
        (-> (response/file-response (.getPath zip-file))
            (response/content-type "application/zip")
            (response/header "Content-Disposition" "attachment; filename=\"gen-code.zip\""))))))
