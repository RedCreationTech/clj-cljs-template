(ns com.ruoyi.infra.secrets
  "生产环境密钥守卫:prod profile 下 JWT_SECRET / COOKIE_SECRET 缺失、使用内置默认值或长度不合格时拒绝启动。
   开发/测试环境允许使用默认值,免配置即可运行。"
  (:require
   [clojure.string :as str]))

(def default-jwt-secret
  "仅供开发/测试使用的 JWT 签名密钥;prod 下出现即视为未配置。"
  "ruoyi-default-jwt-secret-key-change-in-production")

(def default-cookie-secret
  "仅供开发/测试使用的会话 cookie 加密密钥(ring cookie-store 要求恰好 16 字节)。"
  "KWGRWFTDVZAHISQO")

(def min-jwt-secret-length 32)

(defn- byte-length [^String s]
  (count (.getBytes s "UTF-8")))

(defn problems
  "检查环境变量 map(如 (System/getenv)),返回问题描述列表;没有问题返回空序列。"
  [env]
  (let [jwt (get env "JWT_SECRET")
        cookie (get env "COOKIE_SECRET")]
    (cond-> []
      (or (nil? jwt) (= jwt ""))
      (conj "未设置 JWT_SECRET")

      (= jwt default-jwt-secret)
      (conj "JWT_SECRET 仍是模板内置的默认值")

      (and (seq jwt) (< (count jwt) min-jwt-secret-length))
      (conj (str "JWT_SECRET 太短(" (count jwt) " 个字符),至少需要 " min-jwt-secret-length " 个"))

      (or (nil? cookie) (= cookie ""))
      (conj "未设置 COOKIE_SECRET")

      (= cookie default-cookie-secret)
      (conj "COOKIE_SECRET 仍是模板内置的默认值")

      (and (seq cookie) (not= 16 (byte-length cookie)))
      (conj (str "COOKIE_SECRET 必须恰好 16 字节(当前 " (byte-length cookie) " 字节)")))))

(defn verify!
  "profile 为 :prod 且存在问题时抛出异常,异常信息里给出生成密钥的命令。"
  [profile env]
  (when (= :prod profile)
    (let [ps (problems env)]
      (when (seq ps)
        (throw (ex-info (str "生产环境密钥配置不安全,拒绝启动:\n  - "
                             (str/join "\n  - " ps)
                             "\n可以这样生成:\n"
                             "  export JWT_SECRET=$(openssl rand -hex 32)\n"
                             "  export COOKIE_SECRET=$(openssl rand -hex 8)")
                        {:type ::insecure-secrets :problems ps}))))))
