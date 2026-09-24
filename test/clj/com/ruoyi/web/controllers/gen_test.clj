(ns com.ruoyi.web.controllers.gen-test
  "代码生成器控制器测试。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controllers.gen :as gen]))

(def mock-columns
  [{:name "id" :type "INTEGER" :pk 1 :notnull 1}
   {:name "name" :type "varchar" :is_nullable "YES"}])

(def mock-gen-service
  {:query-fn (fn [q _params]
               (case q
                 :gen-tables [{:name "sys_gen_test"}]
                 :gen-columns mock-columns
                 []))})

(deftest test-list-tables
  (testing "查询所有表"
    (let [request {}
          response (gen/list-tables {:gen-service mock-gen-service} request)]
      (is (= 200 (:status response)))
      (is (= 200 (get-in response [:body :code])))
      (is (= [{:name "sys_gen_test"}] (get-in response [:body :data]))))))

(deftest test-table-columns
  (testing "查询表列信息"
    (let [request {:query-params {"tableName" "sys_gen_test"}}
          response (gen/table-columns {:gen-service mock-gen-service} request)]
      (is (= 200 (:status response)))
      (is (= 2 (count (get-in response [:body :data]))))))
  (testing "缺少表名返回 nil"
    (is (nil? (gen/table-columns {:gen-service mock-gen-service} {:query-params {}})))))

(deftest test-preview-code
  (testing "预览代码"
    (let [request {:query-params {"tableName" "sys_gen_test"}}
          response (gen/preview-code {:gen-service mock-gen-service} request)]
      (is (= 200 (:status response)))
      (is (= "gen-test" (get-in response [:body :data :kebab-name])))
      (is (string? (get-in response [:body :data :backend-domain])))))
  (testing "缺少表名返回 nil"
    (is (nil? (gen/preview-code {:gen-service mock-gen-service} {:query-params {}})))))

(deftest test-batch-generate
  (testing "批量生成"
    (let [request {:body-params {:tables ["sys_gen_test"]}}
          response (gen/batch-generate {:gen-service mock-gen-service} request)]
      (is (= 200 (:status response)))
      (is (= 1 (count (get-in response [:body :data]))))
      (is (= "gen-test" (get-in response [:body :data 0 :kebab-name])))))
  (testing "空表列表"
    (let [response (gen/batch-generate {:gen-service mock-gen-service} {:body-params {}})]
      (is (= 200 (:status response)))
      (is (= [] (get-in response [:body :data]))))))

(defn- temp-root []
  (doto (java.io.File/createTempFile "gen-deploy" "") (.delete) (.mkdirs)))

(deftest test-deploy-code-dev-only
  (testing "非开发环境拒绝部署,不写任何文件"
    (let [root (temp-root)
          response (gen/deploy-code {:gen-service mock-gen-service :env :prod :root (.getPath root)}
                                    {:body-params {:tableName "sys_gen_test"}})]
      (is (= 403 (get-in response [:body :code])))
      (is (empty? (rest (file-seq root)))))))

(deftest test-deploy-code-failure
  (testing "无法生成 kebab 名时返回 500"
    (let [empty-service {:query-fn (fn [_ _] [])}
          request {:body-params {:tableName "sys_"}}
          response (gen/deploy-code {:gen-service empty-service :env :dev :root (.getPath (temp-root))} request)]
      (is (= 200 (:status response)))
      (is (= 500 (get-in response [:body :code])))
      (is (= "生成失败" (get-in response [:body :msg])))
      (is (= "无法生成代码" (get-in response [:body :data :error]))))))

(deftest test-deploy-code-success
  (testing "部署到项目目录:源码按命名空间路径,两套迁移成对,HugSQL 进 generated.sql"
    (let [root (temp-root)
          response (gen/deploy-code {:gen-service mock-gen-service :env :dev :root (.getPath root)}
                                    {:body-params {:tableName "sys_gen_test"}})
          written (set (get-in response [:body :data :written]))
          file-names (->> (file-seq root) (filter #(.isFile %)) (map #(.getName %)) set)]
      (is (= "操作成功" (get-in response [:body :msg])))
      (is (string? (get-in response [:body :data :routes])))
      (is (contains? written "resources/sql/generated.sql"))
      (is (contains? written "src/clj/com/ruoyi/domain/system/gen_test.clj") "文件名用下划线,与命名空间对应")
      (is (= 2 (count (filter #(re-find #"create-gen-test\.up\.sql$" %) written))) "SQLite 与 MySQL 各一份")
      (is (some #(re-find #"AUTO_INCREMENT" (slurp (io/file root %)))
                (filter #(re-find #"^resources/migrations/" %) written)))
      (is (contains? file-names "generated.sql")))))

(deftest test-download-code-empty
  (testing "空表列表返回 400"
    (let [response (gen/download-code {:gen-service mock-gen-service} {:body-params {}})]
      (is (= 200 (:status response)))
      (is (= 400 (get-in response [:body :code])))
      (is (= "请选择要生成的表" (get-in response [:body :msg]))))))

(deftest test-download-code-success
  (testing "批量生成并打包下载"
    (let [response (gen/download-code {:gen-service mock-gen-service}
                                      {:body-params {:tables ["sys_gen_test"]}})]
      (try
        (is (= 200 (:status response)))
        (is (= "application/zip" (get-in response [:headers "Content-Type"])))
        (is (some? (:body response)))
        (finally
          (when-let [body (:body response)]
            (when (instance? java.io.File body)
              (let [zip-path (.getPath body)
                    temp-dir (io/file (.replace zip-path ".zip" ""))]
                (.delete body)
                (doseq [f (reverse (file-seq temp-dir))]
                  (.delete f))))))))))
