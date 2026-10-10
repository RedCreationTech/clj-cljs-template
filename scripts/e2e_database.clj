(ns e2e-database
  "只创建/删除本轮随机命名的隔离验收数据库,不改已有业务库。"
  (:require
   [next.jdbc :as jdbc]))

(defn- settings []
  (let [name (System/getenv "E2E_DB_NAME")
        url (System/getenv "E2E_ADMIN_URL")
        dialect (System/getenv "EXPECTED_DB")]
    (when-not (and (= "true" (System/getenv "CI"))
                   (re-matches #"ruoyi_e2e_[a-f0-9]{32}" (or name ""))
                   (re-find #"^jdbc:(mysql|postgresql)://127\.0\.0\.1:" (or url "")))
      (throw (ex-info "服务端验收库只允许 CI 的本地临时服务和随机专用库名" {})))
    {:name name :url url :dialect dialect}))

(defn- execute! [action]
  (let [{:keys [name url dialect]} (settings)
        ds (jdbc/get-datasource {:jdbcUrl url})]
    (with-open [connection (jdbc/get-connection ds)]
      (let [metadata (.getMetaData connection)
            product (.getDatabaseProductName metadata)
            expected ({"mysql" "MySQL" "postgresql" "PostgreSQL"} dialect)]
        (when-not (= expected product)
          (throw (ex-info "数据库产品与验收配置不符" {:expected expected :actual product})))
        (println "E2E database:" product (.getDatabaseProductVersion metadata) name)
        (case action
          "create" (jdbc/execute! connection [(str "CREATE DATABASE " name)])
          "drop" (jdbc/execute! connection [(str "DROP DATABASE " name)])
          (throw (ex-info "未知验收数据库操作" {:action action})))))))

(execute! (first *command-line-args*))
(shutdown-agents)
