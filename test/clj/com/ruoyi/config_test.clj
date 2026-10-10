(ns com.ruoyi.config-test
  "配置后处理测试。"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.config :as config]
   [com.ruoyi.infra.datasource :as ds]
   [com.ruoyi.integrant.trace]
   [integrant.core :as ig]))

(defn- cfg [url]
  {:db.sql/connection {:jdbc-url url}
   :db.sql/migrations {:migration-dir "migrations-sqlite"}})

(defn- system-edn-pool
  "system.edn 里 :db.sql/connection 的那组值(为 SQLite 准备的全是 1)。"
  [url]
  (update (cfg url) :db.sql/connection merge config/sqlite-pool))

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

(deftest dialect-pool-test
  (testing "SQLite 强制单连接,避免并发写互相等锁"
    (is (= config/sqlite-pool
           (select-keys (:db.sql/connection
                         (config/with-dialect-pool
                           (assoc-in (cfg "jdbc:sqlite:x.db") [:db.sql/connection :maximum-pool-size] 50) {}))
                        (keys config/sqlite-pool)))))
  (testing "MySQL 沿用 system.edn 里给 SQLite 的那组 1 时,整体换成服务端连接池"
    (is (= config/server-pool
           (select-keys (:db.sql/connection
                         (config/with-dialect-pool (system-edn-pool "jdbc:mysql://h/db") {}))
                        (keys config/server-pool)))))
  (testing "显式配置优先:设过 DB_MAX_ACTIVE 或自己改过数字的都不动"
    (is (= (system-edn-pool "jdbc:mysql://h/db")
           (config/with-dialect-pool (system-edn-pool "jdbc:mysql://h/db") {"DB_MAX_ACTIVE" "50"})))
    (is (= 20 (get-in (config/with-dialect-pool
                        (assoc-in (system-edn-pool "jdbc:mysql://h/db")
                                  [:db.sql/connection :maximum-pool-size] 20) {})
                      [:db.sql/connection :maximum-pool-size]))))
  (testing "不是 JDBC URL 时完全不碰连接池"
    (is (= (cfg "redis://h") (config/with-dialect-pool (cfg "redis://h") {})))))

(deftest prod-warnings-test
  (testing "非 prod 不提示"
    (is (nil? (config/prod-warnings (cfg "jdbc:sqlite:x.db") {}))))
  (testing "prod 用 SQLite 或未设 TZ 时各提示一条"
    (let [warnings (config/prod-warnings (assoc (cfg "jdbc:sqlite:x.db") :system/env :prod) {})]
      (is (= 2 (count warnings)))
      (is (some #(str/index-of % "SQLite") warnings))
      (is (some #(str/index-of % "TZ") warnings))))
  (testing "prod 用 MySQL 且设了 TZ 时不提示"
    (is (empty? (config/prod-warnings (assoc (cfg "jdbc:mysql://h/db") :system/env :prod)
                                      {"TZ" "Asia/Shanghai"})))))

(def ^:private pool-file "target/config-pool-test.db")

(deftest pool-keys-reach-hikari-test
  (testing "兜底值要真的落到 HikariCP 上:键名写成 max-active 会被 conman 静默忽略"
    ;; target/ 未必存在(干净克隆、bb rename 之后第一件事就是跑测试),SQLite 打不开不存在的目录
    (io/make-parents pool-file)
    (let [spec (:db.sql/connection
                (config/with-dialect-pool (cfg (str "jdbc:sqlite:" pool-file)) {}))
          conn ((get-method ig/init-key :db.sql/connection) :db.sql/connection spec)
          ^com.zaxxer.hikari.HikariDataSource pool (ds/get-delegate conn)]
      (try
        (is (= 1 (.getMaximumPoolSize pool)) "SQLite 必须是单连接")
        (is (= 1 (.getMinimumIdle pool)))
        (finally
          (.close pool)
          (io/delete-file pool-file true))))))

(deftest database-selection-test
  (doseq [[db url] [[:sqlite "jdbc:sqlite:test.db"]
                    [:mysql "jdbc:mysql://localhost/test"]
                    [:postgresql "jdbc:postgresql://localhost/test"]]]
    (let [base (cfg url)
          selected (config/with-database-selection base)]
      (is (= base selected) "旧 JDBC_URL 配置继续工作")
      (is (= db (get-in (meta selected) [::config/database-options :type])))
      (is (= base (config/with-database-selection
                    (assoc base :database/options {:enabled (name db) :type (name db)}))))
      (is (= (config/migration-dirs db)
             (get-in (config/with-default-migration-dir base {}) [:db.sql/migrations :migration-dir])))))
  (doseq [options [{:enabled ""} {:enabled "postgres"} {:enabled "sqlite,"}
                   {:enabled "mysql"} {:type "postgresql"} {:type "unknown"} {:type ""}]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/with-database-selection (assoc (cfg "jdbc:sqlite:test.db")
                                                        :database/options options)))))
  (is (thrown? clojure.lang.ExceptionInfo (config/with-database-selection (cfg "jdbc:unknown:test"))))
  (testing "禁用的方言在创建连接池之前失败,错误不回显凭据"
    (try
      (config/with-database-selection
        (assoc (cfg "jdbc:postgresql://localhost/test?password=private")
               :database/options {:enabled "sqlite"}))
      (is false "should reject")
      (catch clojure.lang.ExceptionInfo e
        (is (not (str/includes? (str (.getMessage e) (ex-data e)) "private")))))))

(deftest expanded-selection-test
  (let [selected (config/with-database-selection
                   (assoc (cfg "jdbc:postgresql://localhost/test")
                          :database/options {:enabled "postgresql"}))]
    (is (= (meta selected) (meta (config/expand-config selected))))))
