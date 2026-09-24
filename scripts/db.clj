;; 数据库维护脚本(在 JVM 里运行,需要 JDBC 驱动):
;;   clojure -M:dev scripts/db.clj reset      清空库:SQLite 删文件,MySQL 删除当前 schema 下所有表
;;   clojure -M:dev scripts/db.clj roundtrip  迁移往返检查:全部 up → 全部 down → 全部 up,验证 down 脚本可用
;; 连接信息与应用一致:JDBC_URL(默认 jdbc:sqlite:rouyi.db)、MIGRATION_DIR(默认 migrations-sqlite)。
;; 通常经由 bb 调用:bb test:mysql、bb db:roundtrip。
(ns db
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]))

(def url (or (System/getenv "JDBC_URL") "jdbc:sqlite:rouyi.db"))
(def migration-dir (or (System/getenv "MIGRATION_DIR") "migrations-sqlite"))
(def mysql? (str/starts-with? url "jdbc:mysql"))

(defn- sqlite-file [] (str/replace url #"^jdbc:sqlite:" ""))

(defn- table-names [ds]
  (->> (jdbc/execute! ds [(if mysql?
                            "SELECT table_name AS t FROM information_schema.tables WHERE table_schema = DATABASE()"
                            "SELECT name AS t FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")])
       (map (comp str val first))))

(defn reset-db! []
  (if mysql?
    (let [ds (jdbc/get-datasource {:jdbcUrl url})
          tables (table-names ds)]
      (jdbc/execute! ds ["SET FOREIGN_KEY_CHECKS = 0"])
      (doseq [t tables] (jdbc/execute! ds [(str "DROP TABLE IF EXISTS `" t "`")]))
      (jdbc/execute! ds ["SET FOREIGN_KEY_CHECKS = 1"])
      (println "已删除" (count tables) "张表"))
    (do (io/delete-file (sqlite-file) true)
        (println "已删除" (sqlite-file)))))

(defn roundtrip! []
  (let [ds (jdbc/get-datasource {:jdbcUrl url})
        cfg {:store :database :migration-dir migration-dir :db {:datasource ds}}
        sys-tables #(count (filter (fn [t] (str/starts-with? t "sys_")) (table-names ds)))]
    (migratus/migrate cfg)
    (let [n-up (sys-tables)]
      (migratus/reset cfg)
      (let [n-again (sys-tables)
            pending (count (migratus/pending-list cfg))]
        (println (format "%s: up 后 %d 张 sys_ 表,down+up 后 %d 张,待执行迁移 %d" migration-dir n-up n-again pending))
        (when (or (not= n-up n-again) (pos? pending) (zero? n-up))
          (println "迁移往返检查失败")
          (System/exit 1))))))

(case (first *command-line-args*)
  "reset" (reset-db!)
  "roundtrip" (roundtrip!)
  (do (println "用法: clojure -M:dev scripts/db.clj reset|roundtrip")
      (System/exit 2)))
(shutdown-agents)
