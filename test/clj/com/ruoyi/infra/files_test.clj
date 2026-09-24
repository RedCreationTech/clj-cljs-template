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
