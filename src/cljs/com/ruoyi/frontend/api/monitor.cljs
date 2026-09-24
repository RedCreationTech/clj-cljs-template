(ns com.ruoyi.frontend.api.monitor
  "监控相关 API:在线用户、服务器、数据源、缓存、Integrant 依赖与追踪、首页统计。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-online-users
  "获取在线用户列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/online" :params params
              :on-success on-success :on-error on-error}))

(defn get-datasource
  "获取数据源监控信息。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/datasource"
              :on-success on-success :on-error on-error}))

(defn get-server-info
  "获取服务器信息。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/server"
              :on-success on-success :on-error on-error}))

(defn get-dashboard-stats
  "获取首页仪表盘统计数据。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/dashboard/stats"
              :on-success on-success :on-error on-error}))

(defn get-integrant-info
  "获取 Integrant 配置、依赖图与运行时系统摘要。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/integrant"
              :on-success on-success :on-error on-error}))

(defn set-integrant-trace
  "开启/关闭某个 Integrant 函数组件的调用追踪。"
  [key enabled? on-success on-error]
  (t/request {:method :post :uri (str "/system/integrant/trace/" key)
              :params {:enabled enabled?}
              :on-success on-success :on-error on-error}))

(defn get-integrant-trace-logs
  "获取某个 Integrant 函数组件的追踪日志。"
  [key on-success on-error]
  (t/request {:method :get :uri (str "/system/integrant/trace/" key)
              :on-success on-success :on-error on-error}))

(defn get-cache-info
  "获取缓存信息。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/cache"
              :on-success on-success :on-error on-error}))

(defn get-cache-keys
  "获取缓存键列表。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/cache/keys"
              :on-success on-success :on-error on-error}))

(defn clear-cache
  "清空缓存。"
  [on-success on-error]
  (t/request {:method :delete :uri "/system/cache"
              :on-success on-success :on-error on-error}))

(defn get-cache-names
  "获取缓存名称列表。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/cache/getNames"
              :on-success on-success :on-error on-error}))

(defn get-cache-keys-by-name
  "获取指定缓存名称的键列表。"
  [cache-name on-success on-error]
  (t/request {:method :get :uri (str "/system/cache/getKeys/" cache-name)
              :on-success on-success :on-error on-error}))

(defn get-cache-value
  "获取缓存值。"
  [cache-name cache-key on-success on-error]
  (t/request {:method :get :uri (str "/system/cache/getValue/" cache-name "/" cache-key)
              :on-success on-success :on-error on-error}))

(defn clear-cache-name
  "清除指定缓存。"
  [cache-name on-success on-error]
  (t/request {:method :delete :uri (str "/system/cache/clearCacheName/" cache-name)
              :on-success on-success :on-error on-error}))

(defn clear-cache-key
  "清除指定缓存键。"
  [cache-name cache-key on-success on-error]
  (t/request {:method :delete :uri (str "/system/cache/clearCacheKey/" cache-name "/" cache-key)
              :on-success on-success :on-error on-error}))

(defn force-logout
  "强制登出用户。"
  [token-id on-success on-error]
  (t/request {:method :delete
              :uri (str "/system/online/" (js/encodeURIComponent (str token-id)))
              :on-success on-success :on-error on-error}))
