(ns com.ruoyi.frontend.subs.monitor
  "re-frame 订阅：在线用户/定时任务/服务监控/连接池/缓存监控。"
  (:require
   [re-frame.core :as rf]))

;; ─── 在线用户 ──────────────────────────────────────────────────────

(rf/reg-sub :online-users/items
            (fn [db _]
              (get-in db [:online-users :items])))

(rf/reg-sub :online-users/total
            (fn [db _]
              (get-in db [:online-users :total])))

(rf/reg-sub :online-users/loading?
            (fn [db _]
              (get-in db [:online-users :loading?])))

;; ─── 定时任务 ──────────────────────────────────────────────────────

(rf/reg-sub :jobs/items
            (fn [db _]
              (get-in db [:jobs :items])))

(rf/reg-sub :jobs/total
            (fn [db _]
              (get-in db [:jobs :total])))

(rf/reg-sub :jobs/loading?
            (fn [db _]
              (get-in db [:jobs :loading?])))

(rf/reg-sub :job-logs/items
            (fn [db _]
              (get-in db [:job-logs :items])))

(rf/reg-sub :job-logs/total
            (fn [db _]
              (get-in db [:job-logs :total])))

(rf/reg-sub :job-logs/loading?
            (fn [db _]
              (get-in db [:job-logs :loading?])))

;; ─── 服务器监控 ──────────────────────────────────────────────────────

(rf/reg-sub :server/data
            (fn [db _]
              (get-in db [:server :data])))

(rf/reg-sub :server/loading?
            (fn [db _]
              (get-in db [:server :loading?] false)))

(rf/reg-sub :server/datasource
            (fn [db _]
              (get-in db [:server :datasource])))

(rf/reg-sub :server/datasource-loading?
            (fn [db _]
              (get-in db [:server :datasource-loading?] false)))

;; ─── 连接池 ──────────────────────────────────────────────────────

(rf/reg-sub :integrant/data
            (fn [db _]
              (get-in db [:integrant :data])))

(rf/reg-sub :integrant/trace
            (fn [db [_ key]]
              (get-in db [:integrant :trace key])))

(rf/reg-sub :integrant/traces
            (fn [db _]
              (get-in db [:integrant :trace] {})))

;; ─── 缓存监控 ──────────────────────────────────────────────────────

(rf/reg-sub :cache/data
            (fn [db _]
              (get-in db [:cache :data])))

(rf/reg-sub :cache/names
            (fn [db _]
              (get-in db [:cache :names] [])))

(rf/reg-sub :cache/selected-name
            (fn [db _]
              (get-in db [:cache :selected-name])))

(rf/reg-sub :cache/keys
            (fn [db _]
              (get-in db [:cache :keys] [])))

(rf/reg-sub :cache/value
            (fn [db _]
              (get-in db [:cache :value])))

(rf/reg-sub :cache/value-visible?
            (fn [db _]
              (get-in db [:cache :value-visible?] false)))

(rf/reg-sub :cache/loading?
            (fn [db _]
              (get-in db [:cache :loading?] false)))
