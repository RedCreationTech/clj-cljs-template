(ns com.ruoyi.dev
  "开发期 REPL 助手。

   所有助手只读 `com.ruoyi.integrant.state/system`,也就是 `core/start-app` 真正装配起来的
   那份状态:查库、打接口、重启用的都是运行中系统的组件,不会碰到另一份空的状态。"
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [clojure.tools.namespace.repl :as tn]
   [com.ruoyi.config :as config]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev.http :as http]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.security :as security]
   [com.ruoyi.web.controllers.monitor :as monitor]
   [integrant.core :as ig]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]))

(def nrepl-key
  "nREPL 组件的 Integrant 键。重启时必须把它留在原地:`:nrepl/server` 也是系统的一个组件,
   连带它一起 halt 会把自己所在的 REPL 连接关掉,表现为重启命令永远‘卡住’。"
  :nrepl/server)

(def api-prefix "/api")

(defn system
  "当前装配好的系统 map(未启动时为 nil)。"
  []
  @core/system)

(defn- active-profile
  []
  (or (get (system) :system/env) :dev))

(defn prepared-config
  "按 profile 重新读一遍 system.edn 并展开,去掉 `:nrepl/server`。
   每次重启都重新读文件,改了配置才生效;这是纯函数,便于单独测试。"
  [profile]
  (-> (config/system-config {:profile profile})
      (config/expand-config)
      (dissoc nrepl-key)))

(def keep-keys
  "重启过程中必须留在系统 map 里的键:REPL 本身和当前 profile。"
  [nrepl-key :system/env])

(defn- component-count
  "真正的组件数(不含上面那些元数据键)。"
  [sys]
  (count (apply dissoc sys keep-keys)))

(defn halt!
  "停掉除 nREPL 以外的全部组件(Undertow 端口、连接池、调度器都释放)。
   `:system/env` 要一起留着:`init!` 靠它知道该按哪个 profile 重新装配。"
  []
  (let [sys (system)]
    (some-> sys (dissoc nrepl-key) (ig/halt!))
    (reset! core/system (with-meta (select-keys sys keep-keys) (meta sys)))
    (component-count (system))))

(defn- merge-initialized-system [sys initialized]
  (let [retained (fn [k] (select-keys (get (meta sys) k) keep-keys))
        metadata (-> (meta initialized)
                     (update ::ig/origin into (retained ::ig/origin))
                     (update ::ig/build into (retained ::ig/build)))]
    ;; 新组件用新配置;保留的 nREPL 仍须在最终 halt 时被 Integrant 找到。
    (with-meta (merge sys initialized) metadata)))

(defn init!
  "重新读配置并 init(不含 nREPL),把组件并回系统;监控页展示的依赖图同步更新。"
  []
  (let [cfg (prepared-config (active-profile))]
    (config/remember-active-config! cfg)
    (let [initialized (ig/init cfg)]
      (swap! core/system merge-initialized-system initialized))
    (component-count (system))))

(def refresh-dirs
  "热重载扫描的目录。只扫 src/clj:env/dev/clj 里的助手改了要重启进程,
   免得 reload 把正在使用的助手函数换掉,而 user 里的短名字(rd/rr/q/req)还指着旧那份。"
  ["src/clj"])

(def reload-exclusions
  "不参与热重载的命名空间:它们的 defonce 装的是运行期身份,卸载重载就会换掉正在被引用的那份值。

   - `com.ruoyi.integrant.trace` 覆盖 Integrant 的 `defmethod`,`defonce` 记住 kit-edge 的上游实现;
     重载会先 unmap 符号,于是重新抓取时拿到的是本 ns 自己刚装上的方法,连接池每重载一次多套一层代理
     —— 数据源监控解包解不到 Hikari 只显示 unknown,在线会话继续用已关闭的池,接口全部 401。
   - `com.ruoyi.integrant.state` 持有系统 map 和稳定 Ring 入口;换身份会让旧服务器看不到新 handler。
   - `com.ruoyi.infra.datasource` 的注册表按 `System/identityHashCode` 索引代理,清空后老代理不再可换。

   改了这些文件要重启进程(bb dev / bb backend),其余源码照常热重载。"
  '[com.ruoyi.integrant.trace
    com.ruoyi.integrant.state
    com.ruoyi.infra.datasource])

(defn- protect-runtime-state!
  "告诉 tools.namespace 不要卸载/重载持有运行期状态的命名空间。"
  []
  (doseq [sym reload-exclusions
          :let [ns (find-ns sym)]
          :when ns]
    (tn/disable-reload! ns)))

