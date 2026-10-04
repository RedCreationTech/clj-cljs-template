(ns com.ruoyi.integrant.trace
  "Integrant 函数组件的运行时调用追踪。"
  (:require
   [com.ruoyi.infra.clock :as clock]
   [com.ruoyi.infra.datasource :as ds]
   [com.ruoyi.infra.redact :as redact]
   [com.ruoyi.integrant.state :as state]
   [com.ruoyi.web.handler :as handler]
   [integrant.core :as ig]
   [kit.edge.db.sql.conman]))

(defonce ^:private registry (atom {}))
;; registry: {key-str {:original fn :active? boolean :logs [...]}}

(defonce ^:private dynamic-atoms (atom {}))
;; dynamic-atoms: {keyword <atom-of-actual-fn>}

(defn register-dynamic!
  "注册一个动态代理组件。返回的函数会实时去取 atom 里的实际实现。

   同一个键只用同一个 atom:系统 halt/init 之后(开发期 restart、swap-db!)老组件里持有的
   还是上一代代理函数,如果每次都换新 atom,老引用就永远停在已经关闭的那个连接池上
   (在线会话写不进去,请求全部 401)。复用 atom 并换成新实现,任何一代代理都指向最新实现。"
  [k f]
  (let [a (or (get @dynamic-atoms k) (atom f))]
    (swap! dynamic-atoms assoc k a)
    (reset! a f)
    (fn [& args]
      (apply @a args))))

(defn set-dynamic!
  "替换动态代理组件的实际实现。"
  [k f]
  (when-let [a (get @dynamic-atoms k)]
    (reset! a f)))

(defn current-dynamic
  "获取动态代理组件当前实际实现。"
  [k]
  (when-let [a (get @dynamic-atoms k)]
    @a))

;; 覆盖 kit-sql-conman 的 query-fn，使其成为一个可动态替换的代理。
;; 这样启动后所有持有 :db.sql/query-fn 的服务仍然指向同一个函数对象，
;; 但函数对象内部会读取 atom，从而支持运行时切换追踪包装。
;; ─── db.sql/connection: 包装为 DelegatingDataSource ────────────────
;; 这样运行时可以通过 swap-db! 热切换底层连接池，
;; 所有已持有引用的服务无需重新初始化。

;; 三个 original-* 必须用 defonce 抓:热重载(refresh / restart)会重新加载本 ns,
;; 用 def 第二次抓到的就是下面自己定义的 defmethod,于是每重载一次就多套一层
;; DelegatingDataSource 与 query-fn 代理 —— 连接池被套到最里层,监控页的
;; get-delegate 拿到的还是代理而不是 HikariDataSource,数据源监控再次显示 unknown,
;; 并且旧代理继续持有已经关闭的那个池。
(defonce ^:private original-conn-init
  (get-method ig/init-key :db.sql/connection))

(defmethod ig/init-key :db.sql/connection
  [k pool-spec]
  (let [real-ds (original-conn-init k pool-spec)]
    ;; 已经包过一层就不要再包:嵌套代理会让监控页解包解不到 Hikari(显示 unknown),
    ;; 也会让 swap-db! 只换掉最外面那层的底层池,里层继续用已经关闭的池。
    (if (ds/swappable? real-ds)
      real-ds
      (ds/delegating-datasource real-ds))))

;; 覆盖 halt-key! 以正确关闭 DelegatingDataSource（conman 只认 HikariDataSource）
(defonce ^:private original-conn-halt
  (get-method ig/halt-key! :db.sql/connection))

(defmethod ig/halt-key! :db.sql/connection
  [k conn]
  (if (ds/swappable? conn)
    (do (ds/close-pool! conn)
        (ds/deregister! conn)
        nil)
    (original-conn-halt k conn)))

(defmethod ig/halt-key! :db.sql/query-fn
  [_ _]
  ;; 追踪登记里的 :original 是挂在即将关闭的连接池上的查询函数;系统重启后 stop!
  ;; 会把它装回共享的动态 atom,于是所有请求都打到已经关闭的池上。停用时直接丢弃。
  (swap! registry dissoc "db.sql/query-fn" :db.sql/query-fn)
  nil)

;; ─── db.sql/query-fn: 动态代理 ─────────────────────────────────────

(defonce ^:private original-query-fn-init
  (get-method ig/init-key :db.sql/query-fn))

(defonce ^{:doc "query-fn 加载的 SQL 文件(swap-db! 换库后按同一份清单重新绑定)。"}
  query-filenames
  (atom nil))

(defmethod ig/init-key :db.sql/query-fn
  [k opts]
  (reset! query-filenames (or (:filenames opts) [(:filename opts)]))
  ;; 外层补 :now(infra.clock):追踪包装、换库都在代理内部发生,时间参数始终注入
  (clock/with-now (register-dynamic! :db.sql/query-fn (original-query-fn-init k opts))))

(def ^:private max-log-entries 200)

(defn- now []
  (System/currentTimeMillis))

(defn- make-wrapper [key-str original]
  (fn [& args]
    (let [rec (get @registry key-str)
          active? (:active? rec)
          options (:snapshot-options rec)
          t0 (now)]
      (try
        (let [ret (apply original args)]
          (when active?
            (swap! registry update-in [key-str :logs]
                   (fn [logs]
                     (->> (conj logs
                                {:time (now)
                                 :duration (- (now) t0)
                                 :args (redact/snapshot args options)
                                 :result (redact/snapshot ret options)})
                          (take-last max-log-entries)
                          vec))))
          ret)
        (catch Throwable e
          (when active?
            (swap! registry update-in [key-str :logs]
                   (fn [logs]
                     (->> (conj logs
                                {:time (now)
                                 :duration (- (now) t0)
                                 :args (redact/snapshot args options)
                                 :error (redact/snapshot e options)})
                          (take-last max-log-entries)
                          vec))))
          (throw e))))))

(defn- kw [key-str]
  (if (keyword? key-str) key-str (keyword key-str)))

(defn- current-actual [k]
  (case k
    :handler/ring (handler/current-ring-handler)
    :db.sql/query-fn (current-dynamic :db.sql/query-fn)
    (get @state/system k)))

(defn- set-actual! [k f]
  (case k
    :handler/ring (handler/set-ring-handler! f)
    :db.sql/query-fn (set-dynamic! :db.sql/query-fn f)
    (swap! state/system assoc k f)))

(defn start!
  "开启追踪；可用第二参数的 :sensitive-keys 扩展业务字段脱敏。"
  ([key-str] (start! key-str {}))
  ([key-str snapshot-options]
   (let [k (kw key-str)
         f (current-actual k)]
     (when (fn? f)
       (swap! registry
              (fn [reg]
                (let [entry (or (get reg key-str)
                                {:original f :wrapper (make-wrapper key-str f) :logs []})]
                  (assoc reg key-str (assoc entry :active? true
                                            :snapshot-options snapshot-options)))))
       (set-actual! k (get-in @registry [key-str :wrapper]))
       true))))

(defn stop!
  "停止追踪并清空日志，恢复原始函数。"
  [key-str]
  (let [k (kw key-str)
        rec (get @registry key-str)]
    (when rec
      (let [original (:original rec)]
        (set-actual! k original)
        (swap! registry dissoc key-str)))
    true))

(defn set-active! [key-str active?]
  (if active?
    (start! key-str)
    (stop! key-str)))

(defn logs [key-str]
  (get-in @registry [key-str :logs] []))

(defn active? [key-str]
  (boolean (get-in @registry [key-str :active?])))
