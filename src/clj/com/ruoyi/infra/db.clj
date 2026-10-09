(ns com.ruoyi.infra.db
  "数据库抽象层 — 支持 SQLite、MySQL 和 PostgreSQL。"
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.ruoyi.infra.datasource :as ds]
   [com.ruoyi.config :as config]
   [migratus.core]
   [next.jdbc :as jdbc]
   [next.jdbc.transaction :as transaction]))

;; ─── 数据库类型检测 ──────────────────────────────────────────────────────

(defn detect-db-type
  "检测数据库类型。支持 DataSource、Connection 以及 next.jdbc 包装对象。"
  [db]
  (let [connable (or (:connectable db) db)]
    (try
      (let [product-name (try
                           (.getDatabaseProductName (.getMetaData connable))
                           (catch Exception _
                             (with-open [conn (jdbc/get-connection connable)]
                               (.getDatabaseProductName (.getMetaData conn)))))]
        (cond
          (str/includes? (str/lower-case product-name) "sqlite") :sqlite
          (str/includes? (str/lower-case product-name) "mysql") :mysql
          (str/includes? (str/lower-case product-name) "postgresql") :postgresql
          :else :unknown))
      (catch Exception _ :unknown))))

(defn- id-query-name [db query-name]
  (case (detect-db-type db)
    :mysql (keyword (str (name query-name) "-mysql"))
    :postgresql (keyword (str (name query-name) "-postgresql"))
    :sqlite query-name
    (if (nil? db) query-name
        (throw (ex-info "无法识别数据库方言,不能查询生成主键" {})))))

(defn last-insert-id
  "查询当前连接最近生成的 ID;必须与插入使用同一事务连接。"
  ([query-fn db]
   (last-insert-id query-fn db :last-insert-rowid :last_insert_rowid))
  ([query-fn db query-name result-key]
   (get (query-fn db (id-query-name db query-name) {}) result-key)))

