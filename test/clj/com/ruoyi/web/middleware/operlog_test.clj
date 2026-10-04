(ns com.ruoyi.web.middleware.operlog-test
  "操作日志中间件测试。"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.domain.system.permission :as permission]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.web.middleware.auth :as auth]
   [com.ruoyi.web.middleware.operlog :as operlog]
   [conman.core :as conman]
   [reitit.ring :as ring]))

(defn- make-capturing-query-fn []
  (let [calls (atom [])]
    (fn [q p] (swap! calls conj [q p]) nil)
    calls))

(deftest test-wrap-oper-log-records-post
  (testing "POST API 请求记录操作日志"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params {:user_name "admin"}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)
          log-entry (second (first @calls))]
      (is (= 200 (:status response)))
      (is (= :create-oper-log! (ffirst @calls)))
      (is (= "post /api/system/user" (:title log-entry)))
      (is (= "admin" (:oper_name log-entry)))
      (is (= 0 (:status log-entry)))
      (is (= "127.0.0.1" (:oper_ip log-entry))))))

(deftest test-wrap-oper-log-skips-get
  (testing "GET 请求不记录日志"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/system/user"
                   :request-method :get
                   :params {}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (empty? @calls)))))

(deftest test-wrap-oper-log-skips-login
  (testing "登录路径不记录日志"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/auth/login"
                   :request-method :post
                   :params {:username "admin" :password "123"}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (empty? @calls)))))

(deftest test-wrap-oper-log-error-status
  (testing "错误响应记录状态为 1"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 500}))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params {}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)
          log-entry (second (first @calls))]
      (is (= 500 (:status response)))
      (is (= 1 (:status log-entry))))))

(deftest test-wrap-oper-log-x-forwarded-for
  (testing "优先使用 X-Forwarded-For IP"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params {}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :headers {"x-forwarded-for" "10.0.0.1"}
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)
          log-entry (second (first @calls))]
      (is (= 200 (:status response)))
      (is (= "10.0.0.1" (:oper_ip log-entry))))))

(deftest test-wrap-oper-log-no-query-fn
  (testing "无 query-fn 时不抛出异常"
    (let [handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params {}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"}]
      (is (= 200 (:status (handler request)))))))

(deftest test-wrap-oper-log-string-params
  (testing "未解析字符串参数只记录占位符，避免原始 JSON/表单里的凭据泄漏"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params "raw-body"
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)
          log-entry (second (first @calls))]
      (is (= 200 (:status response)))
      (is (= "<unstructured>" (:oper_param log-entry))))))

(deftest test-wrap-oper-log-truncates-long-params
  (testing "超长参数被截断并追加省略号"
    (let [calls (make-capturing-query-fn)
          handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          long-value (apply str (repeat 300 "a"))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params {:data long-value}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [q p] (swap! calls conj [q p]) nil)}}
          response (handler request)
          log-entry (second (first @calls))
          oper-param (:oper_param log-entry)]
      (is (= 200 (:status response)))
      (is (= 203 (count oper-param)))
      (is (str/ends-with? oper-param "...")))))

(deftest test-wrap-oper-log-query-fn-exception
  (testing "query-fn 抛异常时不影响响应"
    (let [handler (operlog/wrap-oper-log (fn [_] {:status 200}))
          request {:uri "/api/system/user"
                   :request-method :post
                   :params {}
                   :identity {:user-name "admin"}
                   :remote-addr "127.0.0.1"
                   :components {:query-fn (fn [_ _] (throw (Exception. "db down")))}}]
      (is (= 200 (:status (handler request)))))))

(deftest mask-sensitive-test
  (is (= {"username" "admin" "password" "******" :newPassword "******" :nested {:token "******" :x 1}}
         (operlog/mask-sensitive {"username" "admin" "password" "p" :newPassword "q" :nested {:token "t" :x 1}}))))

(deftest mask-sensitive-covers-sequences-provider-keys-and-url-passwords
  (let [params {:items [{:API_KEY "private-provider" "Authorization" "private-token"}]
                :headers {:Cookie "private-cookie"}
                :url "jdbc:mysql://localhost/test?password=private-db&user=test"
                42 "ordinary-value"}
        masked (operlog/mask-sensitive params)]
    (is (= [{:API_KEY "******" "Authorization" "******"}] (:items masked)))
    (is (= {:Cookie "******"} (:headers masked)))
    (is (= "jdbc:mysql://localhost/test?password=<redacted>&user=test" (:url masked)))
    (is (= "ordinary-value" (get masked 42)))
    (is (not-any? #(str/includes? (json/write-str masked) %)
                  ["private-provider" "private-token" "private-cookie" "private-db"]))))

(defn- capturing-router [calls response data]
  (let [routes [["/api/private" data ["/record" {:post {:handler (constantly response)}}]]]
        router (ring/router routes)
        handler (operlog/wrap-oper-log (ring/ring-handler router) (constantly router))]
    (fn [params]
      (handler {:uri "/api/private/record" :request-method :post :params params
                :identity {:user-name "admin"} :remote-addr "127.0.0.1"
                :components {:query-fn (fn [query entry] (swap! calls conj [query entry]))}}))))