(defn- drain-tracker!
  "把 scan 出来的待重载清单清空:刚起进程时全量扫一遍只是为了记录文件时间戳,
   不该让第一次 `reload` 把 src 下所有命名空间都重新加载一遍。"
  []
  (alter-var-root #'tn/refresh-tracker
                  dissoc :clojure.tools.namespace.track/load
                  :clojure.tools.namespace.track/unload))

(defn- prepare-refresh!
  []
  (apply tn/set-refresh-dirs refresh-dirs)
  (protect-runtime-state!))

(defn- pending-reloads
  "本次 scan 认定改动过、等待重载的命名空间(按依赖顺序)。"
  []
  ;; refresh-tracker 是 defonce 出来的 map,源码里直接引用它(再 @ 一次就是对 map 取 deref 了)
  (get tn/refresh-tracker :clojure.tools.namespace.track/load))

(defn- mutable-holders
  "命名空间里装着可变引用容器(atom / ref / agent / volatile!)的 var → 当前那个对象。

   组件 init 时抓住的就是这个对象本身,所以判断危险不能看 `defonce`:Clojure 的 `defonce`
   展开成 `(.hasRoot v)` 判断,不留任何元数据。也不算 delay/promise —— 那是一次性求值的
   派生数据,换掉不影响运行中的系统,按容器处理只会让 `rd` 白白重建一遍组件。"
  [syms]
  (into {} (mapcat (fn [sym]
                     (when-let [ns (find-ns sym)]
                       (keep (fn [[s v]]
                               (when-let [x (and (.hasRoot ^clojure.lang.Var v) (var-get v))]
                                 (when (or (instance? clojure.lang.IRef x)
                                           (instance? clojure.lang.Volatile x))
                                   {(symbol (name sym) (name s)) x})))
                             (ns-interns ns))))
                   syms)))

(defn- replaced-holders
  "重载后换了身份的可变容器:运行中的系统还抓着旧对象,必须重建组件。"
  [before syms]
  (let [after (mutable-holders syms)]
    (keep (fn [[k old]]
            (when-not (identical? old (get after k)) k))
          before)))

(defn- lifecycle-methods []
  (into {} (for [[kind methods] {:init (methods ig/init-key) :halt (methods ig/halt-key!)}
                 [k f] methods]
             [[kind k] f])))

(defn- refresh-source!
  "扫描并重载改动过的源码,返回 {:reloaded … :stale …}。
   编译不过时 `tn/refresh-scanned` 返回异常,这里原样抛出:调用方(halt+init)不能继续拆系统。"
  []
  (prepare-refresh!)
  (tn/scan)
  (let [reloaded (pending-reloads)
        before (mutable-holders reloaded)
        lifecycle-before (lifecycle-methods)
        result (tn/refresh-scanned)]
    (when (instance? Throwable result) (throw result))
    (let [after (lifecycle-methods)]
      {:reloaded reloaded
       :stale (replaced-holders before reloaded)
       :changed-components (distinct
                            (for [[kind k :as key] (distinct (concat (keys lifecycle-before) (keys after)))
                                  :when (not (identical? (get after [kind k]) (get lifecycle-before key)))]
                              k))})))

(defn- reset-components!
  "halt(除 nREPL)后重新 init,让组件重新抓到刚重载的那份运行期状态。"
  []
  (halt!)
  (init!))

(defonce ^:private pending-refresh (atom nil))

(defn init-refresh!
  "启动时调用:设定扫描目录、保护运行期状态,并把当前源码记为已加载。
   bb backend / bb dev 加载 user 时执行一次。"
  []
  (prepare-refresh!)
  (tn/scan {:add-all? true})
  (drain-tracker!)
  (reset! pending-refresh nil))

