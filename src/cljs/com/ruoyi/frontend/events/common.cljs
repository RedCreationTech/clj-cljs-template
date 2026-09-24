(ns com.ruoyi.frontend.events.common
  "common"
  (:require
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.router :as router]
   [com.ruoyi.frontend.storage :as storage]))

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
   :profile {:label "个人中心" :icon "profile"}
   ;; [new-module] tab-meta
   })

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
  (when-let [stored (storage/get-json :layout-settings)]
    (merge db/default-layout-settings stored)))

(defn persist-layout-settings!
  "把布局设置持久化到 localStorage。"
  [settings]
  (storage/set-json! :layout-settings settings))

(defn apply-theme-style
  "根据布局面板的主题风格同步 antd 主题模式。"
  [db settings]
  (let [mode (if (= "dark" (:theme-style settings)) :dark :light)]
    (storage/set-item! :theme-mode mode)
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

(defn stop-all-loading
  "接口失败时把各模块的 :loading? 统一复位(列表请求失败后表格不会一直转圈)。
   约定:模块状态放在 app-db 顶层键下,形如 {:configs {:loading? true ..}}。"
  [db]
  (reduce-kv (fn [acc k v]
               (if (and (map? v) (true? (:loading? v)))
                 (assoc-in acc [k :loading?] false)
                 acc))
             db
             db))

(defn unread-count
  "铃铛角标:比上次查看时最新的一条更新的通知数(notice_id 自增)。"
  [items seen-id]
  (count (filter #(> (:notice_id %) (or seen-id 0)) items)))
