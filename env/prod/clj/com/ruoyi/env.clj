(ns com.ruoyi.env
  (:require
   [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[ruoyi starting]=-"))
   :start      (fn []
                 (log/info "\n-=[ruoyi started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[ruoyi has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
