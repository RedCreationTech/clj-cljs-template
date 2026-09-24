(ns com.ruoyi.infra.security
  "安全工具模块，提供密码哈希与 JWT 令牌签发/验证功能。"
  (:require
   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as str]
   [com.ruoyi.infra.secrets :as secrets]))

(def secret-key
  "JWT 签名密钥,从环境变量 JWT_SECRET 注入;开发/测试时退回默认值,prod 下由 secrets/verify! 拒绝默认值。"
  (or (not-empty (System/getenv "JWT_SECRET"))
      secrets/default-jwt-secret))

(def ^:dynamic *hash-options*
  "密码哈希参数(buddy-hashers)。生产使用默认强度;测试时 deps.edn 的 :test 别名把迭代次数降到最低,
   否则每次哈希约 0.25 秒,会拖慢整套测试。"
  {:alg :bcrypt+sha512})

(defn hash-password
  "使用 bcrypt 对明文密码进行哈希。"
  [plain-text]
  (hashers/derive plain-text *hash-options*))

(defn verify-password
  "验证明文密码与哈希值是否匹配。"
  [plain-text hashed]
  (hashers/check plain-text hashed))

(def default-token-ttl-minutes
  "访问令牌默认有效期(分钟);实际值由 system.edn 的 :auth-config :token-ttl-minutes 决定。"
  720)

(defn- now-seconds [] (quot (System/currentTimeMillis) 1000))

(defn generate-token
  "为用户生成 JWT 访问令牌,claims 含用户 ID、用户名、角色 ID 列表、会话 ID :jti、签发时间 :iat 与过期时间 :exp。
   :iat / :exp 按 JWT 标准使用 Unix 秒,buddy 在 unsign 时据此拒绝过期令牌。"
  [user-id user-name roles & {:keys [ttl-minutes jti]
                              :or {ttl-minutes default-token-ttl-minutes}}]
  (let [now (now-seconds)
        claims {:user-id user-id
                :user-name user-name
                :roles roles
                :jti (or jti (str (random-uuid)))
                :iat now
                :exp (+ now (* 60 ttl-minutes))}]
    (jwt/sign claims secret-key {:alg :hs256})))

(defn parse-token
  "解析并验证 JWT 令牌，成功返回 claims，失败返回 nil。"
  [token]
  (try
    (jwt/unsign token secret-key {:alg :hs256})
    (catch Exception _
      nil)))

(defn extract-token
  "从 Authorization Header 中提取 Bearer Token。"
  [request]
  (some-> (get-in request [:headers "authorization"])
          (str/replace-first #"(?i)^Bearer\s+" "")))
