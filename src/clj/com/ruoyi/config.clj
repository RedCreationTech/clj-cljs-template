(ns com.ruoyi.config
  (:require
   [clojure.string :as str]
   [kit.config :as config]))

(def ^:const system-filename "system.edn")

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

(defn system-config
  [options]
  (-> (config/read-config system-filename options)
      (with-default-migration-dir (System/getenv))))
