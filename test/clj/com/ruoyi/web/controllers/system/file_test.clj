(ns com.ruoyi.web.controllers.system.file-test
  "文件管理控制器测试。上传目录通过 :upload-config 传入,不再依赖仓库里的 uploads/。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controllers.system.file :as file]))

(defn- temp-dir
  "创建临时目录，返回 java.io.File 对象。
   取规范化路径：控制器返回的是 canonical file，macOS 的 /var 临时目录会解析成 /private/var。"
  []
  (let [dir (io/file (System/getProperty "java.io.tmpdir")
                     (str "file-test-" (System/currentTimeMillis) "-" (rand-int 10000)))]
    (.mkdirs dir)
    (.getCanonicalFile dir)))

(defn- clean-dir!
  "递归删除目录及其内容。"
  [dir]
  (when (.exists dir)
    (doseq [f (file-seq dir)]
      (when (not= f dir)
        (.delete f)))
    (.delete dir)))

(defn- with-temp-upload-dir
  "在临时上传目录里跑测试:参数是 (fn [ctx dir]),ctx 直接传给控制器,dir 是它的 File。"
  [test-fn]
  (let [dir (temp-dir)]
    (try
      (test-fn {:upload-config {:dir (.getPath dir)}} dir)
      (finally
        (clean-dir! dir)))))

(deftest test-list-files-empty
  (testing "空目录时返回空列表"
    (with-temp-upload-dir
      (fn [ctx _]
        (let [response (file/list-files ctx {})
              body (:body response)]
          (is (= 200 (:status response)))
          (is (= 200 (:code body)))
          (is (empty? (:data body))))))))

(deftest test-list-files
  (testing "返回上传文件列表"
    (with-temp-upload-dir
      (fn [ctx dir]
        (let [f (io/file dir "test.txt")]
          (spit f "hello")
          (.setLastModified f 1609459200000)
          (let [response (file/list-files ctx {})
                files (:data (:body response))]
            (is (= 200 (:status response)))
            (is (= 1 (count files)))
            (is (= "test.txt" (:name (first files))))
            (is (= 5 (:size (first files))))
            ;; 时间给 java.util.Date,由 infra.json 统一编码成本地 "yyyy-MM-dd HH:mm:ss";
            ;; 直接返回毫秒数的话前端要么显示成对象要么得自己格式化
            (is (instance? java.util.Date (:modified (first files))))
            (is (= 1609459200000
                   (.getTime ^java.util.Date (:modified (first files)))))))))))

(deftest test-list-files-creates-missing-dir
  (testing "目录不存在时先创建再列表(容器刚启动、还没上传过文件)"
    (let [parent (temp-dir)
          dir (io/file parent "not-yet")]
      (try
        (file/list-files {:upload-config {:dir (.getPath dir)}} {})
        (is (.isDirectory dir))
        (finally
          (clean-dir! parent))))))

(deftest test-upload-file-success
  (testing "成功上传文件"
    (with-temp-upload-dir
      (fn [ctx dir]
        (let [temp-file (java.io.File/createTempFile "upload" ".txt")]
          (try
            (spit temp-file "upload content")
            (let [request {:params {:file {:tempfile temp-file
                                           :filename "uploaded.txt"}}}
                  response (file/upload-file ctx request)
                  body (:body response)
                  target (io/file dir "uploaded.txt")]
              (is (= 200 (:status response)))
              (is (= 200 (:code body)))
              (is (= "uploaded.txt" (get-in body [:data :name])))
              (is (= 14 (get-in body [:data :size])))
              (is (.exists target)))
            (finally
              (.delete temp-file))))))))

(deftest test-upload-file-missing
  (testing "缺少文件时上传失败"
    (with-temp-upload-dir
      (fn [ctx _]
        (let [request {:params {}}
              response (file/upload-file ctx request)
              body (:body response)]
          (is (= 200 (:status response)))
          (is (= 400 (:code body)))
          (is (= "请选择要上传的文件" (:msg body)))
          (is (nil? (:data body))))))))

(deftest test-download-file-success
  (testing "成功下载文件"
    (with-temp-upload-dir
      (fn [ctx dir]
        (let [f (io/file dir "report.pdf")]
          (spit f "pdf content")
          (let [request {:path-params {:filename "report.pdf"}}
                response (file/download-file ctx request)]
            (is (= 200 (:status response)))
            (is (= f (:body response)))
            (is (= "application/octet-stream" (get-in response [:headers "Content-Type"])))
            (is (.contains (get-in response [:headers "Content-Disposition"]) "report.pdf"))))))))

(deftest test-download-file-not-found
  (testing "下载不存在的文件"
    (with-temp-upload-dir
      (fn [ctx _]
        (let [request {:path-params {:filename "missing.txt"}}
              response (file/download-file ctx request)
              body (:body response)]
          (is (= 200 (:status response)))
          (is (= 404 (:code body)))
          (is (= "文件不存在" (:msg body))))))))

(deftest test-delete-file-success
  (testing "成功删除文件"
    (with-temp-upload-dir
      (fn [ctx dir]
        (let [f (io/file dir "delete-me.txt")]
          (spit f "delete me")
          (let [request {:path-params {:filename "delete-me.txt"}}
                response (file/delete-file ctx request)
                body (:body response)]
            (is (= 200 (:status response)))
            (is (= 200 (:code body)))
            (is (= "操作成功" (:msg body)))
            (is (= "删除成功" (:data body)))
            (is (not (.exists f)))))))))

(deftest test-delete-file-not-found
  (testing "删除不存在的文件"
    (with-temp-upload-dir
      (fn [ctx _]
        (let [request {:path-params {:filename "missing.txt"}}
              response (file/delete-file ctx request)
              body (:body response)]
          (is (= 200 (:status response)))
          (is (= 404 (:code body)))
          (is (= "文件不存在" (:msg body))))))))
