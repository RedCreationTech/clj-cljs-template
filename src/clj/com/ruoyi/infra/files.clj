(ns com.ruoyi.infra.files
  "上传文件的校验、落盘与读取。
   - 校验:扩展名白名单 + 大小上限(system.edn 的 :upload-config,环境变量 UPLOAD_MAX_MB / UPLOAD_EXTENSIONS);
   - 落盘:客户端给的文件名只取最后一段并替换不安全字符,同名不覆盖;
   - 读取:按名字读、删文件时先规范化路径,确认仍是根目录的直接子文件(防 ../ 目录穿越)。
   请求体整体大小请在反向代理上再限制一道(如 nginx client_max_body_size)。"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.io File]))

(defn safe-name
  "客户端文件名 → 安全文件名:去掉目录部分,只保留字母、数字、点、横线、下划线(含中文);
   空名或以 . 开头(隐藏文件、..)返回 nil。"
  [filename]
  (let [base (last (str/split (str filename) #"[/\\]"))
        cleaned (str/replace (or base "") #"[^\p{L}\p{N}._-]" "_")]
    (when-not (or (str/blank? cleaned) (str/starts-with? cleaned "."))
      cleaned)))

(defn resolve-in
  "root 目录下名为 name 的文件(File);name 不是安全文件名,或规范化后不在 root 里时返回 nil。"
  ^File [root name]
  (when (and (some? name) (= (str name) (safe-name name)))
    (let [dir (.getCanonicalFile (io/file root))
          f (.getCanonicalFile (io/file dir (str name)))]
      (when (= dir (.getParentFile f)) f))))

(defn resolve-under
  "root 目录下的相对路径 rel(可含子目录);规范化后不在 root 里(../、绝对路径)时返回 nil。"
  ^File [root rel]
  (when-not (or (str/blank? (str rel)) (.isAbsolute (io/file (str rel))))
    (let [dir (.toPath (.getCanonicalFile (io/file root)))
          f (.getCanonicalFile (io/file (.toFile dir) (str rel)))]
      (when (and (.startsWith (.toPath f) dir) (not= (.toPath f) dir)) f))))

(defn store!
  "把上传的临时文件复制到 root 下,返回保存后的 File;文件名不安全时返回 nil。
   同名文件已存在时加时间戳前缀,不覆盖已有文件。"
  ^File [root tempfile filename]
  (when-let [n (safe-name filename)]
    (let [dir (doto (io/file root) (.mkdirs))
          target (io/file dir n)
          target (if (.exists target) (io/file dir (str (System/currentTimeMillis) "_" n)) target)]
      (io/copy tempfile target)
      target)))

;; ─── 上传校验 ────────────────────────────────────────────────────────

(def default-extensions
  "默认允许的扩展名(小写)。可执行文件、脚本、HTML/SVG(可能带脚本)不在其中。"
  #{"jpg" "jpeg" "png" "gif" "webp" "bmp" "pdf" "txt" "csv" "md"
    "doc" "docx" "xls" "xlsx" "ppt" "pptx" "zip"})

(def image-extensions #{"jpg" "jpeg" "png" "gif" "webp"})

(defn- parse-extensions [v]
  (cond
    (set? v) v
    (str/blank? (str v)) default-extensions
    :else (into #{} (comp (map str/trim) (map str/lower-case) (remove str/blank?))
                (str/split (str v) #","))))

(defn policy
  "上传配置 → 校验策略 {:max-bytes .. :extensions #{..}};:max-mb 默认 10。"
  [{:keys [max-mb extensions]}]
  {:max-bytes (* (or max-mb 10) 1024 1024)
   :extensions (parse-extensions extensions)})

(defn image-policy
  "头像等图片:只允许常见图片格式,且不超过 2MB(也不超过通用上限)。"
  [upload-config]
  (let [{:keys [max-bytes]} (policy upload-config)]
    {:max-bytes (min max-bytes (* 2 1024 1024))
     :extensions image-extensions}))

(defn extension
  "文件扩展名(小写),没有时返回 nil。"
  [filename]
  (let [n (str filename)
        i (str/last-index-of n ".")]
    (when (and i (< (inc i) (count n)))
      (str/lower-case (subs n (inc i))))))

(defn upload-error
  "上传的文件({:tempfile :filename :size},ring multipart 的格式)不符合策略时返回提示,否则 nil。"
  [{:keys [max-bytes extensions]} {:keys [tempfile filename size]}]
  (let [ext (extension filename)
        size (or size (some-> ^File tempfile .length) 0)]
    (cond
      (nil? tempfile) "请选择要上传的文件"
      (nil? (safe-name filename)) "文件名不合法"
      (not (contains? extensions ext)) (str "不支持的文件类型:" (or ext "无扩展名"))
      (> size max-bytes) (str "文件不能超过 " (quot max-bytes (* 1024 1024)) "MB")
      :else nil)))
