(ns com.ruoyi.frontend.api.token
  "令牌相关的纯函数(不依赖浏览器与 HTTP 库,可在 Node 单元测试里直接测)。"
  (:require
   [clojure.string :as str]))

(defn claims
  "解码 JWT 载荷(不校验签名,只读取 iat/exp 等);格式不对返回 nil。"
  [token]
  (try
    (let [payload (-> (second (str/split (str token) #"\."))
                      (str/replace "-" "+")
                      (str/replace "_" "/"))]
      (js->clj (js/JSON.parse (js/atob payload)) :keywordize-keys true))
    (catch :default _ nil)))

(defn refresh-due?
  "令牌已过有效期一半且尚未过期时返回 true。iat/exp 为 Unix 秒,now-ms 为毫秒。"
  [{:keys [iat exp]} now-ms]
  (boolean
   (when (and (number? iat) (number? exp))
     (let [now (/ now-ms 1000)]
       (and (< now exp) (> now (+ iat (/ (- exp iat) 2))))))))

(defn unauthorized?
  "响应是否表示未登录/会话失效:HTTP 401,或 HTTP 200 但业务码 401。"
  [ok result]
  (if ok
    (= 401 (:code result))
    (= 401 (:status result))))