(defn- combine-refresh [old fresh]
  (merge-with #(vec (distinct (concat %1 %2))) (or old {}) fresh))

(defn- apply-refresh! [{:keys [stale changed-components]}]
  (let [unsafe (remove http/http-key? changed-components)]
    (if (or (seq stale) (seq unsafe))
      (do
        (log/warn "运行期状态或非 HTTP 组件已变化,请运行 (user/rr):" (vec stale) (vec unsafe))
        {:status :restart-required :stale (mapv str stale) :components (vec unsafe)})
      (try
        (http/rebuild! (system))
        (reset! pending-refresh nil)
        {:status :http-updated}
        (catch Exception e
          (log/warn e "HTTP 链未替换;停止 Ring 追踪后重试 rd,其它装配问题请运行 (user/rr)")
          {:status (if (:trace-active (ex-data e)) :trace-active :restart-required)})))))

(defn reload
  "刷新改动的源码,成功后只重建 HTTP 链,复用基础设施与领域组件。
   无改动不重建;状态容器/非 HTTP 生命周期变更要求 rr。失败或追踪阻挡不会替换旧入口,
   下次 rd 会重试未完成的 HTTP 装配。:reset 保留兼容但不再自动重启全部组件。"
  []
  (let [{:keys [reloaded] :as fresh} (refresh-source!)
        pending (combine-refresh @pending-refresh fresh)
        result (cond
                 (zero? (component-count (system))) {:status :stopped}
                 (empty? (:reloaded pending)) {:status :unchanged}
                 :else (do (reset! pending-refresh pending)
                           (apply-refresh! pending)))]
    (merge {:reloaded (count reloaded) :reset []} result)))

(defn restart!
  "开发期的一键重启:refresh 源码 → halt(除 nREPL)→ 重新 init。
   HugSQL `.sql`、`system.edn`、路由数据的改动都会在这里生效,不用重启 JVM。
   refresh 放在最前面:源码编译不过时系统还是原来那份,不会被拆在半路。"
  []
  (:reloaded (refresh-source!))
  (let [n (reset-components!)]
    (reset! pending-refresh nil)
    (log/info "system restarted with" n "components")
    n))

(defn- require-system
  [hint]
  (when (nil? (system))
    (throw (ex-info "系统还没起来:先 bb dev,或在 REPL 里 (user/go)" {:while hint}))))

(defn- component
  "取运行中系统的某个组件。halt 与 init 之间组件是空的,这时也要说人话。"
  [k hint]
  (require-system hint)
  (or (k (system))
      (throw (ex-info (str "组件 " (name k) " 不在系统里:正在重启就用 (user/rr),否则 (user/go)")
                      {:while hint :key k}))))

(defn q
  "在运行中的库上跑一条命名查询,例如 (q :find-user-by-name {:user_name \"admin\"})。
   每次都现取 :db.sql/query-fn,重启之后不会拿到旧组件。"
  ([name]
   (q name {}))
  ([name params]
   ((component :db.sql/query-fn 'q) name params)))

(defn state
  "看一眼运行中的系统:profile、组件数、连接池实时数字。
   监控页 /api/system/integrant 展示的是同一份数据,这里是为了在 REPL 里顺手查。"
  []
  (require-system 'state)
  (let [sys (system)]
    {:profile    (:system/env sys)
     :components (component-count sys)
     :keys       (sort (map str (keys sys)))
     :pool       (:data (:body (monitor/datasource-info {:datasource (:db.sql/connection sys)} nil)))}))

(defn- ->text
  [body]
  (cond
    (string? body) body
    (bytes? body) (String. ^bytes body)
    (instance? java.io.InputStream body) (slurp (io/reader body))
    :else body))

(defn- parse-response
  "把 ring 响应里的 body 解析成 clojure 数据,读起来和接口文档一致。"
  [resp]
  (let [text (->text (:body resp))]
    (if (string? text)
      (try (json/read-str text) (catch Exception _ text))
      text)))

(defn request
  "在进程内打一次真实请求:完整走认证、权限、异常、分页中间件,只是不占用网络端口。
   令牌是真签的,会话临时登记进 sys_online,用完删掉,所以 :perms 检查会真的生效。
   返回值是解析过的响应 body,看 :code 与 :data。

     (request :get \"/system/datasource\")
     (request :get \"/system/user\" :params {:page 1 :size 5})
     (request :post \"/system/post\" :body {:post-name \"店长\" :post-code \"dianzhang\"})
     (request :delete \"/system/post/3\" :as \"ry\")   ; 换成权限更小的用户验证 403"
  [method path & {:keys [body params headers as] :or {as "admin"}}]
  (require-system 'request)
  (let [user (q :find-user-by-name {:user_name as})
        _ (when (nil? user) (throw (ex-info (str "没有这个用户: " as) {:user as})))
        role-ids (mapv :role_id (q :list-roles-by-user-id {:user_id (:user_id user)}))
        jti (str (random-uuid))
        token (security/generate-token (:user_id user) as role-ids :jti jti)
        request (cond-> (-> (mock/request method (str api-prefix path))
                            (mock/header "authorization" (str "Bearer " token)))
                  (seq params) (assoc :query-string (codec/form-encode params))
                  (seq body) (mock/json-body body)
                  (seq headers) (update :headers merge headers))]
    (online/register! jti as "127.0.0.1")
    (try
      (-> ((:handler/ring (system)) request) (parse-response))
      (finally
        (online/unregister! jti)))))
