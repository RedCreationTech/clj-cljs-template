(ns com.ruoyi.test-utils-test
  "系统夹具所有权回归:生命周期全用替身,不连接数据库或打开端口。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.core :as core]
   [com.ruoyi.test-utils :as tu]))

(defn- exercise
  [initial {:keys [start-error stop-error]} body]
  (let [state (atom initial)
        calls (atom [])
        result (with-redefs [core/system state
                             core/start-app (fn [options]
                                              (swap! calls conj [:start options])
                                              (when start-error (throw start-error))
                                              (reset! state {:system/env :test}))
                             core/stop-app (fn []
                                             (swap! calls conj [:stop])
                                             (when stop-error (throw stop-error))
                                             (reset! state nil))]
                 (try
                   {:value ((tu/system-fixture)
                            #(do (swap! calls conj [:body]) (body state)))}
                   (catch Throwable e {:error e})))]
    (assoc result :calls @calls :state @state)))

(def ^:private normal-calls
  [[:start {:opts {:profile :test}}] [:body] [:stop]])

(deftest refuses-existing-systems-test
  (doseq [system [{:system/env :dev} {:system/env :prod}
                  {:system/env :test} {}]]
    (testing (str "不接管已有系统 " system)
      (let [{:keys [error calls state]} (exercise system {} (constantly :unused))]
        (is (= ::tu/system-already-running (:type (ex-data error))))
        (is (re-find #"bb test -n" (ex-message error)))
        (is (empty? calls) "不启动、不执行测试、不关闭")
        (is (identical? system state) "已有状态保持原对象")))))

(deftest owns-started-lifecycle-test
  (testing "正常返回也执行清理"
    (let [{:keys [value calls state]} (exercise nil {} (constantly :done))]
      (is (= :done value))
      (is (= normal-calls calls))
      (is (nil? state))))
  (testing "测试内合法 halt/init 更换系统 map 后仍清理"
    (let [{:keys [calls state]} (exercise nil {} #(reset! % {:system/env :test :rebuilt true}))]
      (is (= normal-calls calls))
      (is (nil? state))))
  (testing "测试已停止系统时不重复清理"
    (let [{:keys [calls state]} (exercise nil {} #(reset! % nil))]
      (is (= (butlast normal-calls) calls))
      (is (nil? state)))))

(deftest preserves-body-error-test
  (let [failure (ex-info "body failed" {})
        {:keys [error calls state]} (exercise nil {} (fn [_] (throw failure)))]
    (is (identical? failure error))
    (is (= normal-calls calls))
    (is (nil? state))))

(deftest does-not-own-failed-start-test
  (let [failure (ex-info "start failed" {})
        {:keys [error calls]} (exercise nil {:start-error failure} (constantly :unused))]
    (is (identical? failure error))
    (is (= [(first normal-calls)] calls) "启动失败不执行测试或停止未知系统")))

(deftest refuses-replacement-system-test
  (doseq [replacement [{:system/env :dev} {:system/env :prod} {}]]
    (let [{:keys [error calls state]} (exercise nil {} #(reset! % replacement))]
      (is (= ::tu/system-replaced (:type (ex-data error))))
      (is (= (butlast normal-calls) calls))
      (is (identical? replacement state)))))

(deftest replacement-preserves-body-error-test
  (let [replacement {:system/env :dev}
        failure (ex-info "body failed" {})
        {:keys [error calls state]} (exercise nil {} (fn [system]
                                                      (reset! system replacement)
                                                      (throw failure)))]
    (is (identical? failure error))
    (is (= [::tu/system-replaced]
           (mapv #(-> % ex-data :type) (.getSuppressed error))))
    (is (= (butlast normal-calls) calls))
    (is (identical? replacement state))))

(deftest cleanup-errors-test
  (let [cleanup (ex-info "stop failed" {})]
    (testing "没有原异常时上报清理错误"
      (let [{:keys [error calls]} (exercise nil {:stop-error cleanup} (constantly :done))]
        (is (identical? cleanup error))
        (is (= normal-calls calls))))
    (testing "清理错误不能覆盖测试的原异常"
      (let [failure (ex-info "body failed" {})
            {:keys [error calls]} (exercise nil {:stop-error cleanup} (fn [_] (throw failure)))]
        (is (identical? failure error))
        (is (= [cleanup] (vec (.getSuppressed error))))
        (is (= normal-calls calls))))))
