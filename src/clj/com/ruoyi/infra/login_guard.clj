(ns com.ruoyi.infra.login-guard
  "登录失败限流:同一用户名在 lock-minutes 窗口内失败 max-failures 次后锁定 lock-minutes 分钟,
   登录成功清零。按用户名(忽略大小写与首尾空格)计数,用户名不存在时同样计数,不泄露账号是否存在。
   状态在进程内存里:单实例足够;多实例部署需要换成共享存储(见 C4 §13 #5)。"
  (:require
   [clojure.string :as str]))

(defonce ^:private state
  ;; 用户名 -> {:failures [失败时间...] :locked-until 解锁时间}(毫秒)
  (atom {}))

(def defaults {:max-failures 5 :lock-minutes 10})

(defn- user-key [username]
  (-> (str username) str/trim str/lower-case))

(defn locked-until
  "被锁定时返回解锁时间(毫秒),否则 nil。"
  [username now]
  (let [until (get-in @state [(user-key username) :locked-until])]
    (when (and until (< now until)) until)))

(defn- prune
  "去掉窗口外的失败记录和已过期的锁,避免随机用户名把内存撑大。"
  [m now window-ms]
  (into {}
        (keep (fn [[k {:keys [failures locked-until]}]]
                (let [recent (filterv #(> % (- now window-ms)) failures)
                      locked (when (and locked-until (< now locked-until)) locked-until)]
                  (when (or (seq recent) locked)
                    [k (cond-> {:failures recent} locked (assoc :locked-until locked))]))))
        m))

(defn record-failure!
  "记一次失败,达到阈值时加锁。返回剩余可尝试次数(0 表示已锁定)。"
  [config username now]
  (let [{:keys [max-failures lock-minutes]} (merge defaults config)
        window-ms (* lock-minutes 60 1000)
        k (user-key username)
        after (swap! state
                     (fn [m]
                       (let [m (prune m now window-ms)
                             failures (conj (get-in m [k :failures] []) now)]
                         (assoc m k (if (>= (count failures) max-failures)
                                      {:failures [] :locked-until (+ now window-ms)}
                                      {:failures failures})))))
        {:keys [failures locked-until]} (get after k)]
    (if locked-until 0 (- max-failures (count failures)))))

(defn record-success!
  "登录成功:清除该用户名的失败记录。"
  [username]
  (swap! state dissoc (user-key username))
  nil)

(defn reset-all!
  "清空全部记录(测试用)。"
  []
  (reset! state {}))
