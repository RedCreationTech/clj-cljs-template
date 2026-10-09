(ns tasks.dev
  "开发与测试相关任务:bb dev / backend / frontend / test / test:mysql / e2e。"
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.string :as str]
   [tasks.util :as u]
   [tasks.vendor :as vendor]))

(defn backend-cmd
  "后端启动命令(录屏任务要在指定端口上单独起一个后端,和 bb backend 用同一条)。"
  []
  (let [{:keys [ns-name]} (u/project)]
    ;; -e 先加载 user:它记录 tools.namespace 的时间基准并给出 (user/rd) (user/rr) 这些短名字,
    ;; 连上 nREPL 就能直接用,不用先 require 一次。
    (into (u/clojure-cmd) ["-M:dev" "-e" "(require 'user)"
                           "-m" (str ns-name ".core")])))

(defn- frontend-cmd []
  [(u/npx-cmd) "shadow-cljs" "watch" "app"])

(defn- reset-sqlite! []
  (doseq [f ["ruoyi.db" "ruoyi.db-journal"]]
    (fs/delete-if-exists f))
  (u/info "已删除本地 SQLite 数据库,启动时会重新迁移并写入种子数据"))

(defn- banner [http-port nrepl-port]
  (println (str "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                "  应用     http://localhost:" http-port "\n"
                "  账号     admin / admin123\n"
                "  nREPL    localhost:" nrepl-port "\n"
                "  Swagger  http://localhost:" http-port "/api\n"
                "  Ctrl+C 停止全部进程\n"
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")))

(defn- watch-until-exit!
  "任一子进程退出就结束另一个并以非 0 退出;Ctrl+C 时由 :shutdown 钩子清理进程树。"
  [procs]
  (loop []
    (Thread/sleep 1000)
    (if-let [[label _] (first (remove (comp p/alive? second) procs))]
      (do (doseq [[_ proc] procs] (p/destroy-tree proc))
          (u/fail! label " 已退出"))
      (recur))))

(defn dev!
  "一键启动后端(含 nREPL)与前端 watch,输出带 [backend]/[frontend] 前缀。
   参数:--reset-db 启动前清空本地 SQLite;--backend-only 只起后端。"
  [args]
  (let [args (set args)
        http-port (u/env-int "PORT" 3000)
        nrepl-port (u/env-int "NREPL_PORT" 7000)
        backend-only? (contains? args "--backend-only")]
    (u/require-free-ports! (cond-> [["HTTP" http-port] ["nREPL" nrepl-port]]
                             (not backend-only?) (conj ["shadow-cljs" 9630])))
    (when (contains? args "--reset-db") (reset-sqlite!))
    (u/info "启动后端 …(首次需要下载依赖,可能要几分钟)")
    (let [backend (u/start! "[backend]  " (backend-cmd))]
      (u/wait-http! (str "http://localhost:" http-port "/api/health") 600 #(p/alive? backend))
      (u/info "后端就绪")
      (if backend-only?
        (do (banner http-port nrepl-port)
            (watch-until-exit! [["后端" backend]]))
        (let [_ (vendor/ensure-npm-deps!)
              ready (promise)
              frontend (u/start! "[frontend] " (frontend-cmd) {}
                                 #(when (re-find #"Build completed" %) (deliver ready true)))]
          (u/info "启动前端 watch …(首次编译约 1~3 分钟)")
          (future @ready (banner http-port nrepl-port))
          (watch-until-exit! [["后端" backend] ["前端" frontend]]))))))

(defn backend! [] (u/exec! (backend-cmd)))

(defn frontend! []
  (vendor/ensure-npm-deps!)
  (u/exec! (frontend-cmd)))

(defn test!
  "后端测试。默认用独立的 test.db(不碰开发库 ruoyi.db),每次从空库迁移;
   额外参数原样传给 cognitect test-runner,例如 bb test -n com.ruoyi.web.handler-test。"
  [args]
  (when-not (System/getenv "JDBC_URL")
    (fs/delete-if-exists "test.db"))
  (u/exec! (into (u/clojure-cmd) (cons "-M:test" args))))

(defn- mysql-test-environment []
  (select-keys (System/getenv) ["JDBC_URL" "MYSQL_TEST_PORT" "COMPOSE_PROJECT_NAME"]))

(defn- mysql-test-port []
  (let [setting (get (mysql-test-environment) "MYSQL_TEST_PORT")
        port (if (str/blank? setting) 3308 (parse-long setting))]
    (when-not (and port (<= 1 port 65535))
      (u/fail! "MYSQL_TEST_PORT 必须是 1~65535 之间的整数"))
    port))

(defn- compose-project-name
  "用 checkout 路径区分同名模板副本;显式 COMPOSE_PROJECT_NAME 仍可覆盖。"
  []
  (if-let [setting (not-empty (get (mysql-test-environment) "COMPOSE_PROJECT_NAME"))]
    setting
    (let [project-name (-> (:name (u/project)) str/lower-case
                           (str/replace #"[^a-z0-9_-]" "-")
                           (str/replace #"^[^a-z0-9]+" ""))
          digest (.digest (java.security.MessageDigest/getInstance "SHA-256")
                          (.getBytes (str (fs/canonicalize ".")) "UTF-8"))
          suffix (subs (apply str (map #(format "%02x" (bit-and % 255)) digest)) 0 12)]
      (str (if (str/blank? project-name) "app" project-name) "-test-" suffix))))

(defn- compose-url [port]
  (str "jdbc:mysql://127.0.0.1:" port "/ruoyi?user=root&password=password&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"))

(defn- docker-compose-cmd []
  (let [project-name (compose-project-name)]
    (cond
      (u/exe "docker") [(u/exe "docker") "compose" "-p" project-name]
      (u/exe "docker-compose") [(u/exe "docker-compose") "-p" project-name]
      :else (u/fail! "没有 docker;请自行准备 MySQL 并设置 JDBC_URL 后重试"))))

(defn- wait-mysql! [compose]
  (u/info "等待 MySQL 就绪 …")
  (loop [n 0]
    (let [{:keys [exit]} @(apply p/process {:out :string :err :string}
                                 (into compose ["exec" "-T" "mysql" "mysqladmin" "ping" "-h127.0.0.1" "-uroot" "-ppassword" "--silent"]))]
      (cond (zero? exit) true
            (> n 90) (u/fail! "MySQL 90 秒内未就绪:" (str/join " " (into compose ["logs" "mysql"])))
            :else (do (Thread/sleep 1000) (recur (inc n)))))))

(defn test-mysql!
  "在 MySQL 上跑后端测试。设置了 JDBC_URL(jdbc:mysql://…)就直接用它;
   否则起当前 checkout 独立的 MySQL,MYSQL_TEST_PORT 默认 3308。每次先清空库再迁移。"
  [args]
  (let [url (or (some-> (get (mysql-test-environment) "JDBC_URL") (#(when (str/starts-with? % "jdbc:mysql") %)))
                (let [port (mysql-test-port)
                      compose (docker-compose-cmd)]
                  (u/info "MySQL 测试 Compose 项目:" (last compose) ",端口:" port)
                  (u/exec! (into compose ["up" "-d" "mysql"]))
                  (wait-mysql! compose)
                  (compose-url port)))
        env {"JDBC_URL" url "MIGRATION_DIR" "migrations"}]
    (u/info "清空 MySQL 库:" (str/replace url #"password=[^&]*" "password=***"))
    (u/exec! (into (u/clojure-cmd) ["-M:dev" "scripts/db.clj" "reset"]) {:extra-env env})
    (u/exec! (into (u/clojure-cmd) (cons "-M:test" args)) {:extra-env env})))

(defn test-postgresql!
  "使用显式 PostgreSQL 测试库;CI 提供一次性 service,不清空已有库。"
  [args]
  (when-not (str/starts-with? (or (System/getenv "JDBC_URL") "") "jdbc:postgresql:")
    (u/fail! "test:postgresql 需要指向独立测试库的 JDBC_URL(jdbc:postgresql:…)。"))
  (test! args))

(defn e2e!
  "Playwright 端到端测试;需要后端已在 BASE_URL(默认 http://localhost:3000)运行。"
  [args]
  (let [base (or (System/getenv "BASE_URL") "http://localhost:3000")]
    (when-not (u/http-ok? (str base "/api/health"))
      (u/fail! "后端未运行:先在另一个终端执行 bb dev(或 bb backend),再运行 bb e2e"))
    (vendor/ensure-npm-deps!)
    (vendor/ensure-browsers!)
    (u/exec! (into [(u/npx-cmd) "playwright" "test"] args))))

(defn roundtrip!
  "迁移往返检查 up → down → up。没设 JDBC_URL 时用临时 SQLite 文件;
   JDBC_URL 是 MySQL 且没设 MIGRATION_DIR 时自动用 migrations 目录。"
  []
  (let [url (System/getenv "JDBC_URL")
        tmp? (nil? url)
        env (cond-> {}
              tmp? (assoc "JDBC_URL" "jdbc:sqlite:roundtrip.db")
              (and url (str/starts-with? url "jdbc:mysql") (not (System/getenv "MIGRATION_DIR")))
              (assoc "MIGRATION_DIR" "migrations"))]
    (when tmp? (fs/delete-if-exists "roundtrip.db"))
    (try
      (u/exec! (into (u/clojure-cmd) ["-M:dev" "scripts/db.clj" "roundtrip"]) {:extra-env env})
      (finally
        (when tmp? (fs/delete-if-exists "roundtrip.db"))))))
