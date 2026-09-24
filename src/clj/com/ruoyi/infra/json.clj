(ns com.ruoyi.infra.json
  "后端唯一的 JSON 编解码入口(jsonista)。muuntaja 的 API 响应、兜底响应、异常响应、操作日志都用这里的 mapper。

   时间统一输出为服务器本地时区的 \"yyyy-MM-dd HH:mm:ss\",日期输出 \"yyyy-MM-dd\":
   SQLite 存的就是这种文本,MySQL 驱动返回的是 java.sql.Timestamp / java.time 对象,
   不统一的话同一个字段在两个库上会一个是 \"2026-09-24 16:09:57\"、一个是 \"2026-09-24T08:09:57Z\"。"
  (:require
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.core JsonGenerator]
   [java.time Instant LocalDate LocalDateTime OffsetDateTime ZoneId ZonedDateTime]
   [java.time.format DateTimeFormatter]
   [java.util Date]))

(def ^DateTimeFormatter datetime-format
  (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defn- local ^LocalDateTime [^Instant instant]
  (LocalDateTime/ofInstant instant (ZoneId/systemDefault)))

(defprotocol DateText
  (date-text [v] "时间 / 日期对象 → 接口里的文本"))

(extend-protocol DateText
  ;; java.sql.Date 不支持 toInstant,单独处理;Timestamp 按本地时间取值
  java.sql.Date
  (date-text [v] (str (.toLocalDate v)))
  java.sql.Timestamp
  (date-text [v] (.format datetime-format (.toLocalDateTime v)))
  Date
  (date-text [v] (.format datetime-format (local (.toInstant v))))
  Instant
  (date-text [v] (.format datetime-format (local v)))
  OffsetDateTime
  (date-text [v] (.format datetime-format (local (.toInstant v))))
  ZonedDateTime
  (date-text [v] (.format datetime-format (local (.toInstant v))))
  LocalDateTime
  (date-text [v] (.format datetime-format v))
  LocalDate
  (date-text [v] (str v)))

(defn- write-date [v ^JsonGenerator gen]
  (.writeString gen ^String (date-text v)))

(def mapper-options
  "给 muuntaja 的 \"application/json\" :encoder-opts / :decoder-opts 用。"
  {:encoders (into {} (map (fn [c] [c write-date]))
                   [java.sql.Date java.sql.Timestamp Date Instant
                    OffsetDateTime ZonedDateTime LocalDateTime LocalDate])})

(def mapper (j/object-mapper mapper-options))

(def ^:private keyword-mapper (j/object-mapper (assoc mapper-options :decode-key-fn true)))

(defn write-str
  "Clojure 数据 → JSON 字符串。"
  [data]
  (j/write-value-as-string data mapper))

(defn read-str
  "JSON 字符串 → Clojure 数据(键转为关键字)。"
  [s]
  (j/read-value s keyword-mapper))
