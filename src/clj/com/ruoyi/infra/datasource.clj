(ns com.ruoyi.infra.datasource
  "代理 DataSource — 支持运行时热切换底层连接池（SQLite ↔ MySQL）。"
  (:import
   [javax.sql DataSource]))

;; 全局注册表: {object-id -> atom-of-datasource}
;; 用于在 classloader reload 后仍能通过对象身份找到对应的 delegate
(defonce ^:private registry (java.util.concurrent.ConcurrentHashMap.))

(defn delegating-datasource
  "创建一个支持热切换的代理 DataSource，初始指向 real-ds。"
  ^DataSource [^DataSource real-ds]
  (let [delegate (atom real-ds)
        ds (reify
             DataSource
             (getConnection [_]
               (.getConnection ^DataSource @delegate))
             (getConnection [_ username password]
               (.getConnection ^DataSource @delegate ^String username ^String password))
             ;; javax.sql.DataSource 的其余方法一律转发:只实现 getConnection 的代理是一个
             ;; 残缺的 DataSource,任何拿它当池用的库(健康检查、conman 的登录超时设置、JMX)
             ;; 都会撞上 AbstractMethodError,而报错地方离真正的原因很远。
             (getLogWriter [_]
               (.getLogWriter ^DataSource @delegate))
             (setLogWriter [_ writer]
               (.setLogWriter ^DataSource @delegate writer))
             (setLoginTimeout [_ seconds]
               (.setLoginTimeout ^DataSource @delegate seconds))
             (getLoginTimeout [_]
               (.getLoginTimeout ^DataSource @delegate))
             (getParentLogger [_]
               (.getParentLogger ^DataSource @delegate))

             java.sql.Wrapper
             (isWrapperFor [_ iface]
               (.isWrapperFor ^java.sql.Wrapper @delegate iface))
             (unwrap [_ iface]
               (.unwrap ^java.sql.Wrapper @delegate iface))

             Object
             (toString [_]
               (str "DelegatingDataSource -> " @delegate)))]
    ;; 注册到全局注册表
    (.put registry (System/identityHashCode ds) delegate)
    ds))

(defn swappable?
  "检查 ds 是否为可热切换的代理 DataSource。通过类名检测。"
  [ds]
  (boolean
   (when ds
     (.containsKey registry (System/identityHashCode ds)))))

(defn- get-delegate-atom
  "获取代理 DataSource 内部的 delegate atom。"
  [ds]
  (let [aid (System/identityHashCode ds)]
    (when-let [a (.get registry aid)]
      a)))

(defn swap-delegate!
  "替换代理 DataSource 的底层连接池。返回旧的 DataSource。"
  [dds new-ds]
  (when-let [a (get-delegate-atom dds)]
    (let [old @a]
      (reset! a new-ds)
      old)))

(defn get-delegate
  "获取代理 DataSource 当前的底层 DataSource。"
  [dds]
  (when-let [a (get-delegate-atom dds)]
    @a))

(defn unwrap
  "一层层解开代理,返回最里面真正的 DataSource(不是代理时原样返回)。
   历史遗留的嵌套代理只解一层会拿到代理本身,监控页因此显示 unknown。"
  [ds]
  (loop [x ds]
    (if-let [inner (and (swappable? x) (get-delegate x))]
      (recur inner)
      x)))

(defn close-pool!
  "关闭真正的连接池(只认 HikariDataSource,已关闭的不再处理)。"
  [ds]
  (let [^Object target (unwrap ds)]
    (when (instance? com.zaxxer.hikari.HikariDataSource target)
      (let [pool ^com.zaxxer.hikari.HikariDataSource target]
        (when-not (.isClosed pool) (.close pool))))))

(defn deregister!
  "代理 DataSource 停用后摘掉登记:注册表按 identityHashCode 索引,对象回收后 JVM 会把这个
   哈希值发给新对象,残留的登记会让一个普通连接池被误认成代理。"
  [ds]
  (.remove registry (System/identityHashCode ds))
  nil)
