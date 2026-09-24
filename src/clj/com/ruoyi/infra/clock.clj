(ns com.ruoyi.infra.clock
  "应用时间。SQL 里不用 CURRENT_TIMESTAMP:SQLite 的 CURRENT_TIMESTAMP 是 UTC,MySQL 取会话时区,
   同一套 SQL 在两个库里写出的时间不一致(SQLite 下「最近操作」会差 8 小时)。
   改为由应用生成服务器本地时间(JVM 默认时区,可用 TZ 或 -Duser.timezone 指定),
   经 with-now 以 :now 参数注入每一次查询,SQL 里写 :now。"
  (:import
   [java.time LocalDateTime]
   [java.time.format DateTimeFormatter]))

(def ^:private formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defn now-str
  "当前本地时间,格式 yyyy-MM-dd HH:mm:ss(两库的 DATETIME / TEXT 列都能直接存)。"
  []
  (.format (LocalDateTime/now) formatter))

(defn- assoc-now [params]
  (if (contains? params :now) params (assoc params :now (now-str))))

(defn with-now
  "包装 query-fn:没有 :now 参数时补上当前时间(调用方显式传入时以调用方为准)。
   支持 query-fn 的两种调用方式 (query-fn query params) 与 (query-fn conn query params & opts)。"
  [query-fn]
  (fn
    ([query params] (query-fn query (assoc-now params)))
    ([conn query params & opts] (apply query-fn conn query (assoc-now params) opts))))
