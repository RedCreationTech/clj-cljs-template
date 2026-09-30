(ns com.ruoyi.core
  "应用入口。这里 require 的 kit.edge.*、domain.*、web.* 命名空间大多只为加载其中的
   Integrant init-key 注册(副作用),不要因为\"未使用\"而删除;新增组件的命名空间也要加到这里。"
  (:require
   [clojure.tools.logging :as log]
   [com.ruoyi.config :as config]
   [com.ruoyi.domain.system]
   [com.ruoyi.domain.system.config]
   [com.ruoyi.domain.system.dept]
   [com.ruoyi.domain.system.dict]
   [com.ruoyi.domain.system.log]
   [com.ruoyi.domain.system.menu]
   [com.ruoyi.domain.system.post]
   [com.ruoyi.domain.system.role]
   [com.ruoyi.domain.system.user]
   [com.ruoyi.env :refer [defaults]]
   [com.ruoyi.infra.secrets :as secrets]
   [com.ruoyi.integrant.state :as integrant-state]
   [com.ruoyi.integrant.trace]
   [com.ruoyi.web.handler]
   [com.ruoyi.web.middleware.auth]
   [com.ruoyi.web.routes.api]
   [com.ruoyi.web.routes.auth]
   [com.ruoyi.web.routes.monitor]
   [com.ruoyi.web.routes.system]
   [integrant.core :as ig]
   [kit.edge.db.mysql]
   [kit.edge.db.sql.conman]
   [kit.edge.db.sql.migratus]
   [kit.edge.scheduling.quartz]
   [kit.edge.server.undertow]
   [kit.edge.utils.nrepl])
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
 (fn [thread ex]
   (log/error {:what :uncaught-exception
               :exception ex
               :where (str "Uncaught exception on" (.getName thread))})))

(def system integrant-state/system)

(defn stop-app []
  ((or (:stop defaults) (fn [])))
  (some-> (deref system) (ig/halt!))
  ;; 置空,避免之后误用已关闭的组件(如测试夹具据此判断是否需要重新启动)
  (reset! system nil))

(defn start-app [& [params]]
  (let [opts (or (:opts params) (:opts defaults) {})]
    ;; prod 下密钥不安全时在创建任何组件之前就失败
    (secrets/verify! (:profile opts) (System/getenv))
    ((or (:start params) (:start defaults) (fn [])))
    (let [cfg (-> (config/system-config opts) ig/expand)]
      ;; 配置体检只提示不阻止启动:SQLite、没设 TZ 这类默认值能跑,但不是给生产用的
      (doseq [warning (config/prod-warnings cfg (System/getenv))]
        (log/warn warning))
      ;; 监控页展示的就是真正跑起来的那份配置,不能请求时按别的 profile 再读一遍
      (config/remember-active-config! cfg)
      (->> cfg ig/init (reset! system)))))

(defn -main [& _]
  (try
    (start-app)
    (catch Throwable e
      ;; 启动失败必须退出进程:否则已启动的 nREPL 等非守护线程会让 JVM 带着半个系统继续运行
      (log/error e "启动失败")
      (binding [*out* *err*] (println (ex-message e)))
      (System/exit 1)))
  (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop-app) (shutdown-agents))))
  ;; Keep main thread alive so JVM doesn't exit
  (while true (Thread/sleep 60000)))
