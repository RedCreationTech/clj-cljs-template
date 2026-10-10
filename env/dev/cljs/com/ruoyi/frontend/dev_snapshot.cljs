(ns com.ruoyi.frontend.dev-snapshot
  "仅由 shadow dev preload 加载;固定白名单,不收集用户数据或请求历史。"
  (:require
   [re-frame.db :as db]))

(defonce ^:private runtime-id (str (random-uuid)))

(def ^:private pages
  #{:login :dashboard :user :role :menu :dept :post :file :dict :config :notice
    :oper-log :login-log :online :job :server :cache :datasource :integrant
    :swagger :build :profile})

(def ^:private modules
  [:users :roles :menus :depts :posts :dicts :configs :notices :oper-logs
   :login-logs :online-users :jobs :job-logs :profile :dashboard :server
   :cache :file :fb :form-templates])

(defn- flag [value]
  (if (boolean? value) value :unknown))

(defn- item-count [items]
  ;; 不遍历列表元素或惰性序列;数值是封顶计数。
  (if (vector? items) (min 1000000 (count items)) :unknown))

(defn- module-state [value]
  {:loading? (flag (:loading? value))
   :saving? (flag (:saving? value))
   :modal-visible? (flag (:modal-visible? value))
   :item-count (item-count (:items value))})

(defn summarize
  "纯投影;未知字段、页面、值均不会透传。token-present? 不是登录有效性验证。"
  [app-db]
  (if (and (map? app-db) (seq app-db))
    {:status :available
     :state {:page (get pages (:page app-db) :unknown)
             :token-present? (some? (get-in app-db [:auth :token]))
             :modules (into {} (map (fn [k] [k (module-state (get app-db k))]) modules))}}
    {:status :unavailable :state nil}))

(defn snapshot
  "读取当前选中的浏览器 runtime;不选择标签页、不触发事件或网络请求。"
  []
  (merge {:schema-version 1
          :captured-at (.toISOString (js/Date.))
          :source :frontend
          :runtime-id runtime-id}
         (if goog.DEBUG
           (summarize @db/app-db)
           {:status :forbidden :state nil})))

(defn ^:export snapshot_edn
  "浏览器控制台入口,只返回安全摘要的 EDN 字符串。"
  []
  (pr-str (snapshot)))
