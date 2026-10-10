(ns com.ruoyi.dev-snapshot-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [com.ruoyi.dev-snapshot :as snapshot]
   [com.ruoyi.infra.datasource :as datasource]
   [com.ruoyi.integrant.state :as state])
  (:import
   [com.zaxxer.hikari HikariDataSource HikariPoolMXBean]))

(deftest profile-gate-test
  (with-redefs [datasource/unwrap (fn [_] (throw (AssertionError. "Must not inspect a pool")))]
    (is (= {:status :unavailable :state nil} (snapshot/summarize nil)))
    (doseq [profile [:test :prod nil "dev"]]
      (is (= {:status :forbidden :state nil}
             (snapshot/summarize {:system/env profile :db.sql/connection :secret}))))))

(deftest safe-pool-test
  (let [reads (atom 0)
        bean (reify HikariPoolMXBean
               (getActiveConnections [_] 2)
               (getIdleConnections [_] 3)
               (getTotalConnections [_] 5)
               (getThreadsAwaitingConnection [_] 0)
               (softEvictConnections [_] (throw (AssertionError. "No writes")))
               (suspendPool [_] (throw (AssertionError. "No writes")))
               (resumePool [_] (throw (AssertionError. "No writes"))))
        pool (proxy [HikariDataSource] []
               (getConnection [& _] (swap! reads inc) (throw (AssertionError. "No connections")))
               (getHikariPoolMXBean [] bean))
        system {:system/env :dev :server/http :server :db.sql/connection pool
                :private {:email "NEVER_EXPORT" :phone "NEVER_EXPORT" :body "NEVER_EXPORT"}}
        result (snapshot/summarize system)]
    (is (= {:status :available :active 2 :idle 3 :total 5 :waiting 0}
           (get-in result [:state :pool])))
    (is (= 0 @reads))
    (is (not (str/includes? (pr-str result) "NEVER_EXPORT")))
    (is (< (count (pr-str result)) 1000))
    (is (= :unknown (get-in result [:state :system-generation])))))

(deftest unavailable-and-error-test
  (is (= :unavailable (get-in (snapshot/summarize {:system/env :dev}) [:state :pool :status])))
  (with-redefs [datasource/unwrap (fn [_] (throw (ex-info "NEVER_EXPORT" {:password "secret"})))]
    (is (= {:status :error}
           (get-in (snapshot/summarize {:system/env :dev}) [:state :pool])))))

(deftest snapshot-reads-only-current-system-test
  (let [system (atom {:system/env :dev})
        writes (atom 0)]
    (add-watch system ::writes (fn [& _] (swap! writes inc)))
    (with-redefs [state/system system]
      (let [a (snapshot/snapshot) b (snapshot/snapshot)]
        (is (= 1 (:schema-version a)))
        (is (= :backend (:source a)))
        (is (= (:runtime-id a) (:runtime-id b)))
        (is (re-matches #"\d+:\d+" (:runtime-id a)))
        (is (string? (:captured-at a)))
        (is (= 0 @writes))
        (is (= {:system/env :dev} @system))))))
