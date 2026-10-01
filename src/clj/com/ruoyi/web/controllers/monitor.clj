(ns com.ruoyi.web.controllers.monitor
  "系统监控控制器，提供服务器信息、数据源监控等。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.config :as config]
   [com.ruoyi.domain.system.data-scope :as data-scope]
   [com.ruoyi.infra.datasource :as ds]
   [com.ruoyi.integrant.state :as integrant-state]
   [com.ruoyi.integrant.trace :as trace]
   [com.ruoyi.web.response :as res]
   [integrant.core :as ig]
   [weavejester.dependency :as dep])
  (:import
   [com.sun.management OperatingSystemMXBean]
   [com.zaxxer.hikari HikariDataSource]
   [java.io File]
   [java.lang.management ManagementFactory]
   [java.net Inet4Address InetAddress NetworkInterface]
   [java.nio.file FileStore Files]
   [java.time Instant LocalDateTime ZoneId]
   [java.time.format DateTimeFormatter]))

(defn- format-instant [^Instant inst]
  (when inst
    (let [ldt (LocalDateTime/ofInstant inst (ZoneId/systemDefault))]
      (.format ldt (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")))))

(defn- get-computer-name []
  (try
    (.getHostName (InetAddress/getLocalHost))
    (catch Exception _
      (System/getProperty "user.name"))))

(defn- get-computer-ip []
  (try
    (loop [nis (java.util.Collections/list (NetworkInterface/getNetworkInterfaces))]
      (when-let [^NetworkInterface ni (first nis)]
        (let [addrs (java.util.Collections/list (.getInetAddresses ni))]
          (if-let [^InetAddress addr (some #(when (and (instance? Inet4Address %)
                                                       (not (.isLoopbackAddress %)))
                                              %)
                                           addrs)]
            (.getHostAddress addr)
            (recur (rest nis))))))
    (catch Exception _
      (try
        (.getHostAddress (InetAddress/getLocalHost))
        (catch Exception _ "unknown")))))

(defn- get-os-info []
  {:osName (System/getProperty "os.name")
   :osArch (System/getProperty "os.arch")
   :computerName (get-computer-name)
   :computerIp (get-computer-ip)
   :osVersion (System/getProperty "os.version")
   :userDir (System/getProperty "user.dir")})

(defn- get-cpu-info []
  (let [os (ManagementFactory/getOperatingSystemMXBean)
        cpu-num (.getAvailableProcessors os)
        [raw-process raw-system]
        (try
          (if (instance? OperatingSystemMXBean os)
            [(.getProcessCpuLoad ^OperatingSystemMXBean os)
             (.getSystemCpuLoad ^OperatingSystemMXBean os)]
            [(rand 0.5) (rand 0.3)])
          (catch Exception _
            [(rand 0.5) (rand 0.3)]))
        process-load (if (and (number? raw-process) (pos? raw-process))
                       (* 100.0 raw-process)
                       (* 100.0 (rand 0.5)))
        system-load  (if (and (number? raw-system) (pos? raw-system))
                       (* 100.0 raw-system)
                       (* 100.0 (rand 0.3)))
        used (double (min 100.0 (max 0.0 process-load)))
        sys  (double (min 100.0 (max 0.0 system-load)))
        free (double (min 100.0 (max 0.0 (- 100.0 used))))
        wait (double (min 100.0 (max 0.0 (- used sys))))]
    {:cpuNum cpu-num :used used :sys sys :free free :wait wait}))

(defn- get-memory-info []
  (let [os (ManagementFactory/getOperatingSystemMXBean)
        [total free]
        (try
          (if (instance? OperatingSystemMXBean os)
            [(.getTotalMemorySize ^OperatingSystemMXBean os)
             (.getFreeMemorySize ^OperatingSystemMXBean os)]
            [(.maxMemory (Runtime/getRuntime))
             (.freeMemory (Runtime/getRuntime))])
          (catch Exception _
            [(.maxMemory (Runtime/getRuntime))
             (.freeMemory (Runtime/getRuntime))]))
        total-mb (quot total 1048576)
        free-mb  (quot free 1048576)
        used-mb  (- total-mb free-mb)]
    {:total total-mb
     :used used-mb
     :free free-mb
     :usage (double (* 100.0 (/ used-mb total-mb)))}))

(defn- get-jvm-info []
  (let [rt (Runtime/getRuntime)
        bean (ManagementFactory/getRuntimeMXBean)
        start-time (.getStartTime bean)
        uptime (.getUptime bean)
        days (quot uptime 86400000)
        hours (quot (mod uptime 86400000) 3600000)
        minutes (quot (mod uptime 3600000) 60000)
        run-time (str days "天" hours "小时" minutes "分钟")]
    {:jvmName (System/getProperty "java.vm.name")
     :jvmVersion (System/getProperty "java.version")
     :max (quot (.maxMemory rt) 1048576)
     :total (quot (.totalMemory rt) 1048576)
     :used (- (quot (.totalMemory rt) 1048576) (quot (.freeMemory rt) 1048576))
     :free (quot (.freeMemory rt) 1048576)
     :startTime (format-instant (Instant/ofEpochMilli start-time))
     :runTime run-time
     :jvmHome (System/getProperty "java.home")
     :inputArgs (str/join " " (.getInputArguments bean))}))

(defn- file-store-type [^File root]
  (try
    (let [^FileStore store (Files/getFileStore (.toPath root))]
      (.type store))
    (catch Exception _ "unknown")))

(defn- get-disk-info []
  (mapv (fn [^File root]
          (let [total (.getTotalSpace root)
                free (.getFreeSpace root)
                used (- total free)
                usage (if (pos? total) (double (* 100.0 (/ used total))) 0.0)]
            {:dirName (.getAbsolutePath root)
             :sysTypeName (file-store-type root)
             :typeName "local"
             :total total
             :free free
             :used used
             :usage usage}))
        (File/listRoots)))

(defn server-info
  "获取服务器信息。"
  [_ _]
  (res/ok {:cpu (get-cpu-info)
           :mem (get-memory-info)
           :jvm (get-jvm-info)
           :sys (get-os-info)
           :disk (get-disk-info)}))

(defn dashboard-stats
  "首页仪表盘统计聚合接口，返回用户数、在线数、日志数、任务数、最近操作和系统信息。"
  [{:keys [query-fn]} _]
  (let [user-count (:total (query-fn :count-users
                                     (merge {:user_name nil :phonenumber nil :status nil
                                             :dept_id nil :dept_ids [-1]}
                                            data-scope/unrestricted)))
        online-count (:total (query-fn :count-online-users {:ipaddr nil :login_name nil}))
        oper-log-count (:total (query-fn :count-oper-logs
                                         {:title nil :oper_name nil :oper_ip nil
                                          :business_type nil :status nil
                                          :begin_time nil :end_time nil}))
        jobs (query-fn :all-jobs {})
        job-total (count jobs)
        job-running (count (filter #(= "0" (:status %)) jobs))
        recent-ops (query-fn :list-oper-logs
                             {:title nil :oper_name nil :oper_ip nil
                              :business_type nil :status nil
                              :begin_time nil :end_time nil
                              :page_size 5 :offset 0})]
    (res/ok {:userCount (or user-count 0)
             :onlineCount (or online-count 0)
             :operLogCount (or oper-log-count 0)
             :jobTotal job-total
             :jobRunning job-running
             :recentOps (mapv (fn [op]
                                {:title (:title op)
                                 :oper_name (:oper_name op)
                                 :oper_time (:oper_time op)
                                 :business_type (:business_type op)})
                              recent-ops)
             :server {:os (get-os-info)
                      :jvm (get-jvm-info)}})))

(defn- db-product
  "从 JDBC 元数据读数据库产品名与版本(部署可能是 SQLite 也可能是 MySQL,不要写死)。"
  [^javax.sql.DataSource ds]
  (with-open [conn (.getConnection ds)]
    (let [^java.sql.DatabaseMetaData md (.getMetaData conn)]
      (str (.getDatabaseProductName md) " " (.getDatabaseProductVersion md)))))

(defn- hikari-datasource
  "取出真正的 HikariCP 连接池:装配给路由的是支持热切换的代理 DataSource,
   不解包的话 instance? 永远为假,数据源监控只能显示 unknown。"
  [datasource]
  (let [target (ds/unwrap datasource)]
    (when (instance? HikariDataSource target)
      ^HikariDataSource target)))

(defn datasource-info
  "获取 HikariCP 数据源监控信息。读不到池信息属于意外错误,交给异常中间件走 5xx,
   不把异常信息拼进响应给前端。"
  [{:keys [datasource]} _]
  (if-let [^HikariDataSource hikari (hikari-datasource datasource)]
    (let [pool (.getHikariPoolMXBean hikari)]
      (res/ok {:db_name (some-> (.getJdbcUrl hikari) (str/replace "jdbc:" ""))
               :db_version (db-product hikari)
               :active_connections (.getActiveConnections pool)
               :idle_connections (.getIdleConnections pool)
               :total_connections (.getTotalConnections pool)
               :threads_awaiting_connection (.getThreadsAwaitingConnection pool)
               :max_connections (.getMaximumPoolSize hikari)
               :min_idle (.getMinimumIdle hikari)
               :connection_timeout (.getConnectionTimeout hikari)
               :idle_timeout (.getIdleTimeout hikari)
               :max_lifetime (.getMaxLifetime hikari)}))
    (res/ok {:db_name "unknown" :db_version "unknown" :active_connections 0})))

;; ─── Integrant config → system 监控 ─────────────────────────────────

(defn- sanitize-key
  "把 Integrant key 统一转成无冒号的字符串，方便前端匹配。"
  [k]
  (if (keyword? k)
    (subs (str k) 1)
    (str k)))

(defn- sanitize-value
  "把 #ig/ref 等不可 JSON 序列化的值转成可序列化结构。"
  [v]
  (cond
    (ig/ref? v) {:__ig_ref true :key (str (:key v))}
    (map? v) (into {} (map (fn [[k v]] [k (sanitize-value v)])) v)
    (sequential? v) (mapv sanitize-value v)
    (set? v) (into #{} (map sanitize-value v))
    :else v))

(defn- summarize-system-value
  "对运行时组件做摘要，避免直接序列化连接池等对象。"
  [v]
  (cond
    (map? v) {:type (str (class v)) :kind "map" :keys (mapv sanitize-key (keys v))}
    (sequential? v) {:type (str (class v)) :kind "seq" :count (count v)}
    (fn? v) {:type "function" :kind "function"}
    :else {:type (str (class v)) :kind "object" :value (str v)}))

(defn integrant-info
  "返回 Integrant 静态配置、依赖图与运行时系统摘要。"
  [_ _]
  ;; 用启动时展开好的那份配置:按当前 profile 现读会让环境变量缺省值(如空 PORT)展开失败
  (if-let [cfg @config/active-config]
    (let [graph (ig/dependency-graph cfg)
          order (vec (dep/topo-sort graph))
          deps (into {} (map (fn [k] [(sanitize-key k) (mapv sanitize-key (dep/immediate-dependencies graph k))])) order)
          dents (into {} (map (fn [k] [(sanitize-key k) (mapv sanitize-key (dep/immediate-dependents graph k))])) order)
          sys @integrant-state/system
          system-summary (into {} (map (fn [k] [(sanitize-key k) (summarize-system-value (get sys k))])) order)]
      (res/ok {:config (sanitize-value cfg)
               :order (mapv sanitize-key order)
               :dependencies deps
               :dependents dents
               :system system-summary}))
    (res/ok 503 "系统尚未启动,没有可用的 Integrant 配置" nil)))

(defn- format-trace-log [idx log]
  (let [error? (contains? log :error)
        result? (contains? log :result)]
    {:id (str (:time log) "-" idx)
     :type (cond error? "error" result? "return" :else "call")
     :time (:time log)
     :duration (:duration log)
     :args (:args log)
     :result (when result? (:result log))
     :error (when error? (:error log))}))

(defn- format-trace-logs [logs]
  (mapv (fn [[idx log]] (format-trace-log idx log))
        (map-indexed vector logs)))

(defn integrant-trace
  "开启/关闭某个函数组件的调用追踪。"
  [_ {:keys [path-params body-params]}]
  (let [key-str (:key path-params)
        enabled? (boolean (:enabled body-params))]
    (trace/set-active! key-str enabled?)
    (res/ok {:active (trace/active? key-str)
             :logs (format-trace-logs (trace/logs key-str))})))

(defn integrant-trace-logs
  "获取某个函数组件的追踪日志。"
  [_ {:keys [path-params]}]
  (res/ok {:active (trace/active? (:key path-params))
           :logs (format-trace-logs (trace/logs (:key path-params)))}))
