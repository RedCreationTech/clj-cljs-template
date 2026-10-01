(ns com.ruoyi.web.controllers.monitor-test
  "数据源监控:必须穿过热切换代理拿到真实的 HikariCP 池信息。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.datasource :as ds]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.web.controllers.monitor :as monitor]))

(defn- with-temp-pool
  "在临时 SQLite 连接池上调用 f,结束关闭池并删掉库文件。"
  [f]
  (let [file (io/file "target/monitor-test.db")
        pool (db/make-hikari-datasource (str "jdbc:sqlite:" (.getPath file)))]
    (try
      (f pool)
      (finally
        (.close pool)
        (io/delete-file file true)))))

(defn- data
  "调用控制器并取出 :data 里的监控字段。"
  [datasource]
  (-> (monitor/datasource-info {:datasource datasource} nil)
      :body
      :data))

(deftest test-datasource-info
  (testing "直接给 Hikari 池:读出真实的产品名、URL 与池参数"
    (with-temp-pool
      (fn [pool]
        (let [info (data pool)]
          (is (re-find #"SQLite" (:db_version info)))
          (is (every? some? (map info [:db_name :max_connections :min_idle
                                       :connection_timeout :idle_timeout :max_lifetime])))
          (is (= 1 (:max_connections info)) "SQLite 强制单连接")))))
  (testing "给装配用的热切换代理:同样要能解包,不能退回 unknown"
    (with-temp-pool
      (fn [pool]
        (let [info (data (ds/delegating-datasource pool))]
          (is (re-find #"SQLite" (:db_version info)))
          (is (number? (:total_connections info)))))))
  (testing "没有数据源时只给通用占位,不抛异常"
    (let [info (data nil)]
      (is (= "unknown" (:db_version info)))
      (is (= 0 (:active_connections info))))))