(deftest route-policy-protects-request-and-response-without-changing-result
  (let [calls (atom [])
        response {:status 200 :headers {"content-type" "application/json"}
                  :body {:code 200 :data {:note "private-response"}}}
        handler (capturing-router calls response {:audit/body? false :auth? true})
        result (handler {:note "private-request" :nested [{:password "private-password"}]})
        entry (second (first @calls))]
    (is (= response result))
    (is (= :create-oper-log! (ffirst @calls)))
    (is (= "<redacted>" (:oper_param entry)))
    (is (= "200" (:json_result entry)))
    (is (= "admin" (:oper_name entry)))
    (is (= "post /api/private/record" (:title entry)))
    (is (not-any? #(str/includes? (str @calls) %)
                  ["private-request" "private-response" "private-password"]))))

(deftest default-route-still-redacts-authentication-fields
  (let [calls (atom [])
        handler (capturing-router calls {:status 403 :body {:code 403}} {})]
    (is (= {:status 403 :body {:code 403}}
           (handler {:items [{:token "private-token" :password "private-password"}]})))
    (let [entry (second (first @calls))]
      (is (= 1 (:status entry)))
      (is (not-any? #(str/includes? (str entry) %) ["private-token" "private-password"])))))

(deftest method-policy-overrides-group-default
  (let [calls (atom [])
        router (ring/router [["/api/private" {:audit/body? true}
                              ["/record" {:post {:audit/body? false
                                                 :handler (constantly {:status 200})}}]]])
        handler (operlog/wrap-oper-log (ring/ring-handler router) router)]
    (handler {:uri "/api/private/record" :request-method :post
              :params {:note "private-request"}
              :components {:query-fn (fn [q p] (swap! calls conj [q p]))}})
    (is (= "<redacted>" (:oper_param (second (first @calls)))))))

(defn- stored-logs [pool]
  (with-open [conn (.getConnection ^javax.sql.DataSource pool)
              statement (.createStatement conn)
              rows (.executeQuery statement "SELECT * FROM sys_oper_log")]
    (let [metadata (.getMetaData rows)
          columns (range 1 (inc (.getColumnCount metadata)))]
      (loop [result []]
        (if (.next rows)
          (recur (conj result (into {} (map (fn [column]
                                              [(.getColumnName metadata column) (.getObject rows column)]))
                                    columns)))
          result)))))

(deftest private-route-content-never-enters-real-oper-log-table
  (with-open [pool (db/make-hikari-datasource "jdbc:sqlite::memory:")]
    (with-open [conn (.getConnection pool) statement (.createStatement conn)]
      (.execute statement (first (str/split
                                  (slurp (io/resource "migrations-sqlite/202406110011-create-sys-log.up.sql"))
                                  #"--;;"))))
    (let [bound (conman/bind-connection-map pool {} "sql/log.sql")
          query-fn (fn [q p] ((get-in bound [:fns q :fn]) (assoc p :now "2026-10-05 00:00:00")))
          response {:status 200 :body {:code 200 :data "private-response"}}
          router (ring/router [["/api/private" {:audit/body? false}
                                ["/record" {:post {:handler (constantly response)}}]]])
          handler (operlog/wrap-oper-log (ring/ring-handler router) router)
          request {:uri "/api/private/record" :request-method :post
                   :params {:note "private-request" :password "private-password"}
                   :body "private-raw-body" :body-params {:note "private-decoded-body"}
                   :components {:query-fn query-fn}}]
      (is (= response (handler request)))
      (let [rows (stored-logs pool)]
        (is (= 1 (count rows)))
        (is (= "<redacted>" (get (first rows) "oper_param")))
        (is (= "200" (get (first rows) "json_result")))
        (is (not-any? #(str/includes? (str rows) %)
                      ["private-request" "private-response" "private-password"
                       "private-raw-body" "private-decoded-body"]))))))

(deftest privacy-policy-retains-real-route-authorization
  (let [calls (atom [])
        reached? (atom false)
        router (ring/router [["/api/private" {:audit/body? false :auth? true :middleware [auth/authorize]}
                              ["/record" {:post {:perms "private:record:add"
                                                 :handler (fn [_] (reset! reached? true) {:status 200})}}]]])
        handler (operlog/wrap-oper-log (ring/ring-handler router) router)
        request {:uri "/api/private/record" :request-method :post :params {:note "private-request"}
                 :components {:query-fn (fn [q p] (swap! calls conj [q p]))}}]
    (is (= 401 (:status (handler request))))
    (with-redefs [permission/user-permissions (constantly #{})]
      (is (= 403 (:status (handler (assoc request :identity {:user-id 1 :user-name "user"}))))))
    (is (false? @reached?))
    (is (= ["<redacted>" "<redacted>"] (mapv #(get-in % [1 :oper_param]) @calls)))
    (is (= [1 1] (mapv #(get-in % [1 :status]) @calls)))))
