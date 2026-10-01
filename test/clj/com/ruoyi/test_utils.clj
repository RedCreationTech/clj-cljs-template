(ns com.ruoyi.test-utils
  "走真实 ring handler 的集成测试助手:启动/停止系统,以及按 HTTP 动词发请求。
   名字用大写是为了在读测试时一眼看出请求方法,参数顺序固定为 app path 数据 headers。"
  (:require
   [byte-streams :as bs]
   [clojure.data.json :as json]
   [com.ruoyi.core :as core]
   [peridot.core :as p]))

(defn system-state
  "运行中的系统 map。测试和开发期助手读同一个 atom(com.ruoyi.integrant.state/system),
   没有第二份 integrant.repl 状态 —— 装配只经 core/start-app。"
  []
  @core/system)

(defn system-fixture
  []
  (fn [f]
    (when (nil? (system-state))
      (core/start-app {:opts {:profile :test}}))
    (f)
    (core/stop-app)))

(defn get-response [ctx]
  (-> ctx
      :response
      (update :body (fnil bs/to-string ""))))

(defn GET
  "GET 请求:`params` 是查询参数(字符串键,和 URL 上看到的一致),没有请求体。"
  [app path params headers]
  (-> (p/session app)
      (p/request path
                 :request-method :get
                 :content-type "application/edn"
                 :headers headers
                 :params params)
      (get-response)))

(defn PUT
  "PUT 请求:`body` 是 JSON 请求体(EDN 会先转成 JSON 字符串),不接查询参数。"
  [app path body headers]
  (-> (p/session app)
      (p/request path
                 :request-method :put
                 :content-type "application/json"
                 :headers headers
                 :body (json/write-str body))
      (get-response)))
