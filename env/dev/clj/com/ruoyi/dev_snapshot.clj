(ns com.ruoyi.dev-snapshot
  "开发期只读摘要:不运行 SQL、不借连接、不返回配置或业务数据。"
  (:require
   [com.ruoyi.infra.datasource :as datasource]
   [com.ruoyi.integrant.state :as state])
  (:import
   [com.zaxxer.hikari HikariDataSource]
   [java.lang.management ManagementFactory]
   [java.time Instant]))

(defn- bounded-count [n]
  (min 1000000 (max 0 n)))

(defn- pool-state [connection]
  (try
    (let [pool (datasource/unwrap connection)]
      (cond
        (nil? pool) {:status :unavailable}
        (not (instance? HikariDataSource pool)) {:status :unknown}
        (.isClosed ^HikariDataSource pool) {:status :closed}
        :else (if-let [bean (.getHikariPoolMXBean ^HikariDataSource pool)]
                {:status :available
                 :active (bounded-count (.getActiveConnections bean))
                 :idle (bounded-count (.getIdleConnections bean))
                 :total (bounded-count (.getTotalConnections bean))
                 :waiting (bounded-count (.getThreadsAwaitingConnection bean))}
                {:status :unavailable})))
    (catch Exception _ {:status :error})))

(defn summarize
  "只接受已读取的系统值;非 dev 在访问组件之前拒绝。"
  [system]
  (cond
    (nil? system) {:status :unavailable :state nil}
    (not= :dev (:system/env system)) {:status :forbidden :state nil}
    :else {:status :available
           :state {:system-generation :unknown
                   :component-count (bounded-count (count (dissoc system :system/env :nrepl/server)))
                   :http-present? (some? (:server/http system))
                   :pool (pool-state (:db.sql/connection system))}}))

(defn snapshot
  "在后端 nREPL 调用;进程身份不代表系统代际或源码已加载。"
  []
  (let [runtime (ManagementFactory/getRuntimeMXBean)]
    (merge {:schema-version 1
            :captured-at (str (Instant/now))
            :source :backend
            :runtime-id (str (.pid (java.lang.ProcessHandle/current)) ":" (.getStartTime runtime))}
           (summarize @state/system))))
