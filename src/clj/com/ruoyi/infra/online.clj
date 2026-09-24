(ns com.ruoyi.infra.online
  "在线会话管理。

  每个令牌的 claims 里有唯一的 :jti,作为 sys_online 表的 session_id。会话是否有效以这张表为准:
  - 登录:register! 插入会话;
  - 每次请求:heartbeat! 更新 last_access_time,更新 0 行说明会话已被删除 → 视为未登录;
  - 空闲超时:后台每 5 分钟删除 idle-timeout-ms 内没有请求的会话;
  - 登出 / 强退:删除会话,令牌立即失效(存在数据库里,多实例部署同样生效);
  - 续期:rotate! 把会话改挂到新令牌的 jti 上,旧令牌在 rotate-grace-ms 宽限期内仍可用,
    避免与续期并发的请求被误判为未登录(宽限记录在进程内存里)。"
  (:require
   [clojure.tools.logging :as log])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

;; ──────────── 全局状态 ────────────

(defonce ^:private query-fn-atom (atom nil))

(defonce ^:private rotated
  ;; 旧 session-id -> 宽限截止时间(毫秒)
  (atom {}))

(def idle-timeout-ms
  "会话空闲超时:超过这么久没有任何请求,会话被清理,令牌随之失效。"
  (* 30 60 1000))

(def rotate-grace-ms
  "续期后旧令牌继续可用的宽限期。"
  (* 30 1000))

(defn set-query-fn!
  "由 online-service 在系统启动时注入 query-fn。"
  [query-fn]
  (reset! query-fn-atom query-fn))

(defn- query-fn []
  (if-let [q @query-fn-atom]
    q
    (throw (IllegalStateException. "online query-fn not initialized"))))

(defn- now [] (System/currentTimeMillis))

;; ──────────── 续期宽限 ────────────

(defn in-grace?
  "session-id 是否是刚续期的旧会话且仍在宽限期内。"
  [session-id]
  (when-let [until (get @rotated session-id)]
    (< (now) until)))

(defn- cleanup-rotated! []
  (let [t (now)]
    (swap! rotated #(into {} (remove (fn [[_ until]] (< until t))) %))))

;; ──────────── 清理调度 ────────────

(declare cleanup-expired-sessions!)

(defonce cleanup-executor
  (delay
    (doto (ScheduledThreadPoolExecutor. 1)
      (.scheduleAtFixedRate
       (reify Runnable
         (run [_]
           (try
             (cleanup-expired-sessions!)
             (cleanup-rotated!)
             (catch Exception e
               (log/warn e "Online user cleanup failed")))))
       5 5 TimeUnit/MINUTES))))

;; ──────────── 核心 API ────────────

(defn register!
  "登录成功后登记会话。"
  [session-id user-name login-ip]
  (let [t (now)]
    (try
      ((query-fn) :create-online-user!
                  {:session_id session-id
                   :login_name user-name
                   :dept_name ""
                   :ipaddr (or login-ip "127.0.0.1")
                   :login_location ""
                   :browser ""
                   :os ""
                   :status "on_line"
                   :start_timestamp t
                   :last_access_time t
                   :expire_time idle-timeout-ms})
      (catch Exception e
        (log/warn e "Failed to register online user")))
    (force cleanup-executor)
    nil))

(defn heartbeat!
  "更新会话最后访问时间。返回 false 表示会话已不存在(空闲超时被清理、已登出或被强退);
   数据库异常时返回 true,避免一次数据库抖动把所有人踢下线。"
  [session-id]
  (if-not session-id
    false
    (try
      (not= 0 ((query-fn) :update-online-user!
                          {:session_id session-id
                           :last_access_time (now)
                           :status nil
                           :expire_time nil}))
      (catch Exception e
        (log/warn e "Failed to update online user heartbeat")
        true))))

(defn active?
  "请求鉴权用:宽限期内的旧会话直接放行,否则以心跳是否命中会话为准。"
  [session-id]
  (boolean (or (in-grace? session-id) (heartbeat! session-id))))

(defn unregister!
  "删除会话(登出、强退),对应令牌立即失效。"
  [session-id]
  (when session-id
    (try
      ((query-fn) :delete-online-user! {:session_id session-id})
      (catch Exception e
        (log/warn e "Failed to unregister online user"))))
  nil)

(defn rotate!
  "令牌续期:把会话改挂到新 session-id 上;旧 session-id 在 rotate-grace-ms 内仍然有效。
   返回 false 表示旧会话已不存在(不应签发新令牌)。"
  [old-session-id new-session-id]
  (let [n (try
            ((query-fn) :rename-online-session! {:old_session_id old-session-id
                                                 :session_id new-session-id
                                                 :last_access_time (now)})
            (catch Exception e
              (log/warn e "Failed to rotate online session")
              0))]
    (when (pos? n)
      (swap! rotated assoc old-session-id (+ (now) rotate-grace-ms)))
    (pos? n)))

(defn cleanup-expired-sessions!
  "清理超过 idle-timeout-ms 未心跳的会话。"
  []
  (let [threshold (- (now) idle-timeout-ms)]
    (try
      ((query-fn) :delete-idle-online-users! {:last_access_time threshold})
      (catch Exception e
        (log/warn e "Failed to delete idle sessions"))))
  nil)

(defn list-online
  "获取在线用户列表，支持条件筛选。"
  [& {:keys [login-name ipaddr page-num page-size]
      :or   {page-num 1 page-size 10}}]
  (let [offset (* (dec page-num) page-size)
        rows ((query-fn) :list-online-users
                         {:ipaddr ipaddr
                          :login_name login-name
                          :page_size page-size
                          :offset offset})
        total (:total ((query-fn) :count-online-users
                                  {:ipaddr ipaddr
                                   :login_name login-name}))]
    {:rows (mapv #(-> %
                      (assoc :token-id (:session_id %))
                      (assoc :token (:session_id %))
                      (assoc :user-id (:login_name %))
                      (assoc :user-name (:login_name %))
                      (assoc :login-ip (:ipaddr %))
                      (assoc :login-time (:start_timestamp %))
                      (assoc :last-access (:last_access_time %)))
                 rows)
     :total total}))

(defn force-logout!
  "强退指定会话(参数是在线列表里的 session-id)。"
  [session-id]
  (unregister! session-id)
  {:success true})
