(ns com.ruoyi.web.routes.monitor
  "系统监控路由:日志、在线用户、定时任务、服务/数据源/Integrant/缓存监控。
   与 routes.system 一样挂在 /system 下(前端接口路径不变),整组要求登录,各接口用 :perms 声明按钮权限。"
  (:require
   [com.ruoyi.web.controllers.job :as job]
   [com.ruoyi.web.controllers.monitor :as monitor]
   [com.ruoyi.web.controllers.system.cache :as cache]
   [com.ruoyi.web.controllers.system.log :as log]
   [com.ruoyi.web.controllers.system.online :as online]))

(def ^:private PagingQuery
  [:map {:closed true}
   [:page {:optional true} :int] [:size {:optional true} :int]
   [:order_by {:optional true} :string] [:is_asc {:optional true} :string]])

(def ^:private PathId [:map [:id [:re #"\d+"]]])

(defn- oper-log-routes
  "操作日志路由。"
  [{:keys [log-service]}]
  [["/oper-log"
    ["" {:get    {:perms "monitor:operlog:list" :summary "操作日志列表" :description "分页查询操作日志（只读）"
                  :handler (partial log/list-oper-logs {:log-service log-service})}
         :delete {:perms "monitor:operlog:remove" :summary "清空操作日志" :description "清空所有操作日志（需要确认）"
                  :handler (partial log/clear-oper-logs {:log-service log-service})}}]
    ["/:ids" {:delete {:perms "monitor:operlog:remove" :summary "删除操作日志" :description "删除指定操作日志"
                       :handler (partial log/delete-oper-logs {:log-service log-service})}}]]])

(defn- login-log-routes
  "登录日志路由。"
  [{:keys [log-service]}]
  [["/login-log"
    ["" {:get    {:perms "monitor:logininfor:list" :summary "登录日志列表" :description "分页查询登录日志（只读）"
                  :handler (partial log/list-login-logs {:log-service log-service})}
         :delete {:perms "monitor:logininfor:remove" :summary "清空登录日志" :description "清空所有登录日志"
                  :handler (partial log/clear-login-logs {:log-service log-service})}}]
    ["/unlock/:userName" {:put {:perms "monitor:logininfor:unlock" :summary "解锁用户"
                                :description "清除该用户名的登录失败记录与锁定"
                                :parameters {:path [:map [:userName :string]]}
                                :handler (partial log/unlock-user {})}}]
    ["/:ids" {:delete {:perms "monitor:logininfor:remove" :summary "删除登录日志" :description "删除指定登录日志"
                       :handler (partial log/delete-login-logs {:log-service log-service})}}]]])

(defn- online-routes
  "在线用户路由。"
  [{:keys [online-service]}]
  [["/online"
    ["" {:get {:perms "monitor:online:list" :summary "在线用户列表" :description "查询当前在线用户列表（只读）"
               :handler (partial online/list-online {:online-service online-service})}}]
    ["/:token-id" {:delete {:perms "monitor:online:forceLogout" :summary "强退用户" :description "强制踢出在线用户"
                            :parameters {:path [:map [:token-id :string]]}
                            :handler (partial online/force-logout {:online-service online-service})}}]]])

(defn- job-routes
  "定时任务与执行日志路由。"
  [{:keys [user-service datasource]}]
  (let [ctx {:query-fn (:query-fn user-service) :db datasource}]
    [["/job"
      ["" {:get  {:perms "monitor:job:list" :summary "定时任务列表" :parameters {:query PagingQuery}
                  :handler (partial job/list-jobs ctx)}
           :post {:perms "monitor:job:add" :summary "新增定时任务" :handler (partial job/create-job ctx)}}]
      ["/:id" {:get    {:perms "monitor:job:query" :summary "任务详情" :parameters {:path PathId}
                        :handler (partial job/get-job ctx)}
               :put    {:perms "monitor:job:edit" :summary "更新任务" :parameters {:path PathId}
                        :handler (partial job/update-job ctx)}
               :delete {:perms "monitor:job:remove" :summary "删除任务" :parameters {:path PathId}
                        :handler (partial job/delete-job ctx)}}]
      ["/:id/changeStatus" {:put {:perms "monitor:job:changeStatus" :summary "修改任务状态"
                                  :handler (partial job/change-status ctx)}}]
      ["/:id/run" {:put {:perms "monitor:job:changeStatus" :summary "执行一次" :handler (partial job/run-once {})}}]]
     ["/job-log"
      ["" {:get    {:perms "monitor:job:list" :summary "任务执行日志" :description "查询定时任务执行日志列表"
                    :handler (partial job/list-job-logs ctx)}
           :delete {:perms "monitor:job:remove" :summary "清空日志" :description "清空所有任务执行日志"
                    :handler (partial job/clean-logs ctx)}}]]]))

(defn- status-routes
  "仪表盘(登录即可)、服务器、数据源、Integrant。"
  [{:keys [query-fn datasource]}]
  [["/dashboard/stats" {:get {:summary "首页仪表盘统计" :description "聚合用户数、在线数、日志数、任务数、最近操作和系统信息"
                              :handler (partial monitor/dashboard-stats {:query-fn query-fn})}}]
   ["/server" {:get {:perms "monitor:server:list" :summary "服务器监控" :description "JVM/CPU/内存等系统信息"
                     :handler (partial monitor/server-info {})}}]
   ["/datasource" {:get {:perms "monitor:datasource:list" :summary "数据源监控" :description "数据库连接池状态"
                         :handler (partial monitor/datasource-info {:datasource datasource})}}]
   ["/integrant" {:get {:perms "monitor:integrant:list" :summary "Integrant 依赖"
                        :description "Integrant 配置、依赖图与运行时系统摘要"
                        :handler (partial monitor/integrant-info {})}}]
   ["/integrant/trace/*key"
    {:get {:perms "monitor:integrant:list" :summary "Integrant 函数追踪日志"
           :handler (partial monitor/integrant-trace-logs {})}
     :post {:perms "monitor:integrant:trace" :summary "开启/关闭 Integrant 函数追踪"
            :handler (partial monitor/integrant-trace {})}}]])

(defn- cache-routes
  "缓存监控路由:查看需要 list,清除需要 remove。"
  [_]
  (let [view "monitor:cache:list"
        clear "monitor:cache:remove"]
    [["/cache"
      ["" {:get {:perms view :summary "缓存信息" :description "获取缓存名称、类型、键数量等"
                 :handler (partial cache/cache-info {})}}]
      ["/keys" {:get {:perms view :summary "缓存键列表" :handler (partial cache/cache-keys {})}}]
      ["/getNames" {:get {:perms view :summary "缓存名称" :handler (partial cache/cache-names {})}}]
      ["/getKeys/:cacheName" {:get {:perms view :summary "缓存键" :handler (partial cache/cache-keys-by-name {})}}]
      ["/getValue/:cacheName/:cacheKey" {:get {:perms view :summary "缓存值" :handler (partial cache/cache-value {})}}]
      ["/clear" {:delete {:perms clear :summary "清空缓存" :handler (partial cache/clear-cache {})}}]
      ["/clearCacheName/:cacheName" {:delete {:perms clear :summary "清除指定缓存"
                                              :handler (partial cache/clear-cache-name {})}}]
      ["/clearCacheKey/:cacheName/:cacheKey" {:delete {:perms clear :summary "清除指定键"
                                                       :handler (partial cache/clear-cache-key {})}}]
      ["/clearCacheAll" {:delete {:perms clear :summary "清除所有缓存" :handler (partial cache/clear-cache-all {})}}]]]))

(defn monitor-routes
  "监控路由聚合(挂在 /system 下,整组要求登录)。"
  [opts]
  (into ["/system" {:auth? true :swagger {:tags ["系统监控"]}}]
        (mapcat #(% opts) [oper-log-routes login-log-routes online-routes job-routes status-routes cache-routes])))
