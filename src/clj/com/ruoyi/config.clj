(ns com.ruoyi.config
  (:require
   [clojure.string :as str]
   [kit.config :as config]))

(def ^:const system-filename "system.edn")

(def sqlite-pool
  "SQLite 只有一个写者:多连接不会变快,只会在并发写时抛 database is locked。
   键名用 HikariCP 的 maximum-pool-size / minimum-idle,conman 会原样映射到池配置。"
  {:minimum-idle 1 :maximum-pool-size 1})

(def server-pool
  "服务端数据库(MySQL 等)的连接池兜底值:system.edn 里那组 1 是给 SQLite 的,
   换成 MySQL 又忘了调,整个应用会在一个连接上排队。"
  {:minimum-idle 2 :maximum-pool-size 10})

(defn with-default-migration-dir
  "没有显式设置 MIGRATION_DIR 时,按数据库选迁移目录:JDBC_URL 是 MySQL 就用 migrations,
   否则保持 system.edn 里的默认值(migrations-sqlite)。env 为环境变量 map。"
  [config env]
  (let [url (get-in config [:db.sql/connection :jdbc-url])]
    (cond-> config
      (and (str/blank? (get env "MIGRATION_DIR"))
           (string? url)
           (str/starts-with? url "jdbc:mysql")
           (contains? config :db.sql/migrations))
      (assoc-in [:db.sql/migrations :migration-dir] "migrations"))))

(defn jdbc-url
  [config]
  (str (get-in config [:db.sql/connection :jdbc-url])))

(defn sqlite-url?
  [config]
  (str/starts-with? (jdbc-url config) "jdbc:sqlite"))

(defn with-dialect-pool
  "连接池按数据库方言兜底,不区分 profile:
   - SQLite:强制单连接(SQLite 多连接只会互相等锁);
   - 其它(MySQL 等):只有当 maximum-pool-size 还停在 SQLite 的 1 时才抬到 server-pool,
     显式设过 DB_MAX_ACTIVE 或在 system.edn 里改过数字的都不动。"
  [config env]
  (let [url (jdbc-url config)]
    (cond
      (not (re-find #"^jdbc:" url)) config
      (sqlite-url? config) (update config :db.sql/connection merge sqlite-pool)
      :else
      (let [current (get-in config [:db.sql/connection :maximum-pool-size])]
        (if (or (contains? env "DB_MAX_ACTIVE") (not= 1 current))
          config
          (update config :db.sql/connection merge server-pool))))))

(defn prod-warnings
  "prod 启动前的配置体检,返回要打印的提示(不阻止启动)。"
  [config env]
  (when (= :prod (:system/env config))
    (cond-> []
      (sqlite-url? config)
      (conj "生产环境仍在用 SQLite 文件库:写操作全部串行,多实例部署会互相锁住。请设置 JDBC_URL 连到 MySQL。")

      (not (contains? env "TZ"))
      (conj "未设置 TZ:写库与接口返回的时间用 JVM 默认时区,多实例部署时请统一(如 TZ=Asia/Shanghai)。"))))

;; 启动时展开好的那份配置,供监控页展示运行时依赖图(而不是按别的 profile 再读一遍)
(defonce active-config (atom nil))

(defn remember-active-config!
  [cfg]
  (reset! active-config cfg)
  cfg)

(defn system-config
  [options]
  (let [env (System/getenv)]
    (-> (config/read-config system-filename options)
        (with-default-migration-dir env)
        (with-dialect-pool env))))
