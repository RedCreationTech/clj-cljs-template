(ns com.ruoyi.frontend.events.common
  "common"
  (:require
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.router :as router]))

(def page-tab-meta
  {:dashboard {:label "首页" :icon "dashboard" :closable false}
   :user {:label "用户管理" :icon "user"}
   :role {:label "角色管理" :icon "peoples"}
   :menu {:label "菜单管理" :icon "tree-table"}
   :dept {:label "部门管理" :icon "tree"}
   :post {:label "岗位管理" :icon "post"}
   :dict {:label "字典管理" :icon "dict"}
   :config {:label "参数设置" :icon "edit"}
   :notice {:label "通知公告" :icon "message"}
   :oper-log {:label "操作日志" :icon "form"}
   :login-log {:label "登录日志" :icon "logininfor"}
   :online {:label "在线用户" :icon "online"}
   :job {:label "定时任务" :icon "job"}
   :server {:label "服务监控" :icon "server"}
   :cache {:label "缓存监控" :icon "cache"}
   :datasource {:label "连接池监视" :icon "database"}
   :build {:label "表单构建" :icon "build"}
   :gen {:label "代码生成" :icon "code"}
   :swagger {:label "系统接口" :icon "swagger"}
   :profile {:label "个人中心" :icon "profile"}})

(defn activate-page-tab [db page]
  (let [meta (merge {:label (get router/page-names page "页面")}
                    (get page-tab-meta page {}))
        tabs (get-in db [:tabs :items] [])
        exists? (some #(= (:key %) page) tabs)
        tab (merge {:key page :closable (not= page :dashboard)} meta)]
    (-> db
        (assoc :page page)
        (assoc-in [:tabs :active] page)
        (cond-> (not exists?)
          (update-in [:tabs :items] conj tab)))))

(defn stored-layout-settings
  "从 localStorage 读取布局设置。"
  []
  (try
    (when-let [raw (js/localStorage.getItem "rouyi-layout-settings")]
      (merge db/default-layout-settings
             (js->clj (.parse js/JSON raw) :keywordize-keys true)))
    (catch js/Error _ nil)))

(defn persist-layout-settings!
  "把布局设置持久化到 localStorage。"
  [settings]
  (try
    (js/localStorage.setItem "rouyi-layout-settings"
                             (.stringify js/JSON (clj->js settings)))
    (catch js/Error _)))

(defn apply-theme-style
  "根据布局面板的主题风格同步 antd 主题模式。"
  [db settings]
  (let [mode (if (= "dark" (:theme-style settings)) :dark :light)]
    (js/localStorage.setItem "rouyi-theme-mode" (name mode))
    (assoc-in db [:theme :mode] mode)))

(defn build-dept-tree
  [items parent-id]
  (->> items
       (filter #(= parent-id (:parent_id %)))
       (mapv (fn [d]
               (let [children (build-dept-tree items (:dept_id d))]
                 (if (seq children)
                   (assoc d :children children)
                   d))))))
