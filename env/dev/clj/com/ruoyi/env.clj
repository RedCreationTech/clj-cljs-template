(ns com.ruoyi.env
  (:require
   [clojure.tools.logging :as log]
   [com.ruoyi.dev-middleware :refer [wrap-dev]]))

;; 跨 profile 同名定义:clj-kondo 一次 lint 所有 env 源根,按运行时会误报重定义
#_{:clj-kondo/ignore [:redefined-var]}
(def defaults
  {:init       (fn []
                 (log/info "\n-=[ruoyi starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[ruoyi started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[ruoyi has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev}})
