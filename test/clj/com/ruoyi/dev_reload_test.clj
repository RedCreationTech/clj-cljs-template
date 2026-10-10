(ns com.ruoyi.dev-reload-test
  "内存 Ring 回归:模拟刷新结果,不卸载命名空间、不启动数据库或服务器。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev :as dev]
   [com.ruoyi.integrant.state :as state]
   [com.ruoyi.integrant.trace :as trace]
   [com.ruoyi.web.controllers.system.post :as post]
   [com.ruoyi.web.handler :as handler]
   [com.ruoyi.web.middleware.core :as middleware]
   [com.ruoyi.web.routes.system :as routes]
   [integrant.core :as ig]
   [ring.mock.request :as mock]))

(def ^:private controller-chain
  '[com.ruoyi.web.controllers.system.post com.ruoyi.web.routes.system
    com.ruoyi.web.routes.api com.ruoyi.core])

(derive ::routes :reitit/routes)

(defmethod ig/init-key ::routes [_ opts]
  #(routes/system-routes opts))

(def ^:private memory-config
  {::routes {:query-fn (ig/ref :db.sql/query-fn)}
   :db.sql/query-fn {}
   :router/routes {:routes (ig/refset :reitit/routes)}
   :router/core {:routes (ig/ref :router/routes) :env :dev}
   :handler/ring {:router (ig/ref :router/core)}})

(defn- build-handler []
  (let [routes (ig/init-key :router/routes {:routes [(routes/system-routes {})]})
        router (ig/init-key :router/core {:routes routes :env :dev})]
    (ig/init-key :handler/ring {:router router})))

(defn- response []
  ((:handler/ring (dev/system)) (mock/request :get "/system/post")))

(defn- with-memory-system [refresh f]
  (let [calls (atom [])
        sentinel (Object.)
        sys (atom (with-meta {:system/env :dev dev/nrepl-key sentinel
                              :db.sql/query-fn identity
                              :server/http (Object.) :cronut/scheduler (Object.)
                              ::business-state (atom {:draft "keep"})}
                    {::ig/origin memory-config}))]
    (with-redefs-fn
      {#'core/system sys
       #'state/system sys
       #'state/ring-handler (atom nil)
       #'trace/registry (atom {})
       #'dev/pending-refresh (atom nil)
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
      (is (= {:reloaded 4 :reset [] :status :http-updated} (dev/reload)))
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
        (is (= {:reloaded 0 :reset [] :status :unchanged} (dev/reload)))
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

(deftest unsafe-reload-requires-restart-test
  (doseq [change [{:stale ['example/state]}
                  {:changed-components [:app.system/online-service]}]]
    (with-memory-system
      (constantly (merge {:reloaded controller-chain :stale []} change))
      (fn [calls _]
        (is (= :restart-required (:status (dev/reload))))
        (is (empty? @calls) "不擅自重启数据库、调度器或端口")
        (is (= :v1 (:body (response))))))))

(deftest reload-does-not-start-stopped-system-test
  (doseq [sys [nil {:system/env :dev dev/nrepl-key :repl}]
          stale [[] ['example/state]]]
    (testing (str "未启动或 halt 后: " sys)
      (let [calls (atom [])]
        (with-redefs-fn
          {#'core/system (atom sys)
           #'dev/pending-refresh (atom nil)
           #'dev/refresh-source! (constantly {:reloaded controller-chain :stale stale})
           #'dev/reset-components! #(swap! calls conj :reset)}
          (fn []
            (is (= {:reloaded 4 :reset [] :status :stopped} (dev/reload)))
            (is (empty? @calls))
            (is (= sys (dev/system)))))))))

(deftest http-reload-preserves-running-components-test
  (with-memory-system
    (constantly {:reloaded controller-chain :stale []})
    (fn [calls _]
      (let [before (dev/system)]
        (is (= :http-updated (:status (dev/reload))))
        (is (empty? @calls))
        (doseq [k [:db.sql/query-fn :server/http :cronut/scheduler ::business-state dev/nrepl-key]]
          (is (identical? (get before k) (get (dev/system) k)) (str k)))
        (is (= {:draft "keep"} @(::business-state (dev/system))))))))

(deftest failed-http-build-keeps-old-handler-test
  (with-memory-system
    (constantly {:reloaded controller-chain :stale []})
    (fn [calls _]
      (let [actual (handler/current-ring-handler)
            before (dev/system)]
        (with-redefs [handler/build-ring-handler (fn [_] (throw (ex-info "bad router" {})))]
          (is (= :restart-required (:status (dev/reload)))))
        (is (identical? actual (handler/current-ring-handler)))
        (is (= before (dev/system)))
        (is (= :v1 (:body (response))))
        (is (empty? @calls))))))

(deftest missing-dependency-does-not-initialize-resources-test
  (with-memory-system
    (constantly {:reloaded controller-chain :stale []})
    (fn [calls _]
      (swap! core/system dissoc :db.sql/query-fn)
      (is (= :restart-required (:status (dev/reload))))
      (is (not (contains? (dev/system) :db.sql/query-fn)))
      (is (= :v1 (:body (response))))
      (is (empty? @calls)))))

(deftest active-trace-blocks-until-stopped-test
  (let [refreshes (atom 0)]
    (with-memory-system
      #(if (= 1 (swap! refreshes inc))
         {:reloaded controller-chain :stale []}
         {:reloaded [] :stale []})
      (fn [calls _]
        (trace/start! "handler/ring")
        (is (= :trace-active (:status (dev/reload))))
        (is (trace/active? "handler/ring"))
        (is (= :v1 (:body (response))))
        (trace/stop! "handler/ring")
        (is (= :http-updated (:status (dev/reload))) "无新文件改动也会重试 pending HTTP 装配")
        (is (= :v2 (:body (response))))
        (trace/start! "handler/ring")
        (trace/stop! "handler/ring")
        (is (= :v2 (:body (response))) "停止追踪不会复活旧 handler")
        (is (empty? @calls))))))
