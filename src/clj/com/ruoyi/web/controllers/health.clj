(ns com.ruoyi.web.controllers.health
  "健康检查:进程存活 + 数据库可用。数据库不可用时返回 503,方便负载均衡 / 容器编排摘除实例。"
  (:require
   [clojure.tools.logging :as log]
   [next.jdbc :as jdbc]
   [ring.util.http-response :as http-response])
  (:import
   [java.lang.management ManagementFactory]
   [java.util Date]))

(defn db-status
  "执行 SELECT 1 检查数据库;返回 {:status \"up\"/\"down\"/\"unknown\" ...}。
   /api/health 是公开接口且不带鉴权,数据库异常信息(主机、路径、SQL)只写服务端日志。"
  [datasource]
  (if-not datasource
    {:status "unknown"}
    (try
      (let [t0 (System/nanoTime)]
        (jdbc/execute-one! datasource ["SELECT 1"])
        {:status "up" :latency-ms (quot (- (System/nanoTime) t0) 1000000)})
      (catch Exception e
        (log/warn e "数据库健康检查失败")
        {:status "down"}))))

(defn healthcheck!
  [{:keys [datasource]} _req]
  (let [db (db-status datasource)
        up? (not= "down" (:status db))
        ;; Date 交给 infra.json 编码,与其它接口一样是本地 "yyyy-MM-dd HH:mm:ss"
        body {:time     (Date.)
              :up-since (Date. (.getStartTime (ManagementFactory/getRuntimeMXBean)))
              :app      {:status (if up? "up" "down") :message (if up? "" "数据库不可用")}
              :db       db}]
    (if up?
      (http-response/ok body)
      (http-response/service-unavailable body))))
