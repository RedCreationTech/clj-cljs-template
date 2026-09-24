(ns com.ruoyi.frontend.subs.config
  "re-frame 订阅：个人中心/字典/参数/操作日志/登录日志/通知公告(含顶部铃铛)/首页仪表盘。"
  (:require
   [re-frame.core :as rf]))

;; ─── 个人中心 ──────────────────────────────────────────────────────

(rf/reg-sub :profile/data
            (fn [db _]
              (get-in db [:profile :data])))

(rf/reg-sub :profile/loading?
            (fn [db _]
              (get-in db [:profile :loading?])))

;; ─── 字典管理 ──────────────────────────────────────────────────────

(rf/reg-sub :dicts/types
            (fn [db _]
              (get-in db [:dicts :types])))

(rf/reg-sub :dicts/data
            (fn [db _]
              (get-in db [:dicts :data])))

(rf/reg-sub :dicts/loading?
            (fn [db _]
              (get-in db [:dicts :loading?])))

(rf/reg-sub :dicts/selected-type
            (fn [db _]
              (get-in db [:dicts :selected-type] nil)))

;; ─── 参数管理 ──────────────────────────────────────────────────────

(rf/reg-sub :configs/items
            (fn [db _]
              (get-in db [:configs :items])))

(rf/reg-sub :configs/total
            (fn [db _]
              (get-in db [:configs :total])))

(rf/reg-sub :configs/loading?
            (fn [db _]
              (get-in db [:configs :loading?])))

;; ─── 操作日志 ──────────────────────────────────────────────────────

(rf/reg-sub :oper-logs/items
            (fn [db _]
              (get-in db [:oper-logs :items])))

(rf/reg-sub :oper-logs/total
            (fn [db _]
              (get-in db [:oper-logs :total])))

(rf/reg-sub :oper-logs/loading?
            (fn [db _]
              (get-in db [:oper-logs :loading?])))

(rf/reg-sub :oper-logs/detail-visible?
            (fn [db _]
              (get-in db [:oper-logs :detail-visible?] false)))

(rf/reg-sub :oper-logs/detail-data
            (fn [db _]
              (get-in db [:oper-logs :detail-data])))

;; ─── 登录日志 ──────────────────────────────────────────────────────

(rf/reg-sub :login-logs/items
            (fn [db _]
              (get-in db [:login-logs :items])))

(rf/reg-sub :login-logs/total
            (fn [db _]
              (get-in db [:login-logs :total])))

(rf/reg-sub :login-logs/loading?
            (fn [db _]
              (get-in db [:login-logs :loading?])))

;; ─── 首页仪表盘 ──────────────────────────────────────────────────────

(rf/reg-sub :dashboard/stats
            (fn [db _]
              (get-in db [:dashboard :stats])))

(rf/reg-sub :dashboard/loading?
            (fn [db _]
              (get-in db [:dashboard :loading?] false)))

;; ─── 通知公告 ──────────────────────────────────────────────────────

(rf/reg-sub :notices/items
            (fn [db _]
              (get-in db [:notices :items])))

(rf/reg-sub :notices/total
            (fn [db _]
              (get-in db [:notices :total])))

(rf/reg-sub :notices/loading?
            (fn [db _]
              (get-in db [:notices :loading?] false)))

(rf/reg-sub :notices/modal-visible?
            (fn [db _]
              (get-in db [:notices :modal-visible?] false)))

(rf/reg-sub :notices/editing?
            (fn [db _]
              (boolean (get-in db [:notices :editing]))))

(rf/reg-sub :notices/editing
            (fn [db _]
              (get-in db [:notices :editing])))

(rf/reg-sub :notices/form-data
            (fn [db _]
              (get-in db [:notices :form-data] {})))

(rf/reg-sub :notice-bell/items
            (fn [db _]
              (get-in db [:notice-bell :items] [])))

(rf/reg-sub :notice-bell/unread
            (fn [db _]
              (get-in db [:notice-bell :unread] 0)))
