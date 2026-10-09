(ns com.ruoyi.dev-reload-test
  "内存 Ring 回归:模拟刷新结果,不卸载命名空间、不启动数据库或服务器。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev :as dev]
   [com.ruoyi.web.controllers.system.post :as post]
   [com.ruoyi.web.handler :as handler]
   [com.ruoyi.web.middleware.core :as middleware]
   [com.ruoyi.web.routes.system :as routes]
   [integrant.core :as ig]
   [ring.mock.request :as mock]))

(def ^:private controller-chain
  '[com.ruoyi.web.controllers.system.post com.ruoyi.web.routes.system
    com.ruoyi.web.routes.api com.ruoyi.core])

(defn- build-handler []
  (let [routes (ig/init-key :router/routes {:routes [(routes/system-routes {})]})
        router (ig/init-key :router/core {:routes routes :env :dev})]
    (ig/init-key :handler/ring {:router router})))

(defn- response []
  ((:handler/ring (dev/system)) (mock/request :get "/system/post")))

(defn- with-memory-system [refresh f]
  (let [calls (atom [])
        sentinel (Object.)]
    (with-redefs-fn
      {#'core/system (atom {:system/env :dev dev/nrepl-key sentinel})
       #'handler/ring-handler-atom (atom nil)
       #'middleware/wrap-base (constantly identity)
       #'post/list-posts (fn [_ _] {:status 200 :body :v1})
       #'dev/refresh-source! refresh
       #'dev/halt! (fn []
                     (swap! calls conj :halt)
                     (swap! core/system select-keys dev/keep-keys))
       #'dev/init! (fn []
                     (swap! calls conj :init)
                     (swap! core/system assoc :handler/ring (build-handler))
                     1)}
      (fn []
        (swap! core/system assoc :handler/ring (build-handler))
        (is (= {:status 200 :body :v1} (response)))
        (with-redefs [post/list-posts (fn [_ _] {:status 200 :body :v2})]
          (f calls sentinel))))))

(deftest reload-updates-captured-controller-test
  (with-memory-system
    (constantly {:reloaded controller-chain :stale []})
    (fn [_ sentinel]
      (is (= :v1 (:body (response))) "路由 partial 仍抓着旧函数")
      (is (= {:reloaded 4 :reset []} (dev/reload)))
      (is (= {:status 200 :body :v2} (response)) "rd 必须让真实 Ring handler 用上新函数")
      (is (identical? sentinel (get (dev/system) dev/nrepl-key))))))

(deftest restart-updates-captured-controller-test
  (with-memory-system
    (constantly {:reloaded [] :stale []})
    (fn [calls sentinel]
      (is (= 1 (dev/restart!)))
      (is (= :v2 (:body (response))) "rr 即使无源码变化也重新装配")
      (is (= [:halt :init] @calls))
      (is (identical? sentinel (get (dev/system) dev/nrepl-key))))))

(deftest idle-reload-does-not-rebuild-test
  (with-memory-system
    (constantly {:reloaded [] :stale []})
    (fn [calls _]
      (let [actual (handler/current-ring-handler)]
        (is (= {:reloaded 0 :reset []} (dev/reload)))
        (is (empty? @calls))
        (is (identical? actual (handler/current-ring-handler)))
        (is (= :v1 (:body (response))))))))

(deftest failed-refresh-keeps-handler-test
  (doseq [reload [dev/reload dev/restart!]]
    (with-memory-system
      (fn [] (throw (ex-info "compile failed" {})))
      (fn [calls sentinel]
        (let [actual (handler/current-ring-handler)]
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"compile failed" (reload)))
          (is (empty? @calls) "刷新失败后不能 halt/init")
          (is (identical? actual (handler/current-ring-handler)))
          (is (= :v1 (:body (response))))
          (is (identical? sentinel (get (dev/system) dev/nrepl-key))))))))

(deftest stale-reload-rebuilds-once-test
  (with-memory-system
    (constantly {:reloaded controller-chain :stale ['example/state]})
    (fn [calls _]
      (is (= {:reloaded 4 :reset ["example/state"]} (dev/reload)))
      (is (= [:halt :init] @calls))
      (is (= :v2 (:body (response)))))))

(deftest reload-does-not-start-stopped-system-test
  (doseq [sys [nil {:system/env :dev dev/nrepl-key :repl}]]
    (testing (str "未启动或 halt 后: " sys)
      (let [calls (atom [])]
        (with-redefs-fn
          {#'core/system (atom sys)
           #'dev/refresh-source! (constantly {:reloaded controller-chain :stale []})
           #'dev/reset-components! #(swap! calls conj :reset)}
          (fn []
            (is (= {:reloaded 4 :reset []} (dev/reload)))
            (is (empty? @calls))
            (is (= sys (dev/system)))))))))
