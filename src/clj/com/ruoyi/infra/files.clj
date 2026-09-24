(ns com.ruoyi.infra.files
  "上传文件的落盘与读取。客户端给的文件名只取最后一段并替换不安全字符;
   按名字读、删文件时先规范化路径,确认仍是根目录的直接子文件(防 ../ 目录穿越)。"
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
