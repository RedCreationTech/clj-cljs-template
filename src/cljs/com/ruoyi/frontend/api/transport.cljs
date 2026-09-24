(ns com.ruoyi.frontend.api.transport
  "HTTP 传输层:api-base / token 读取 / request 封装,供各领域 api 子命名空间共用。
   - 令牌已过有效期一半时顺带触发一次续期(:auth/refresh),持续使用的用户不会被登出;
   - 带令牌的请求返回 401(HTTP 状态或业务码)时触发 :auth/session-expired,回到登录页。"
  (:require
   [ajax.core :as ajax]
   [com.ruoyi.frontend.api.token :as token]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]))

(def api-base "/api")

(defn get-token
  []
  (get-in @rf-db/app-db [:auth :token]))

(defn request
  "发起 HTTP 请求。默认从 app-db 读取令牌;传 :token 可显式指定(如登出时)。"
  [{:keys [method uri params on-success on-error token]}]
  (let [token (or token (get-token))]
    (when (and token (not= uri "/auth/refresh") (token/refresh-due? (token/claims token) (js/Date.now)))
      (rf/dispatch [:auth/refresh]))
    (ajax/ajax-request
     {:method method
      :uri (str api-base uri)
      :params params
      :headers (when token {"Authorization" (str "Bearer " token)})
      :format (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :handler (fn [[ok result]]
                 (when (and token (token/unauthorized? ok result))
                   (rf/dispatch [:auth/session-expired]))
                 (if ok
                   (on-success result)
                   (on-error result)))})))
