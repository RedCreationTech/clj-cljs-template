(ns com.ruoyi.dev-reload-process-test
  "真实磁盘重载在独立 JVM 内验收,不启动数据库、监听端口或修改项目源码。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev :as dev]
   [com.ruoyi.web.middleware.core :as middleware]
   [integrant.core :as ig])
  (:import
   [java.io File]
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]
   [java.util.concurrent TimeUnit]))

(defn- controller-source [version]
  (str "(ns reload-fixture.controller)\n"
       "(defn respond [_ _] {:status 200 :body " (pr-str version) "})\n"))

(def ^:private route-source
  (str "(ns reload-fixture.routes\n"
       "  (:require [reload-fixture.controller :as controller]\n"
       "            [integrant.core :as ig]))\n"
       "(derive :reload-fixture/routes :reitit/routes)\n"
       "(defmethod ig/init-key :reload-fixture/routes [_ _]\n"
       "  (fn [] [\"/probe\" {:get {:handler (partial controller/respond {})}}]))\n"))

(defn- write-source! [root path content]
  (let [file (io/file root path)]
    (io/make-parents file)
    (spit file content)
    file))

(defn- changed-source! [file content]
  (let [previous (.lastModified ^File file)]
    (spit file content)
    ;; Must be newer than the scan, not just the pre-JVM file creation time.
    (when-not (.setLastModified ^File file (+ (max previous (System/currentTimeMillis)) 2000))
      (throw (ex-info "无法更新临时源码时间戳" {})))))

(defn- ensure! [condition message]
  (when-not condition (throw (ex-info message {}))))

(defn- memory-config []
  {:reload-fixture/routes {}
   :router/routes {:routes (ig/refset :reitit/routes)}
   :router/core {:routes (ig/ref :router/routes) :env :dev}
   :handler/ring {:router (ig/ref :router/core)}})

(defn- check-live! [entry sentinels version]
  (ensure! (= {:status 200 :body version}
              (entry {:request-method :get :uri "/probe"}))
           (str "旧 HTTP 入口没有读取更新后的响应: " version))
  (doseq [[key sentinel] sentinels]
    (ensure! (identical? sentinel (get (dev/system) key))
             (str "重载替换了非 HTTP 组件: " key))))

(defn- exercise-reloads! [root]
  (require 'reload-fixture.routes)
  (let [sentinels (zipmap [dev/nrepl-key :server/http :db.sql/connection
                           :cronut/scheduler :fixture/business-state]
                          (repeatedly 5 #(Object.)))
        system (merge (ig/init (memory-config)) {:system/env :dev} sentinels)
        entry (:handler/ring system)
        entry-var (ns-resolve 'com.ruoyi.web.handler 'ring-handler)
        controller (io/file root "reload_fixture/controller.clj")
        handler (io/file root "com/ruoyi/web/handler.clj")]
    (reset! core/system system)
    (dev/init-refresh!)
    (check-live! entry sentinels "v1")
    (changed-source! controller (controller-source "v2"))
    (ensure! (pos? (:reloaded (dev/reload))) "真实控制器文件没有被重载")
    (check-live! entry sentinels "v2")
    ;; Reload the actual handler namespace, while retaining the old server entry.
    (changed-source! handler (str (slurp handler) "\n;; reload probe\n"))
    (changed-source! controller (controller-source "v3"))
    (ensure! (pos? (:reloaded (dev/reload))) "真实 handler 文件没有被重载")
    (ensure! (not (identical? entry-var (ns-resolve 'com.ruoyi.web.handler 'ring-handler)))
             "handler 命名空间没有真正卸载重建")
    (check-live! entry sentinels "v3")
    (println "REAL-DISK-RELOAD-PASSED")))

(defn run-child!
  "仅由本文件的独立 JVM 测试入口调用。"
  [root]
  (with-redefs [dev/refresh-dirs [root]
                middleware/wrap-base (constantly identity)]
    (exercise-reloads! root)))

(defn- prepare-sources! [root]
  (write-source! root "reload_fixture/controller.clj" (controller-source "v1"))
  (write-source! root "reload_fixture/routes.clj" route-source)
  (write-source! root "com/ruoyi/web/handler.clj"
                 (slurp (io/resource "com/ruoyi/web/handler.clj"))))

(defn- child-command [root]
  (let [java (io/file (System/getProperty "java.home") "bin"
                      (if (.startsWith (System/getProperty "os.name") "Windows")
                        "java.exe" "java"))
        classpath (str root File/pathSeparator (System/getProperty "java.class.path"))
        expression (str "(require 'com.ruoyi.dev-reload-process-test) "
                        "(com.ruoyi.dev-reload-process-test/run-child! " (pr-str (str root)) ") "
                        "(shutdown-agents)")]
    [(str java) "-cp" classpath "clojure.main" "-e" expression]))

(defn- run-process! [root]
  (let [output (io/file root "child.log")
        process (-> (ProcessBuilder. ^java.util.List (child-command root))
                    (.redirectErrorStream true)
                    (.redirectOutput output)
                    (.start))]
    (try
      (let [completed? (.waitFor process 120 TimeUnit/SECONDS)]
        {:completed? completed?
         :exit (when completed? (.exitValue process))
         :output (slurp output)})
      (finally
        (when (.isAlive process)
          (.destroyForcibly process)
          (.waitFor process))))))

(deftest actual-files-refresh-in-isolated-jvm-test
  (let [root (.toFile (Files/createTempDirectory "ruoyi-reload-" (make-array FileAttribute 0)))]
    (try
      (prepare-sources! root)
      (let [{:keys [completed? exit output]} (run-process! root)]
        (is completed? (str "子 JVM 120 秒内未完成\n" output))
        (is (= 0 exit) output)
        (is (boolean (re-find #"REAL-DISK-RELOAD-PASSED" output)) output))
      (finally
        (doseq [file (reverse (file-seq root))]
          (io/delete-file file true))))))
