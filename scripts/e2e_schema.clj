(ns e2e-schema
  "验收库的 JDBC 产品、迁移记录、关键 schema 与 seed 断言。"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.ruoyi.config :as config]
   [next.jdbc :as jdbc]))

(defn- scalar [connection sql]
  (-> (jdbc/execute-one! connection [sql]) vals first))

(defn- verify! []
  (let [url (System/getenv "JDBC_URL")
        dialect (System/getenv "EXPECTED_DB")
        expected ({"sqlite" "SQLite" "mysql" "MySQL" "postgresql" "PostgreSQL"} dialect)
        directory (config/migration-dirs (keyword dialect))
        migrations (count (filter #(str/ends-with? (.getName %) ".up.sql")
                                  (file-seq (io/file "resources" directory))))]
    (when-not (str/starts-with? (or url "") (str "jdbc:" dialect ":"))
      (throw (ex-info "验收 JDBC 方言不匹配" {})))
    (with-open [connection (jdbc/get-connection (jdbc/get-datasource {:jdbcUrl url}))]
      (let [metadata (.getMetaData connection)
            actual (.getDatabaseProductName metadata)
            applied (scalar connection "SELECT COUNT(*) FROM schema_migrations")]
        (assert (= expected actual) "实际 JDBC 产品不匹配")
        (assert (pos? migrations) "迁移文件缺失")
        (assert (= migrations applied) "迁移历史必须包含每个 up 文件")
        (doseq [table ["sys_user" "sys_role" "sys_dept" "sys_menu" "sys_dict_type" "sys_dict_data"]]
          (assert (pos? (scalar connection (str "SELECT COUNT(*) FROM " table)))
                  (str "缺少关键 schema/seed: " table)))
        (assert (= 1 (scalar connection "SELECT COUNT(*) FROM sys_user WHERE user_name='admin'"))
                "管理员 seed 不唯一或缺失")
        (println "VERIFIED SCHEMA:" actual (.getDatabaseProductVersion metadata)
                 "migration-count=" applied "seed-tables=6 admin-count=1")))))

(verify!)
(shutdown-agents)
