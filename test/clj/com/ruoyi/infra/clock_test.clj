(ns com.ruoyi.infra.clock-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.clock :as clock]))

(deftest now-str-test
  (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}" (clock/now-str))))

(deftest with-now-test
  (let [seen (atom nil)
        qf (clock/with-now (fn ([q p] (reset! seen [q p]) :ok)
                             ([conn q p & opts] (reset! seen [conn q p opts]) :ok)))]
    (testing "两参调用补上 :now"
      (is (= :ok (qf :x {:a 1})))
      (is (= :x (first @seen)))
      (is (string? (:now (second @seen)))))
    (testing "显式传入的 :now 不被覆盖;params 为 nil 也能用"
      (qf :x {:now "2000-01-01 00:00:00"})
      (is (= "2000-01-01 00:00:00" (:now (second @seen))))
      (qf :x nil)
      (is (string? (:now (second @seen)))))
    (testing "带连接的调用同样补上"
      (qf :conn :y {} :opt)
      (is (= [:conn :y] (take 2 @seen)))
      (is (string? (:now (nth @seen 2))))
      (is (= [:opt] (nth @seen 3))))))
