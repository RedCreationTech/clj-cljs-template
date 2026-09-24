(ns com.ruoyi.infra.kv-test
  "短期键值:内存实现与数据库实现行为一致;数据库实现让多个实例(这里用两个 JdbcStore 模拟)看到同一份状态。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.infra.kv :as kv]
   [com.ruoyi.infra.login-guard :as guard]
   [com.ruoyi.test-utils :refer [system-fixture system-state]]))

(use-fixtures :once (system-fixture))

(defn- query-fn [] (:db.sql/query-fn (system-state)))

(defn- exercise
  "对某个实现跑一遍基本语义。"
  [store]
  (let [now 1000]
    (kv/-clear-prefix! store "t:")
    (testing "写入、覆盖、读取"
      (kv/-put! store "t:a" "1" (+ now 100))
      (kv/-put! store "t:a" "2" (+ now 100))
      (is (= "2" (kv/-get store "t:a" now))))
    (testing "过期后读不到,purge 删除"
      (is (nil? (kv/-get store "t:a" (+ now 100))))
      (kv/-purge! store (+ now 100))
      (kv/-put! store "t:a" "3" (+ now 200))
      (is (= "3" (kv/-get store "t:a" (+ now 150)))))
    (testing "删除与按前缀清空"
      (kv/-put! store "t:b" "x" (+ now 100))
      (kv/-del! store "t:a")
      (is (nil? (kv/-get store "t:a" now)))
      (kv/-clear-prefix! store "t:")
      (is (nil? (kv/-get store "t:b" now))))))

(deftest memory-store-test
  (exercise (kv/memory-store)))

(deftest jdbc-store-test
  (exercise (kv/jdbc-store (query-fn))))

(deftest shared-between-instances-test
  (testing "两个实例(各自的 JdbcStore)共享登录失败计数与锁定"
    (let [a (kv/jdbc-store (query-fn))
          b (kv/jdbc-store (query-fn))
          now (System/currentTimeMillis)
          config {:max-failures 3 :lock-minutes 10}]
      (kv/use-store! a)
      (guard/reset-all!)
      (is (= 2 (guard/record-failure! config "Shared" now)))
      (kv/use-store! b)
      (is (= 1 (guard/record-failure! config " shared " now)) "另一个实例接着计数(用户名忽略大小写与空格)")
      (kv/use-store! a)
      (is (= 0 (guard/record-failure! config "shared" now)))
      (kv/use-store! b)
      (is (some? (guard/locked-until "SHARED" now)) "锁定对所有实例生效")
      (guard/unlock! "shared")
      (kv/use-store! a)
      (is (nil? (guard/locked-until "shared" now))))))
