(ns com.ruoyi.web.controllers.common-test
  "通用控制器测试。上传/资源目录通过 :upload-config 传入,不碰仓库里的 uploads/。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.web.controllers.common :as common])
  (:import
   [java.io File]
   [java.nio.file Files]))

(defn- temp-dir []
  (-> (Files/createTempDirectory "common-test" (make-array java.nio.file.attribute.FileAttribute 0))
      .toFile))

(defn- delete-recursive [^File f]
  (when (.isDirectory f)
    (doseq [child (.listFiles f)]
      (delete-recursive child)))
  (.delete f))

(def ^:dynamic *dirs*
  "当前用例的上传目录,由 fixture 绑定(资源目录与它同一个,见 infra.files/resource-dir)。"
  nil)

(defn- ctx []
  {:upload-config {:dir (:upload *dirs*)}})

(defn- upload-path [& more]
  (apply io/file (:upload *dirs*) more))

(defn- with-temp-dirs [test-fn]
  (let [uploads (temp-dir)]
    (try
      (binding [*dirs* {:upload (.getPath uploads)}]
        (test-fn))
      (finally
        (delete-recursive uploads)))))

(use-fixtures :each with-temp-dirs)

(deftest test-upload-success
  (testing "通用文件上传成功"
    (let [source (File/createTempFile "source" ".txt")]
      (try
        (spit source "hello world")
        (let [request {:params {:file {:tempfile source :filename "hello.txt"}}}
              response (common/upload (ctx) request)]
          (is (= 200 (:status response)))
          (is (= 200 (get-in response [:body :code])))
          (is (= "hello.txt" (get-in response [:body :data :fileName])))
          (is (= "/api/common/download?fileName=hello.txt" (get-in response [:body :data :url])))
          (let [target (upload-path "hello.txt")]
            (is (.exists target))
            (is (= "hello world" (slurp target)))))
        (finally
          (.delete source))))))

(deftest test-upload-missing-file
  (testing "上传请求缺少文件时返回失败"
    (let [response (common/upload (ctx) {:params {}})]
      (is (= 200 (:status response)))
      (is (= 400 (get-in response [:body :code])))
      (is (= "请选择要上传的文件" (get-in response [:body :msg]))))))

(deftest test-upload-rejects-disallowed-type
  (testing "扩展名不在白名单里:拒绝,不落盘"
    (let [source (File/createTempFile "source" ".html")]
      (try
        (let [response (common/upload (ctx) {:params {:file {:tempfile source :filename "x.html"}}})]
          (is (= 400 (get-in response [:body :code])))
          (is (empty? (.listFiles (io/file (:upload *dirs*))))))
        (finally (.delete source))))))

(deftest test-upload-exception
  (testing "上传复制失败时返回异常信息"
    (let [response (common/upload (ctx) {:params {:file {:tempfile (io/file "/nonexistent/path.txt")
                                                         :filename "x.txt"}}})]
      (is (= 200 (:status response)))
      (is (= 500 (get-in response [:body :code])))
      (is (string? (get-in response [:body :msg]))))))

(deftest test-upload-creates-directory
  (testing "上传目录不存在时自动创建"
    (let [source (File/createTempFile "source" ".txt")
          nested (upload-path "new" "uploads")]
      (try
        (spit source "data")
        (binding [*dirs* {:upload (.getPath nested)}]
          (let [response (common/upload (ctx) {:params {:file {:tempfile source :filename "nested.txt"}}})]
            (is (= 200 (:status response)))
            (is (= 200 (get-in response [:body :code])))
            (is (.exists nested))
            (is (.exists (io/file nested "nested.txt")))))
        (finally
          (.delete source))))))

(deftest test-download-success
  (testing "通用文件下载成功"
    (let [f (upload-path "report.txt")]
      (spit f "report content")
      (let [response (common/download (ctx) {:query-params {:fileName "report.txt"}})]
        (is (= 200 (:status response)))
        (is (= "application/octet-stream" (get-in response [:headers "Content-Type"])))
        (is (= "attachment; filename=\"report.txt\"" (get-in response [:headers "Content-Disposition"])))
        (is (= "report content" (slurp (:body response))))))))

(deftest test-download-missing
  (testing "下载不存在的文件返回 404"
    (let [response (common/download (ctx) {:query-params {:fileName "missing.txt"}})]
      (is (= 200 (:status response)))
      (is (= 404 (get-in response [:body :code])))
      (is (= "文件不存在" (get-in response [:body :msg]))))))

(deftest test-download-resource-success
  (testing "下载资源文件成功"
    (let [f (upload-path "templates" "demo.xlsx")]
      (.mkdirs (.getParentFile f))
      (spit f "resource content")
      (let [response (common/download-resource (ctx) {:query-params {:resource "templates/demo.xlsx"}})]
        (is (= 200 (:status response)))
        (is (= "application/octet-stream" (get-in response [:headers "Content-Type"])))
        (is (= "attachment; filename=\"demo.xlsx\"" (get-in response [:headers "Content-Disposition"])))
        (is (= "resource content" (slurp (:body response))))))))

(deftest test-download-resource-missing
  (testing "下载不存在的资源返回 404"
    (let [response (common/download-resource (ctx) {:query-params {:resource "missing.png"}})]
      (is (= 200 (:status response)))
      (is (= 404 (get-in response [:body :code])))
      (is (= "资源不存在" (get-in response [:body :msg]))))))

(deftest test-path-traversal-rejected
  (testing "下载、资源下载都不能跳出根目录"
    (spit (io/file (.getParentFile (io/file (:upload *dirs*))) "outside.txt") "secret")
    (is (= 404 (get-in (common/download (ctx) {:query-params {:fileName "../outside.txt"}}) [:body :code])))
    (is (= 404 (get-in (common/download (ctx) {:query-params {:fileName "/etc/passwd"}}) [:body :code])))
    (is (= 404 (get-in (common/download-resource (ctx) {:query-params {:resource "../outside.txt"}}) [:body :code])))
    (is (= 404 (get-in (common/download-resource (ctx) {:query-params {:resource "/etc/passwd"}}) [:body :code]))))
  (testing "上传时文件名里的目录部分被丢弃,文件只会落在上传目录"
    (let [source (File/createTempFile "source" ".txt")]
      (try
        (spit source "x")
        (let [resp (common/upload (ctx) {:params {:file {:tempfile source :filename "../../evil.txt"}}})]
          (is (= "evil.txt" (get-in resp [:body :data :fileName])))
          (is (.exists (upload-path "evil.txt"))))
        (finally (.delete source))))))

(deftest test-avatar-served-from-avatar-dir
  (testing "头像只从上传目录下的 avatar/ 读图片"
    (let [dir (io/file (:upload *dirs*) "avatar")]
      (.mkdirs dir)
      (spit (io/file dir "me.png") "png-bytes")
      (let [ok (common/avatar (ctx) {:path-params {:name "me.png"}})]
        (is (= 200 (:status ok)))
        (is (= "image/png" (get-in ok [:headers "Content-Type"]))))
      (is (= 404 (get-in (common/avatar (ctx) {:path-params {:name "../deps.edn"}}) [:body :code]))
          "不能借头像接口读上传目录里的其它文件"))))
