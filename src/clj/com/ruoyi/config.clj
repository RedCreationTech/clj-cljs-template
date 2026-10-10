(ns com.ruoyi.config
  (:require
   [clojure.string :as str]
   [kit.config :as config]
   [integrant.core :as ig]))

(def ^:const system-filename "system.edn")

(def sqlite-pool
  "SQLite 只有一个写者:多连接不会变快,只会在并发写时抛 database is locked。
   键名用 HikariCP 的 maximum-pool-size / minimum-idle,conman 会原样映射到池配置。"
  {:minimum-idle 1 :maximum-pool-size 1})

(def server-pool
  "服务端数据库(MySQL 等)的连接池兜底值:system.edn 里那组 1 是给 SQLite 的,
   换成 MySQL 又忘了调,整个应用会在一个连接上排队。"
  {:minimum-idle 2 :maximum-pool-size 10})

(def migration-dirs
  {:sqlite "migrations-sqlite" :mysql "migrations" :postgresql "migrations-postgresql"})

(defn url-db-type
  "只接受明确支持的 JDBC 方言,不包含 URL 的异常避免泄露连接凭据。"
  [url]
  (some->> (re-find #"^jdbc:(sqlite|mysql|postgresql):" (str url)) second keyword))

(defn- enabled-databases [setting]
  (let [enabled (if (string? setting)
                  (mapv (comp keyword str/trim) (str/split setting #"," -1))
                  setting)]
    (when (or (empty? enabled) (some #(not (contains? migration-dirs %)) enabled))
      (throw (ex-info "DB_ENABLED 必须是 sqlite,mysql,postgresql 的非空子集" {})))
    (set enabled)))

(defn with-database-selection
  "DB_TYPE 可省略并从 JDBC_URL 推断;显式选择必须与 URL 一致且已启用。
   选择配置消费后不传给 Integrant/Hikari;元数据供热切换复用相同白名单。"
  [config]
  (let [{:keys [enabled type] :or {enabled "sqlite,mysql,postgresql"}} (:database/options config)
        enabled (enabled-databases enabled)
        selected (when (some? type) (if (keyword? type) type (keyword (str/trim type))))
        actual (url-db-type (get-in config [:db.sql/connection :jdbc-url]))]
    (when-not actual
      (throw (ex-info "JDBC_URL 必须使用 sqlite、mysql 或 postgresql" {})))
    (when (and selected (not= selected actual))
      (throw (ex-info "DB_TYPE 不支持或与 JDBC_URL 不一致" {:db-type selected})))
    (when-not (enabled actual)
      (throw (ex-info "所选数据库未在 DB_ENABLED 中启用" {:db-type actual})))
    (vary-meta (dissoc config :database/options) assoc ::database-options
               {:enabled enabled :type actual})))

(defn with-default-migration-dir
  "没有显式 MIGRATION_DIR 时按 JDBC_URL 选迁移目录。"
  [config env]
  (if (and (str/blank? (get env "MIGRATION_DIR"))
           (contains? config :db.sql/migrations))
    (if-let [dir (migration-dirs (url-db-type (get-in config [:db.sql/connection :jdbc-url])))]
      (assoc-in config [:db.sql/migrations :migration-dir] dir)
      config)
    config))

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
      (conj "生产环境仍在用 SQLite 文件库:写操作全部串行,多实例部署会互相锁住。请设置 JDBC_URL 连到 MySQL 或 PostgreSQL。")

      (not (contains? env "TZ"))
      (conj "未设置 TZ:写库与接口返回的时间用 JVM 默认时区,多实例部署时请统一(如 TZ=Asia/Shanghai)。"))))

;; 启动时展开好的那份配置,供监控页展示运行时依赖图(而不是按别的 profile 再读一遍)
(defonce active-config (atom nil))

(defn expand-config
  "展开 Integrant 引用时保留启动前已验证的数据库白名单。"
  [cfg]
  (with-meta (ig/expand cfg) (meta cfg)))

(defn remember-active-config!
  [cfg]
  (reset! active-config cfg)
  cfg)

(defn system-config
  [options]
  (let [env (System/getenv)]
    (-> (config/read-config system-filename options)
        (with-database-selection)
        (with-default-migration-dir env)
        (with-dialect-pool env))))
