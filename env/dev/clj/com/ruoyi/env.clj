(ns com.ruoyi.env
  (:require
   [clojure.tools.logging :as log]
   [com.ruoyi.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[ruoyi starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[ruoyi started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[ruoyi has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev}})
