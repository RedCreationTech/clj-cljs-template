(ns com.ruoyi.infra.db-test
  "数据库抽象层测试。"
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.db :as db]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(defn- fake-db
  "构造一个带有伪数据库元数据的 db 规格。"
  ([product-name]
   {:connectable (reify java.sql.Connection
                   (getMetaData [_]
                     (reify java.sql.DatabaseMetaData
                       (getDatabaseProductName [_] product-name))))})
  ([]
   {:connectable nil}))

(deftest test-detect-db-type
  (testing "数据库类型检测"
    (is (= :sqlite (db/detect-db-type (fake-db "SQLite"))))
    (is (= :sqlite (db/detect-db-type (fake-db "SQLITE 3.45"))))
    (is (= :mysql  (db/detect-db-type (fake-db "MySQL"))))
    (is (= :unknown (db/detect-db-type (fake-db "MariaDB"))))
    (is (= :unknown (db/detect-db-type (fake-db "PostgreSQL"))))
    (is (= :unknown (db/detect-db-type (fake-db ""))))
    (is (= :unknown (db/detect-db-type
                     {:connectable (reify java.sql.Connection
                                     (getMetaData [_]
                                       (throw (Exception. "no metadata"))))})))
    (is (= :unknown (db/detect-db-type (fake-db))))))

(deftest test-sqlite->mysql
  (testing "SQLite SQL 转换为 MySQL"
    (is (= "AUTO_INCREMENT" (db/sqlite->mysql "AUTOINCREMENT")))
    (is (= "BIGINT PRIMARY KEY AUTO_INCREMENT" (db/sqlite->mysql "INTEGER PRIMARY KEY")))
    (is (= "NOW()" (db/sqlite->mysql "datetime('now')")))
    (is (= "CURDATE()" (db/sqlite->mysql "date('now')")))
    (is (= "DATEDIFF(a, b)" (db/sqlite->mysql "julianday(a) - julianday(b)")))
    (is (= "GROUP_CONCAT" (db/sqlite->mysql "group_concat")))
    (is (= "DESCRIBE sys_user" (db/sqlite->mysql "PRAGMA table_info(sys_user)")))
    (is (str/includes? (db/sqlite->mysql "sqlite_master") "information_schema.tables"))
    (is (str/includes? (db/sqlite->mysql "type='table'") "table_type='BASE TABLE'"))))

(deftest test-mysql->sqlite
  (testing "MySQL SQL 转换为 SQLite"
    (is (= "AUTOINCREMENT" (db/mysql->sqlite "AUTO_INCREMENT")))
    (is (= "BIGINT PRIMARY KEY AUTOINCREMENT" (db/mysql->sqlite "BIGINT PRIMARY KEY AUTO_INCREMENT")))
    (is (= "datetime('now')" (db/mysql->sqlite "NOW()")))
    (is (= "date('now')" (db/mysql->sqlite "CURDATE()")))
    (is (= "julianday(a) - julianday(b)" (db/mysql->sqlite "DATEDIFF(a, b)")))
    (is (= "PRAGMA table_info(sys_user)" (db/mysql->sqlite "DESCRIBE sys_user")))))

(deftest test-sql-conversion-roundtrip
  (testing "SQL 转换往返测试"
    (let [sqlite-sql "datetime('now')"
          mysql-sql (db/sqlite->mysql sqlite-sql)
          back-to-sqlite (db/mysql->sqlite mysql-sql)]
      (is (= sqlite-sql back-to-sqlite)))))

(deftest test-adapt-sql
  (testing "根据数据库类型适配 SQL"
    (is (= "SELECT AUTOINCREMENT FROM t" (db/adapt-sql (fake-db "SQLite") "SELECT AUTOINCREMENT FROM t")))
    (is (= "SELECT AUTO_INCREMENT FROM t" (db/adapt-sql (fake-db "MySQL") "SELECT AUTOINCREMENT FROM t")))
    (is (= "SELECT NOW() FROM t" (db/adapt-sql (fake-db "MySQL") "SELECT datetime('now') FROM t")))
    (is (= "SELECT NOW() FROM t" (db/adapt-sql (fake-db "PostgreSQL") "SELECT NOW() FROM t"))
        "未知数据库类型原样返回")))

(deftest test-paginate-query
  (testing "分页查询适配"
    (let [calls (atom [])]
      (with-redefs [jdbc/execute! (fn [db sql-vec opts]
                                    (swap! calls conj {:db db :sql sql-vec :opts opts})
                                    [{:id 1}])]
        (let [db (fake-db "SQLite")
              result (db/paginate-query db "SELECT * FROM sys_user WHERE status = :status" {:status "0"} 2 10)]
          (is (= [{:id 1}] result))
          (is (= 1 (count @calls)))
          (let [{:keys [sql opts]} (first @calls)
                called-db (:db (first @calls))]
            (is (= (:connectable db) called-db))
            (is (= ["0"] (rest sql)))
            (is (str/starts-with? (first sql) "SELECT * FROM sys_user WHERE status = :status LIMIT 10 OFFSET 10"))
            (is (= rs/as-unqualified-kebab-maps (:builder-fn opts)))))))
    (let [calls (atom [])]
      (with-redefs [jdbc/execute! (fn [db sql-vec opts]
                                    (swap! calls conj {:db db :sql sql-vec :opts opts})
                                    [{:id 2}])]
        (let [db (fake-db "MySQL")
              result (db/paginate-query db "SELECT * FROM sys_user" {} 1 20)]
          (is (= [{:id 2}] result))
          (is (str/starts-with? (first (:sql (first @calls))) "SELECT * FROM sys_user LIMIT 20 OFFSET 0"))
          (is (= "MySQL" (-> (first @calls) :db (.getMetaData) (.getDatabaseProductName)))))))))
