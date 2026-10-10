(ns tasks.e2e-matrix
  "隔离数据库、真实后端进程与浏览器的完整生命周期。"
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.string :as str]
   [tasks.dev :as dev]
   [tasks.util :as u]))

(defn- execute! [command env]
  (let [{:keys [exit]} @(apply p/process {:inherit true :extra-env env} command)]
    (when-not (zero? exit)
      (throw (ex-info "验收子进程失败" {:exit exit :command command})))))

(defn- fixture-env [directory]
  (let [dialect (or (System/getenv "EXPECTED_DB") "sqlite")
        name (str "ruoyi_e2e_" (str/replace (str (random-uuid)) "-" ""))
        admin-url (System/getenv "JDBC_URL")
        url (if (= dialect "sqlite")
              (str "jdbc:sqlite:" (fs/path directory "database.sqlite"))
              (str/replace (or admin-url "") #"(/)[^/?]+(\?.*)?$"
                           (str "/" name "$2")))]
    (when-not (contains? #{"sqlite" "mysql" "postgresql"} dialect)
      (throw (ex-info "未知数据库" {:dialect dialect})))
    (when-not (str/starts-with? url (str "jdbc:" dialect ":"))
      (throw (ex-info "JDBC_URL 与 EXPECTED_DB 不一致" {:dialect dialect})))
    {"EXPECTED_DB" dialect "DB_TYPE" dialect "DB_ENABLED" dialect
     "JDBC_URL" url "E2E_ADMIN_URL" (or admin-url "") "E2E_DB_NAME" name
     "PORT" "3000" "NREPL_PORT" "7000" "BASE_URL" "http://localhost:3000"
     "UPLOAD_DIR" (str (fs/path directory "uploads"))
     "E2E_STATE_FILE" (str (fs/path directory "restart.json"))
     "SCHEDULER_ENABLED" "false"}))

(defn- database! [action env]
  (when-not (= "sqlite" (get env "EXPECTED_DB"))
    (execute! (into (u/clojure-cmd) ["-M:dev" "scripts/e2e_database.clj" action]) env)))

(defn- start! [env label]
  (let [proc (u/start! (str "[" label "] ") (dev/backend-cmd) {:extra-env env} nil)]
    (loop [attempt 0]
      (cond
        (u/http-ok? "http://localhost:3000/api/health") proc
        (or (not (p/alive? proc)) (>= attempt 300))
        (do (p/destroy-tree proc)
            (throw (ex-info "验收后端未能就绪" {:phase label})))
        :else (do (Thread/sleep 1000) (recur (inc attempt)))))))

(defn- stop! [proc]
  (when proc
    (p/destroy-tree proc)
    (deref proc 30000 nil)
    (loop [attempt 0]
      (when-not (u/port-free? 3000)
        (if (>= attempt 30)
          (throw (ex-info "自己启动的后端没有停止" {}))
          (do (Thread/sleep 1000) (recur (inc attempt))))))))

(defn- browser! [args env phase]
  (execute! (into [(u/npx-cmd) "playwright" "test"] args)
        (assoc env "E2E_REPORT_DIR" (str "playwright-report/" phase))))

(defn verify! [args]
  (u/require-free-ports! [["HTTP" 3000] ["nREPL" 7000]])
  (let [directory (fs/create-temp-dir {:prefix "ruoyi-e2e-"})
        env (fixture-env directory)
        process (atom nil)
        created? (atom false)]
    (try
      (database! "create" env)
      (reset! created? true)
      (reset! process (start! env "initial"))
      (execute! (into (u/clojure-cmd) ["-M:dev" "scripts/e2e_schema.clj"]) env)
      (browser! ["tests/e2e/database-contract.spec.js"] env "database")
      (browser! args env "business")
      (browser! ["--config=playwright.restart.config.js"]
                (assoc env "E2E_RESTART_PHASE" "create") "before-restart")
      (stop! @process)
      (reset! process nil)
      (reset! process (start! env "restarted"))
      (browser! ["--config=playwright.restart.config.js"]
                (assoc env "E2E_RESTART_PHASE" "verify") "after-restart")
      (finally
        (stop! @process)
        (when @created? (database! "drop" env))
        (fs/delete-tree directory)))))
