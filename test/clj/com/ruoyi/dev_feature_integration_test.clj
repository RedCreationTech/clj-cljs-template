(ns com.ruoyi.dev-feature-integration-test
  "组合回归:真实内存组件重启和 HTTP 重建,不连接数据库或监听端口。"
  (:require
   [clojure.test :refer [deftest is]]
   [com.ruoyi.config :as config]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev :as dev]
   [com.ruoyi.dev-snapshot :as snapshot]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.integrant.state :as state]
   [com.ruoyi.integrant.trace :as trace]
   [com.ruoyi.web.middleware.core :as middleware]
   [integrant.core :as ig]))

(derive ::routes :reitit/routes)

(def ^:private response-version (atom :v1))

(defmethod ig/init-key ::routes [_ {:keys [path]}]
  (let [version @response-version]
    (fn [] [path {:get {:handler (fn [_] {:status 200 :body version})}}])))

(defn- selected-config [path]
  (-> {::routes {:path path}
       :router/routes {:routes (ig/refset :reitit/routes)}
       :router/core {:routes (ig/ref :router/routes) :env :dev}
       :handler/ring {:router (ig/ref :router/core)}
       :database/options {:enabled "postgresql" :type "postgresql"}
       :db.sql/connection {:jdbc-url "jdbc:postgresql://unused/test"}}
      config/with-database-selection
      (dissoc :db.sql/connection)))

(defn- assert-selection! []
  (let [origin (::ig/origin (meta (dev/system)))
        options {:enabled #{:postgresql} :type :postgresql}]
    (is (= options (::config/database-options (meta origin))))
    (is (= options (::config/database-options (meta @config/active-config))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"DB_ENABLED"
                          (db/swap-db! (dev/system) "jdbc:sqlite:forbidden.db")))))

(defn- exercise-restart-and-reload! [repl]
  (dev/init!)
  (let [entry (:handler/ring (dev/system))
        runtime (:runtime-id (snapshot/snapshot))]
    (assert-selection!)
    (is (= :v1 (:body (entry {:request-method :get :uri "/old"}))))
    (with-redefs [config/system-config (fn [_] (selected-config "/new"))]
      (dev/restart!))
    (is (identical? repl (get (dev/system) dev/nrepl-key)))
    (is (= {:fixture true} (get (::ig/origin (meta (dev/system))) dev/nrepl-key)))
    (doseq [k [::ig/origin ::ig/build]]
      (is (not (contains? (get (meta (dev/system)) k) ::discarded))))
    (is (= "/new" (get-in (::ig/origin (meta (dev/system))) [::routes :path])))
    (assert-selection!)
    (reset! response-version :v2)
    (is (= :http-updated (:status (dev/reload))))
    (is (= :v2 (:body (entry {:request-method :get :uri "/new"}))))
    (assert-selection!)
    (is (= runtime (:runtime-id (snapshot/snapshot))))
    (is (= :available (:status (snapshot/snapshot))))
    (let [halted (atom {})]
      (with-redefs [ig/halt-key! (fn [k value] (swap! halted assoc k value))]
        (core/stop-app))
      (is (identical? repl (get @halted dev/nrepl-key)) "最终 stop 必须关闭保留的 nREPL")
      (is (nil? (dev/system))))))

(deftest restart-retains-selection-and-current-http-origin-test
  (let [repl (Object.)
        system (atom (with-meta {:system/env :dev dev/nrepl-key repl}
                       {::ig/origin {dev/nrepl-key {:fixture true} ::discarded {}}
                        ::ig/build {dev/nrepl-key {:fixture true} ::discarded {}}}))]
    (with-redefs-fn
      {#'core/system system
       #'state/system system
       #'state/ring-handler (atom nil)
       #'trace/registry (atom {})
       #'config/active-config (atom nil)
       #'config/system-config (fn [_] (selected-config "/old"))
       #'middleware/wrap-base (constantly identity)
       #'response-version (atom :v1)
       #'dev/pending-refresh (atom nil)
       #'dev/refresh-source! (constantly {:reloaded ['com.ruoyi.web.handler] :stale []})}
      #(exercise-restart-and-reload! repl))))