(defn insert-and-get-id!
  "同一事务连接内插入并获取生成 ID,支持 SQLite/MySQL/PostgreSQL。"
  ([query-fn db insert-query params]
   (insert-and-get-id! query-fn db insert-query params :last-insert-rowid :last_insert_rowid))
  ([query-fn db insert-query params id-query id-key]
   (if (some? db)
     (binding [transaction/*nested-tx* :ignore]
       (jdbc/with-transaction [tx db]
         (query-fn tx insert-query params)
         (last-insert-id query-fn tx id-query id-key)))
     (do
       (query-fn insert-query params)
       (get (query-fn id-query {}) id-key)))))

;; ─── SQL 方言转换 ──────────────────────────────────────────────────────

(defn sqlite->mysql
  "将 SQLite SQL 转换为 MySQL 兼容 SQL。"
  [sql]
  (-> sql
      ;; SQLite 的 AUTOINCREMENT -> MySQL 的 AUTO_INCREMENT
      (str/replace #"AUTOINCREMENT" "AUTO_INCREMENT")
      ;; SQLite 的 INTEGER PRIMARY KEY -> MySQL 的 BIGINT PRIMARY KEY AUTO_INCREMENT
      (str/replace #"INTEGER PRIMARY KEY" "BIGINT PRIMARY KEY AUTO_INCREMENT")
      ;; SQLite 的 datetime('now') -> MySQL 的 NOW()
      (str/replace #"datetime\('now'\)" "NOW()")
      ;; SQLite 的 date('now') -> MySQL 的 CURDATE()
      (str/replace #"date\('now'\)" "CURDATE()")
      ;; SQLite 的 julianday -> MySQL 的 DATEDIFF
      (str/replace #"julianday\(([^)]+)\)\s*-\s*julianday\(([^)]+)\)" "DATEDIFF($1, $2)")
      ;; SQLite 的 GROUP_CONCAT -> MySQL 的 GROUP_CONCAT
      (str/replace #"group_concat" "GROUP_CONCAT")
      ;; SQLite 的 PRAGMA -> MySQL 的 SHOW
      (str/replace #"PRAGMA table_info\(([^)]+)\)" "DESCRIBE $1")
      ;; SQLite 的 sqlite_master -> MySQL 的 information_schema
      (str/replace #"sqlite_master" "information_schema.tables")
      ;; SQLite 的 type='table' -> MySQL 的 table_type='BASE TABLE'
      (str/replace #"type='table'" "table_type='BASE TABLE'")))

(defn mysql->sqlite
  "将 MySQL SQL 转换为 SQLite 兼容 SQL。"
  [sql]
  (-> sql
      ;; MySQL 的 AUTO_INCREMENT -> SQLite 的 AUTOINCREMENT
      (str/replace #"AUTO_INCREMENT" "AUTOINCREMENT")
      ;; MySQL 的 BIGINT PRIMARY KEY AUTO_INCREMENT -> SQLite 的 INTEGER PRIMARY KEY
      (str/replace #"BIGINT PRIMARY KEY AUTO_INCREMENT" "INTEGER PRIMARY KEY")
      ;; MySQL 的 NOW() -> SQLite 的 datetime('now')
      (str/replace #"NOW\(\)" "datetime('now')")
      ;; MySQL 的 CURDATE() -> SQLite 的 date('now')
      (str/replace #"CURDATE\(\)" "date('now')")
      ;; MySQL 的 DATEDIFF -> SQLite 的 julianday
      (str/replace #"DATEDIFF\(([^,]+),\s*([^)]+)\)" "julianday($1) - julianday($2)")
      ;; MySQL 的 DESCRIBE -> SQLite 的 PRAGMA table_info
      (str/replace #"DESCRIBE\s+(\w+)" "PRAGMA table_info($1)")))

;; ─── 数据库兼容层 ──────────────────────────────────────────────────────

(defn adapt-sql
  "根据数据库类型适配 SQL。"
  [db sql]
  (let [db-type (detect-db-type db)]
    (case db-type
      :mysql (sqlite->mysql sql)
      :sqlite sql
      sql)))

;; ─── 运行时数据库热切换 ──────────────────────────────────────────────

(defn make-hikari-datasource
  "根据 JDBC URL 创建 HikariCP 连接池。"
  [jdbc-url & [{:keys [pool-size]}]]
  (let [pool-size (or pool-size 5)
        hc (doto (com.zaxxer.hikari.HikariConfig.)
             (.setJdbcUrl jdbc-url)
             (.setMaximumPoolSize
              (int (if (.contains jdbc-url "sqlite") 1 pool-size)))
             (.setMinimumIdle
              (int (if (.contains jdbc-url "sqlite") 1 1)))
             (.setConnectionTestQuery "SELECT 1")
             (.setValidationTimeout 3000))]
    (com.zaxxer.hikari.HikariDataSource. hc)))

(defn run-migrations!
  "对指定 DataSource 执行数据库迁移。"
  [datasource migration-dir]
  (let [config {:store :database
                :db {:datasource datasource}
                :migrate-on-init? false
                :migration-dir migration-dir}]
    (migratus.core/migrate config)))

(defn- validate-swap! [jdbc-url]
  (let [options (::config/database-options (meta @config/active-config))]
    (when-not options
      (throw (ex-info "数据库启用配置不可用,请先启动系统" {})))
    (config/with-database-selection
      {:database/options (select-keys options [:enabled])
       :db.sql/connection {:jdbc-url jdbc-url}})))

(defn- rebind-queries! [conn]
  (require 'conman.core)
  (let [bind-fn (resolve 'conman.core/bind-connection-map)
        files @@(resolve 'com.ruoyi.integrant.trace/query-filenames)
        bound (:fns (apply bind-fn conn {} files))
        query-fn (fn
                   ([q params] ((:fn (get bound q)) params))
                   ([connection q params & opts]
                    (apply (:fn (get bound q)) connection params opts)))]
    ((resolve 'com.ruoyi.integrant.trace/set-dynamic!) :db.sql/query-fn query-fn)))

(defn swap-db!
  "热切换到已启用数据库。无需重启 JVM。用法: (swap-db! system jdbc-url opts)"
  [system jdbc-url & [{:keys [migration-dir pool-size]}]]
  (validate-swap! jdbc-url)
  (let [conn (:db.sql/connection system)]
    (when-not (com.ruoyi.infra.datasource/swappable? conn)
      (throw (ex-info "db.sql/connection 不是可热切换的 DataSource，请重启 Integrant 系统。"
                      {:type (type conn)})))
    (log/info "[swap-db!] 创建新连接池:" (config/url-db-type jdbc-url))
    (let [new-ds (make-hikari-datasource jdbc-url {:pool-size pool-size})
          migration-dir (or migration-dir
                            (config/migration-dirs (config/url-db-type jdbc-url)))]
      ;; 运行迁移
      (log/info "[swap-db!] 运行迁移 (" migration-dir ")...")
      (try (run-migrations! new-ds migration-dir)
           (catch Exception e
             (.close new-ds)
             (throw e)))
      ;; 替换底层 DataSource
      (let [old-ds (ds/swap-delegate! conn new-ds)]
        (log/info "[swap-db!] 连接池已替换，关闭旧连接池...")
        (try (.close old-ds)
             (catch Exception e
               (log/warn "关闭旧连接池时出错:" (.getMessage e)))))
      (rebind-queries! conn)
      (let [db-type (detect-db-type conn)]
        (log/info "[swap-db!] 完成! 当前数据库类型:" db-type)
        {:db-type db-type :jdbc-url jdbc-url :migration-dir migration-dir}))))
