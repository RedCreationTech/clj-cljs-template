(ns com.ruoyi.infra.kv
  "带过期时间的短期键值,给必须在多个应用实例之间共享的临时状态用:
   验证码(captcha:<uuid>)、登录失败计数与锁定(login-fail:<用户> / login-lock:<用户>)、
   续期宽限(grace:<旧 jti>)。

   两种实现:
   - 数据库(表 sys_kv,两库通用):系统启动时由 :app.system/online-service 切换过来,多实例部署共享;
   - 进程内存:没启动系统时(单元测试、REPL)的默认实现,系统停止后也会切回。
   值一律存字符串;过期的键读不到,后台清理线程定期删除(见 infra.online)。"
  (:require
   [clojure.string :as str]))

(defprotocol Store
  (-get [s k now] "未过期的值,没有则 nil")
  (-put! [s k v expire-at] "写入或覆盖")
  (-del! [s k] "删除")
  (-purge! [s now] "删除已过期的键")
  (-clear-prefix! [s prefix] "删除某个前缀下的全部键(测试与管理用)"))

(defrecord MemoryStore [a]
  Store
  (-get [_ k now]
    (let [{:keys [v expire-at]} (get @a k)]
      (when (and v (< now expire-at)) v)))
  (-put! [_ k v expire-at] (swap! a assoc k {:v v :expire-at expire-at}) nil)
  (-del! [_ k] (swap! a dissoc k) nil)
  (-purge! [_ now] (swap! a #(into {} (filter (fn [[_ e]] (< now (:expire-at e)))) %)) nil)
  (-clear-prefix! [_ prefix] (swap! a #(into {} (remove (fn [[k _]] (str/starts-with? k prefix))) %)) nil))

(defrecord JdbcStore [query-fn]
  Store
  (-get [_ k now] (:v (query-fn :kv-get {:k k :now_ms now})))
  (-put! [_ k v expire-at]
    ;; 先更新,没有再插入;两个实例同时插入同一个键时第二个会撞主键,再更新一次即可
    (let [params {:k k :v v :expire_at expire-at}]
      (when (zero? (query-fn :kv-update! params))
        (try (query-fn :kv-insert! params)
             (catch Exception _ (query-fn :kv-update! params)))))
    nil)
  (-del! [_ k] (query-fn :kv-delete! {:k k}) nil)
  (-purge! [_ now] (query-fn :kv-purge! {:now_ms now}) nil)
  (-clear-prefix! [_ prefix] (query-fn :kv-delete-prefix! {:pattern (str prefix "%")}) nil))

(defn memory-store [] (->MemoryStore (atom {})))

(defn jdbc-store [query-fn] (->JdbcStore query-fn))

(defonce ^:private store (atom (memory-store)))

(defn use-store!
  "切换实现(系统启动 / 停止时调用)。"
  [s]
  (reset! store s))

(defn- now-ms [] (System/currentTimeMillis))

(defn get-val
  "未过期的值(字符串),没有则 nil。"
  [k]
  (-get @store k (now-ms)))

(defn put!
  "写入 k,ttl-ms 毫秒后过期。"
  [k v ttl-ms]
  (-put! @store k (str v) (+ (now-ms) ttl-ms)))

(defn del! [k] (-del! @store k))

(defn take!
  "读出并删除(一次性的值,如验证码)。"
  [k]
  (let [v (get-val k)]
    (del! k)
    v))

(defn purge!
  "删除已过期的键。"
  []
  (-purge! @store (now-ms)))

(defn clear-prefix! [prefix] (-clear-prefix! @store prefix))
