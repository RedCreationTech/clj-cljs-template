(ns com.ruoyi.frontend.api.transport
  "HTTP 传输层:api-base / token 读取 / request 封装,供各领域 api 子命名空间共用。
   - 令牌已过有效期一半时顺带触发一次续期(:auth/refresh),持续使用的用户不会被登出;
   - 带令牌的请求返回 401(HTTP 状态或业务码)时触发 :auth/session-expired,回到登录页;
   - 其它失败(403 无权限、5xx、网络断开、业务码非 200…)统一 :api/error 提示,文案见 api.errors。"
  (:require
   [ajax.core :as ajax]
   [com.ruoyi.frontend.api.errors :as errors]
   [com.ruoyi.frontend.api.token :as token]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]))

(def api-base "/api")

(defn get-token
  []
  (get-in @rf-db/app-db [:auth :token]))

(defn- handle-failure!
  "带令牌的 401 → 会话过期;其它失败(HTTP 失败或业务码非 200)→ 统一提示(:silent? 时不提示)。"
  [token ok result silent?]
  (cond
    (and token (token/unauthorized? ok result)) (rf/dispatch [:auth/session-expired])
    silent? nil
    :else (when-let [msg (if ok (errors/business-message result) (errors/error-message result))]
            (rf/dispatch [:api/error msg]))))

(defn- prune-nils
  "去掉 GET 查询参数里值为 nil 的项。ajax 会把 nil 序列化成字符串 \"null\",后端只把空串当作未填写,
   \"null\" 会被当成真实筛选条件,导致下拉框没选时列表查不出任何数据。JSON 请求体里的 null 是有效值,不动。"
  [params]
  (reduce-kv (fn [m k v] (if (nil? v) m (assoc m k v))) {} params))

(defn request
  "发起 HTTP 请求。默认从 app-db 读取令牌;传 :token 可显式指定(如登出时);
   :silent? true 时失败不弹统一提示(后台续期、登出等);
   上传文件用 :body 传 js/FormData(不再按 JSON 编码 :params)。"
  [{:keys [method uri params body on-success on-error token silent?]}]
  (let [token (or token (get-token))]
    (when (and token (not= uri "/auth/refresh") (token/refresh-due? (token/claims token) (js/Date.now)))
      (rf/dispatch [:auth/refresh]))
    (ajax/ajax-request
     (merge {:method method
             :uri (str api-base uri)
             :headers (when token {"Authorization" (str "Bearer " token)})
             :response-format (ajax/json-response-format {:keywords? true})
             :handler (fn [[ok result]]
                        (handle-failure! token ok result silent?)
                        (if ok
                          (on-success result)
                          (on-error result)))}
            (if body
              {:body body}
              {:params (if (= method :get) (prune-nils params) params)
               :format (ajax/json-request-format)})))))

(defn- save-blob!
  "让浏览器把 blob 存成文件。"
  [blob filename]
  (let [url (js/URL.createObjectURL blob)
        a (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.removeChild (.-body js/document) a)
    (js/URL.revokeObjectURL url)))

(defn- download-response
  "fetch 的响应 → blob(成功)或 nil(已处理失败:会话过期 / 统一提示)。
   后端的业务错误是 HTTP 200 + JSON({:code 404 :msg ..}),不能当文件存下来。"
  [^js resp]
  (let [json? (some-> (.. resp -headers (get "content-type")) (.includes "application/json"))]
    (cond
      (= 401 (.-status resp)) (do (rf/dispatch [:auth/session-expired]) nil)
      (not (.-ok resp)) (-> (.json resp)
                            (.catch (constantly nil))
                            (.then (fn [body]
                                     (rf/dispatch [:api/error (errors/error-message
                                                               {:status (.-status resp)
                                                                :response (js->clj body :keywordize-keys true)})])
                                     nil)))
      json? (.then (.json resp)
                   (fn [body]
                     (rf/dispatch [:api/error (or (errors/business-message (js->clj body :keywordize-keys true))
                                                  "下载失败")])
                     nil))
      :else (.blob resp))))

(defn download!
  "带令牌下载文件(<a href> 带不了 Authorization 头):fetch → blob → 保存为 filename。
   失败走与 request 相同的处理(401 回登录页,其它统一提示)。json-body 非空时以 POST 发送。"
  [uri filename & {:keys [json-body]}]
  (let [token (get-token)
        headers (cond-> {}
                  token (assoc "Authorization" (str "Bearer " token))
                  json-body (assoc "Content-Type" "application/json"))]
    (-> (js/fetch (str api-base uri)
                  (clj->js (cond-> {:method (if json-body "POST" "GET") :headers headers}
                             json-body (assoc :body (js/JSON.stringify (clj->js json-body))))))
        (.then download-response)
        (.then (fn [blob] (when blob (save-blob! blob filename))))
        (.catch (fn [_] (rf/dispatch [:api/error "网络错误,请稍后重试"]))))))
