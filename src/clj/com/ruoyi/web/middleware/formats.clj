(ns com.ruoyi.web.middleware.formats
  (:require
   [com.ruoyi.infra.json :as json]
   [luminus-transit.time :as time]
   [muuntaja.core :as m]))

(def instance
  (m/create
   (-> m/default-options
       ;; JSON 响应用 infra.json 的 mapper:时间统一为本地 "yyyy-MM-dd HH:mm:ss",两库一致
       (assoc-in [:formats "application/json" :encoder-opts] {:mapper json/mapper})
       (update-in
        [:formats "application/transit+json" :decoder-opts]
        (partial merge time/time-deserialization-handlers))
       (update-in
        [:formats "application/transit+json" :encoder-opts]
        (partial merge time/time-serialization-handlers)))))
