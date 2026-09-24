(ns com.ruoyi.config-test
  "配置后处理测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.config :as config]))

(defn- cfg [url]
  {:db.sql/connection {:jdbc-url url}
   :db.sql/migrations {:migration-dir "migrations-sqlite"}})

(deftest default-migration-dir-test
  (testing "MySQL URL 且没设 MIGRATION_DIR:自动用 migrations"
    (is (= "migrations" (get-in (config/with-default-migration-dir (cfg "jdbc:mysql://h/db") {})
                                [:db.sql/migrations :migration-dir]))))
  (testing "SQLite 保持默认"
    (is (= "migrations-sqlite" (get-in (config/with-default-migration-dir (cfg "jdbc:sqlite:x.db") {})
                                       [:db.sql/migrations :migration-dir]))))
  (testing "显式设置的 MIGRATION_DIR 优先"
    (is (= "migrations-sqlite" (get-in (config/with-default-migration-dir (cfg "jdbc:mysql://h/db")
                                         {"MIGRATION_DIR" "custom"})
                                       [:db.sql/migrations :migration-dir]))
        "env 里有值时不覆盖(system.edn 已经通过 #env 读入)")))
