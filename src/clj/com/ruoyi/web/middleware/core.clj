(ns com.ruoyi.web.middleware.core
  (:require
   [clojure.string :as str]
   [com.ruoyi.env :as env]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.web.middleware.operlog :as operlog]
   [ring.middleware.defaults :as defaults]
   [ring.middleware.session.cookie :as cookie]))

(defn parse-origins
  "CORS_ORIGINS(逗号分隔)→ 集合。"
  [s]
  (->> (str/split (or s "") #",") (map str/trim) (remove str/blank?) set))

(defn- cors-headers [origin]
  {"Access-Control-Allow-Origin" origin
   "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type, Authorization"
   "Access-Control-Allow-Credentials" "true"
   "Vary" "Origin"})

(defn wrap-cors
  "CORS:只对白名单里的 Origin 回显 CORS 头并直接应答其预检请求。
   白名单为空(默认)时不加任何 CORS 头 —— 前后端同源部署不需要 CORS;
   \"*\" 表示接受任意来源(回显请求的 Origin,只建议开发时使用)。"
  [handler allowed]
  (fn [request]
    (let [origin (get-in request [:headers "origin"])
          allowed? (boolean (and origin (or (contains? allowed "*") (contains? allowed origin))))]
      (if (and allowed? (= :options (:request-method request)))
        {:status 204 :headers (cors-headers origin) :body ""}
        (let [response (handler request)]
          (if (and allowed? response)
            (update response :headers merge (cors-headers origin))
            response))))))

(defn wrap-revalidate-static
  "非 /api 的 GET 响应(index.html、js、css 等静态资源)默认加 Cache-Control: no-cache:
   浏览器每次用 If-Modified-Since 向服务器确认,文件没变时拿到 304,发版后不会用到旧的 app.js。"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and response
               (#{:get :head} (:request-method request))
               (not (str/starts-with? (str (:uri request)) "/api"))
               (not (get-in response [:headers "Cache-Control"])))
        (assoc-in response [:headers "Cache-Control"] "no-cache")
        response))))

(defn- wrap-query-fn
  "将 query-fn 注入到请求的 :components 中，供 operlog 中间件使用。"
  [handler query-fn]
  (fn [request]
    (handler (assoc request :components {:query-fn query-fn}))))

(defn- wrap-json-body
  "确保响应 body 是字符串（JSON），而非 Clojure 数据结构。"
  [handler]
  (fn [request]
    (let [resp (handler request)]
      (if (map? (:body resp))
        (-> resp
            (assoc :body (json/write-str (:body resp)))
            (assoc-in [:headers "content-type"] "application/json;charset=utf-8"))
        resp))))

(defn wrap-base
  [{:keys [site-defaults-config cookie-secret query-fn cors-origins] :as opts}]
  (let [cookie-store (cookie/cookie-store {:key (.getBytes ^String cookie-secret)})]
    (fn [handler]
      (-> ((:middleware env/defaults) handler opts)
          (defaults/wrap-defaults
           (assoc-in site-defaults-config [:session :store] cookie-store))
          (wrap-cors (parse-origins cors-origins))
          wrap-revalidate-static
          operlog/wrap-oper-log
          (wrap-query-fn query-fn)
          wrap-json-body))))
