(ns com.ruoyi.infra.login-guard
  "登录失败限流:同一用户名连续失败 max-failures 次(两次失败间隔不超过 lock-minutes)后锁定 lock-minutes 分钟,
   登录成功或管理员解锁时清零。按用户名(忽略大小写与首尾空格)计数,用户名不存在时同样计数,不泄露账号是否存在。
   状态存在 infra.kv(系统运行时即数据库表 sys_kv),多实例部署共享同一份计数与锁。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.infra.kv :as kv]))

(def defaults {:max-failures 5 :lock-minutes 10})

(defn- user-key [username]
  (-> (str username) str/trim str/lower-case))

(defn- fail-key [username] (str "login-fail:" (user-key username)))
(defn- lock-key [username] (str "login-lock:" (user-key username)))

(defn locked-until
  "被锁定时返回解锁时间(毫秒),否则 nil。"
  [username now]
  (when-let [until (some-> (kv/get-val (lock-key username)) parse-long)]
    (when (< now until) until)))

(defn record-failure!
  "记一次失败,达到阈值时加锁。返回剩余可尝试次数(0 表示已锁定)。"
  [config username now]
  (let [{:keys [max-failures lock-minutes]} (merge defaults config)
        window-ms (* lock-minutes 60 1000)
        n (inc (or (some-> (kv/get-val (fail-key username)) parse-long) 0))]
    (if (>= n max-failures)
      (do (kv/put! (lock-key username) (+ now window-ms) window-ms)
          (kv/del! (fail-key username))
          0)
      (do (kv/put! (fail-key username) n window-ms)
          (- max-failures n)))))

(defn record-success!
  "登录成功:清除该用户名的失败记录与锁定。"
  [username]
  (kv/del! (fail-key username))
  (kv/del! (lock-key username))
  nil)

(defn unlock!
  "管理员手动解锁(登录日志页的「解锁」按钮):清除该用户名的失败记录与锁定。"
  [username]
  (record-success! username))

(defn reset-all!
  "清空全部记录(测试用)。"
  []
  (kv/clear-prefix! "login-")
  nil)
