(ns com.ruoyi.frontend.api.transport
  "HTTP 传输层:api-base / token 读取 / request 封装,供各领域 api 子命名空间共用。"
  (:require
   [ajax.core :as ajax]
   [re-frame.db :as rf-db]))

(def api-base "/api")

(defn get-token
  []
  (get-in @rf-db/app-db [:auth :token]))

(defn request
  "发起 HTTP 请求，从 re-frame app-db 读取 token。"
  [{:keys [method uri params on-success on-error]}]
  (ajax/ajax-request
   {:method method
    :uri (str api-base uri)
    :params params
    :headers (when-let [token (get-token)]
               {"Authorization" (str "Bearer " token)})
    :format (ajax/json-request-format)
    :response-format (ajax/json-response-format {:keywords? true})
    :handler (fn [[ok result]]
               (if ok
                 (on-success result)
                 (on-error result)))}))
