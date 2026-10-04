(ns com.ruoyi.web.middleware.operlog
  "操作日志中间件：自动记录所有 API 请求到 sys_oper_log 表。
  本中间件挂在路由器外层(wrap-base),看不到路由级 auth 中间件写入的 :identity,
  因此操作人从 Authorization 头自行解析(无效/缺失则记为 anonymous)。"
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.infra.redact :as redact]
   [com.ruoyi.infra.security :as security]
   [reitit.core :as reitit]))

(def ^:private skip-paths
  "不记录日志的路径"
  #{"/api/auth/login" "/api/health" "/api/user/profile"})

(def ^:private get-methods
  "GET 请求不记录日志（仅 health 和 login 特殊处理）"
  #{:get :head :options})

(defn mask-sensitive
  "参数里的密码、令牌、密钥类字段替换成 ******,不写进日志。"
  [params]
  (cond
    (map? params) (into {} (map (fn [[k v]]
                                  [k (if (redact/secret-key? k) "******" (mask-sensitive v))])) params)
    (sequential? params) (mapv mask-sensitive params)
    (set? params) (into #{} (map mask-sensitive params))
    (string? params) (redact/url params)
    (or (nil? params) (number? params) (boolean? params) (keyword? params)) params
    :else "<opaque>"))

(defn- format-params
  "格式化请求参数(敏感字段打码),过长时截断。"
  [params]
  (let [s (if (string? params)
            "<unstructured>"
            (try (json/write-str (mask-sensitive params))
                 (catch Exception _ "<unavailable>")))]
    (if (> (count s) 200)
      (str (subs s 0 200) "...")
      s)))

(defn- route-data [request router]
  (let [router (if (fn? router) (router) router)
        match (or (:reitit.core/match request)
                  (when router (reitit/match-by-path router (:uri request))))]
    (or (get-in match [:result (:request-method request) :data])
        (merge (:data match) (get-in match [:data (:request-method request)])))))

(defn- log-entry [request response cost-ms router]
  (let [uri (:uri request)
        identity (or (:identity request)
                     (some-> (security/extract-token request) security/parse-token))]
    {:title       (str (name (:request-method request)) " " uri)
     :business_type 0
     :method      ""
     :request_method (name (:request-method request))
     :operator_type 1
     :oper_name   (or (:user-name identity) "anonymous")
     :dept_name   ""
     :oper_url    uri
     :oper_ip     (get-in request [:headers "x-forwarded-for"]
                          (:remote-addr request "127.0.0.1"))
     :oper_location ""
     :oper_param  (if (false? (:audit/body? (route-data request router)))
                    "<redacted>"
                    (format-params (:params request)))
     :json_result (str (:status response))
     :status      (if (>= (:status response) 400) 1 0)
     :error_msg   ""
     :cost_time   cost-ms}))

(defn- recorded? [request]
  (and (str/starts-with? (:uri request) "/api/")
       (not (skip-paths (:uri request)))
       (not (contains? get-methods (:request-method request)))))

(defn wrap-oper-log
  "写入操作日志；路由数据 :audit/body? false 只留操作元信息，不记录请求内容。
   第二参数接收已编译的 router 或零参 router supplier，供外层中间件读取有效路由数据。
   响应始终只记 HTTP 状态，不保留响应 body。"
  ([handler] (wrap-oper-log handler nil))
  ([handler router]
   (fn [request]
     (let [start (System/currentTimeMillis)
           response (handler request)]
       (when (recorded? request)
         (try
           (when-let [query-fn (get-in request [:components :query-fn])]
             (query-fn :create-oper-log!
                       (log-entry request response (- (System/currentTimeMillis) start) router)))
           (catch Exception e
             (log/warn e "Failed to write operation log"))))
       response))))
