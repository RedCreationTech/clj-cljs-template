(ns tasks.dev-check
  "MySQL 开发任务回归:只捕获命令,从不启动 Docker、连接数据库或清空真实库。"
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [tasks.dev :as dev]
   [tasks.util :as u]))

(defn- ensure! [condition message]
  (when-not condition (throw (ex-info message {}))))

(defn- captured-run
  [environment {:keys [root executable]
                :or {root "/fixture/checkout-a" executable "docker"}}]
  (let [commands (atom [])
        result (with-redefs [u/exe #(when (= executable %) executable)
                             u/clojure-cmd (constantly ["clojure"])
                             u/project (constantly {:name "sample"})
                             fs/canonicalize (constantly root)
                             u/exec! (fn [cmd & [options]]
                                       (swap! commands conj {:cmd cmd :options options}))
                             u/info (fn [& _])
                             u/fail! (fn [& parts]
                                       (throw (ex-info (apply str parts) {:task-failed? true})))]
                 (with-redefs-fn {(ns-resolve 'tasks.dev 'mysql-test-environment) (constantly environment)
                                  (ns-resolve 'tasks.dev 'wait-mysql!)
                                  #(swap! commands conj {:ready %})}
                   #(try (dev/test-mysql! ["-n" "com.ruoyi.config-test"])
                         nil
                         (catch clojure.lang.ExceptionInfo exception exception))))]
    {:commands @commands :failure result}))

(defn- docker-fallback! []
  (let [{:keys [commands failure]} (captured-run {"MYSQL_TEST_PORT" "3318"} {})
        [up ready reset-db tests] commands
        project (nth (:cmd up) 3)
        expected-url "jdbc:mysql://127.0.0.1:3318/ruoyi?user=root&password=password&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"]
    (ensure! (nil? failure) "有效的 MySQL 测试端口不应失败")
    (ensure! (= ["docker" "compose" "-p" project "up" "-d" "mysql"] (:cmd up)) "启动必须使用显式 Compose 项目")
    (ensure! (= ["docker" "compose" "-p" project] (:ready ready)) "就绪检查必须属于同一个 Compose 项目")
    (ensure! (= expected-url (get-in reset-db [:options :extra-env "JDBC_URL"])) "清库必须使用配置后的测试端口")
    (ensure! (= (:options reset-db) (:options tests)) "清库和测试必须连接同一个库")
    (ensure! (= ["clojure" "-M:test" "-n" "com.ruoyi.config-test"] (:cmd tests)) "测试参数必须透传")
    (ensure! (= "migrations" (get-in tests [:options :extra-env "MIGRATION_DIR"])) "MySQL 必须使用 MySQL 迁移目录")))

(defn- checkout-isolation! []
  (let [project #(nth (:cmd (first (:commands (captured-run {} {:root %})))) 3)
        a (project "/fixture/checkout-a")
        b (project "/fixture/checkout-b")
        explicit (captured-run {"COMPOSE_PROJECT_NAME" "explicit-test"} {})
        legacy (captured-run {} {:executable "docker-compose"})]
    (ensure! (not= a b) "同名模板的不同 checkout 必须隔离 Compose 项目和卷")
    (ensure! (= a (project "/fixture/checkout-a")) "同一 checkout 项目名必须稳定")
    (ensure! (re-matches #"[a-z0-9][a-z0-9_-]*" a) "默认项目名必须满足 Compose 命名规则")
    (ensure! (= "explicit-test" (nth (:cmd (first (:commands explicit))) 3)) "必须尊重 COMPOSE_PROJECT_NAME")
    (ensure! (= ["docker-compose" "-p"] (take 2 (:cmd (first (:commands legacy))))) "必须兼容独立 docker-compose 命令")))

(defn- invalid-ports! []
  (doseq [port ["invalid" "0" "65536" "-1" "1.5"]
          :let [{:keys [commands failure]} (captured-run {"MYSQL_TEST_PORT" port} {})]]
    (ensure! (:task-failed? (ex-data failure)) "非法端口必须明确失败")
    (ensure! (empty? commands) "非法端口不得启动 Docker 或运行清库命令"))
  (doseq [port [nil "" "1" "65535"]
          :let [{:keys [failure]} (captured-run (cond-> {} port (assoc "MYSQL_TEST_PORT" port)) {})]]
    (ensure! (nil? failure) "默认、空值和边界端口必须通过")))

(defn- explicit-database! []
  (let [url "jdbc:mysql://127.0.0.1:4399/explicit?user=fixture&password=fixture"
        {:keys [commands failure]} (captured-run {"JDBC_URL" url "MYSQL_TEST_PORT" "invalid"}
                                                 {:executable nil})]
    (ensure! (nil? failure) "外部 JDBC_URL 不依赖 Docker 或 MYSQL_TEST_PORT")
    (ensure! (= 2 (count commands)) "外部 JDBC_URL 只能运行清库和测试命令")
    (ensure! (every? #(= url (get-in % [:options :extra-env "JDBC_URL"])) commands) "外部 JDBC_URL 必须原样透传")
    (ensure! (every? #(str/starts-with? (first (:cmd %)) "clojure") commands) "外部 JDBC_URL 不得调用 Docker")))

(defn check! []
  (docker-fallback!)
  (checkout-isolation!)
  (invalid-ports!)
  (explicit-database!)
  (u/info "MySQL 开发任务回归通过(命令捕获,未连接数据库)"))
