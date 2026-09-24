(ns com.ruoyi.infra.json-test
  "JSON 编码:各种时间类型都输出为本地 \"yyyy-MM-dd HH:mm:ss\"(两库一致)。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.web.middleware.formats :as formats]
   [muuntaja.core :as m])
  (:import
   [java.time Instant LocalDate LocalDateTime ZoneId]))

(def ^:private ldt (LocalDateTime/of 2026 9 24 16 9 57))
(def ^:private instant (.toInstant (.atZone ldt (ZoneId/systemDefault))))

(deftest date-text-test
  (testing "驱动可能返回的各种类型 → 同一段本地时间文本"
    (doseq [v [ldt
               instant
               (java.sql.Timestamp/valueOf ldt)
               (java.util.Date/from instant)
               (.atZone ldt (ZoneId/systemDefault))
               (.toOffsetDateTime (.atZone ldt (ZoneId/systemDefault)))]]
      (is (= "2026-09-24 16:09:57" (json/date-text v)) (str (class v)))))
  (testing "日期只保留年月日"
    (is (= "2026-09-24" (json/date-text (LocalDate/of 2026 9 24))))
    (is (= "2026-09-24" (json/date-text (java.sql.Date/valueOf "2026-09-24"))))))

(deftest write-read-test
  (let [data {:create_time (java.sql.Timestamp/valueOf ldt)
              :at (Instant/from instant)
              :name "张三"}]
    (testing "infra.json(兜底响应、异常、操作日志)"
      (is (= {:create_time "2026-09-24 16:09:57" :at "2026-09-24 16:09:57" :name "张三"}
             (json/read-str (json/write-str data)))))
    (testing "muuntaja 的 JSON 响应与之一致"
      (is (= (json/write-str data)
             (slurp (m/encode formats/instance "application/json" data)))))))
