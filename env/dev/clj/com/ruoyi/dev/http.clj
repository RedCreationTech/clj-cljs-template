(ns com.ruoyi.dev.http
  "只重建无状态 HTTP 装配;连接池、服务器、调度器及领域组件继续用原对象。"
  (:require
   [com.ruoyi.integrant.state :as state]
   [com.ruoyi.integrant.trace :as trace]
   [integrant.core :as ig]))

(defn http-key? [k]
  (or (#{:handler/ring :router/core :router/routes} k)
      (ig/derived-from? k :reitit/routes)))

(defn- restart-required [message data]
  (throw (ex-info (str message "; 请运行 (user/rr)")
                  (assoc data :restart-required true))))

(defn rebuild!
  "使用运行系统的配置快照构建 HTTP 链。未知依赖绝不 init,失败前不替换入口。"
  [sys]
  (let [cfg (::ig/origin (meta sys))]
    (when-not (and cfg (:handler/ring cfg))
      (restart-required "缺少运行系统的 HTTP 配置快照" {}))
    (let [build-handler (requiring-resolve 'com.ruoyi.web.handler/build-ring-handler)
          built (ig/build cfg (filter http-key? (keys cfg))
                          (fn [k opts]
                            (cond
                              (= k :handler/ring) (build-handler opts)
                              (http-key? k) (ig/init-key k opts)
                              (contains? sys k) (get sys k)
                              :else (restart-required "HTTP 依赖尚未运行" {:key k})))
                          (fn [_ k opts] (when (http-key? k) (ig/assert-key k opts)))
                          ig/resolve-key)
          actual (:handler/ring built)
          replacements (dissoc (into {} (filter (comp http-key? key)) built) :handler/ring)]
      ;; trace 与发布共享锁;追踪开启时保守拒绝,不破坏其 original/wrapper。
      (trace/install-ring-handler! actual)
      (swap! state/system merge replacements)
      :http-updated)))
