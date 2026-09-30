(ns com.ruoyi.infra.files-test
  "上传文件路径安全。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.files :as files])
  (:import
   [java.nio.file Files]))

(defn- temp-dir []
  (.toFile (Files/createTempDirectory "files-test" (make-array java.nio.file.attribute.FileAttribute 0))))

(deftest safe-name-test
  (is (= "报告.pdf" (files/safe-name "报告.pdf")))
  (is (= "evil.clj" (files/safe-name "../../evil.clj")))
  (is (= "a_b.txt" (files/safe-name "a b.txt")))
  (is (nil? (files/safe-name "..")))
  (is (nil? (files/safe-name ".env")))
  (is (nil? (files/safe-name ""))))

(deftest upload-dir-test
  (testing "目录来自 :upload-config,不再写死在控制器里"
    (is (= "uploads" (files/upload-dir {})) "没配 :dir 时用默认值")
    (is (= "uploads" (files/upload-dir {:dir ""})))
    (is (= "/data/files" (files/upload-dir {:dir "/data/files"})))
    (is (= "/data/files" (files/upload-dir {:dir "/data/files///"}))
        "结尾斜杠统一去掉,拼子目录时不会出现 //")
    (is (= "uploads/avatar" (files/avatar-dir {})))
    (is (= "/data/files/avatar" (files/avatar-dir {:dir "/data/files"})))
    (is (= "/data/files" (files/resource-dir {:dir "/data/files/"})))))

(deftest resolve-test
  (let [root (temp-dir)]
    (testing "resolve-in 只接受根目录的直接子文件名"
      (is (= "a.txt" (.getName (files/resolve-in root "a.txt"))))
      (is (nil? (files/resolve-in root "../a.txt")))
      (is (nil? (files/resolve-in root "sub/a.txt"))))
    (testing "resolve-under 允许子目录但不能跳出根目录"
      (is (some? (files/resolve-under root "sub/a.txt")))
      (is (nil? (files/resolve-under root "sub/../../a.txt")))
      (is (nil? (files/resolve-under root "/etc/passwd"))))))

(deftest store-test
  (let [root (temp-dir)
        src (doto (java.io.File/createTempFile "src" ".txt") (spit "hi"))
        a (files/store! root src "a.txt")
        b (files/store! root src "a.txt")]
    (is (= "a.txt" (.getName a)))
    (is (not= (.getName a) (.getName b)) "同名不覆盖")
    (is (= "hi" (slurp (io/file root (.getName b)))))
    (is (nil? (files/store! root src ".htaccess")))))

(deftest upload-policy-test
  (let [policy (files/policy {:max-mb 1 :extensions ""})
        tmp (java.io.File/createTempFile "upload" ".bin")]
    (testing "默认扩展名白名单,大小上限按 MB"
      (is (= (* 1024 1024) (:max-bytes policy)))
      (is (contains? (:extensions policy) "pdf"))
      (is (not (contains? (:extensions policy) "exe")))
      (is (not (contains? (:extensions policy) "html")) "HTML/SVG 可能带脚本,默认不允许")
      (is (= #{"txt" "log"} (:extensions (files/policy {:extensions " TXT, log ,"})))))
    (testing "校验"
      (is (nil? (files/upload-error policy {:tempfile tmp :filename "a.PDF" :size 10})))
      (is (= "请选择要上传的文件" (files/upload-error policy {:filename "a.pdf"})))
      (is (= "不支持的文件类型:exe" (files/upload-error policy {:tempfile tmp :filename "a.exe" :size 1})))
      (is (= "不支持的文件类型:无扩展名" (files/upload-error policy {:tempfile tmp :filename "Makefile" :size 1})))
      (is (= "文件名不合法" (files/upload-error policy {:tempfile tmp :filename "../.." :size 1})))
      (is (= "文件不能超过 1MB" (files/upload-error policy {:tempfile tmp :filename "a.pdf" :size (* 2 1024 1024)}))))
    (testing "图片策略:只允许图片,最大 2MB"
      (let [img (files/image-policy {:max-mb 10})]
        (is (= (* 2 1024 1024) (:max-bytes img)))
        (is (some? (files/upload-error img {:tempfile tmp :filename "a.pdf" :size 1})))))
    (.delete tmp)))
