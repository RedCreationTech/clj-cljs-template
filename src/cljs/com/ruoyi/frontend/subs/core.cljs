(ns com.ruoyi.frontend.subs.core
  "re-frame 订阅：页面/鉴权/主题/布局/标签页等基础订阅。"
  (:require
   [com.ruoyi.frontend.i18n :as i18n]
   [re-frame.core :as rf]))

(rf/reg-sub :page
            (fn [db _]
              (:page db)))

(rf/reg-sub :auth/token
            (fn [db _]
              (get-in db [:auth :token])))

(rf/reg-sub :auth/user
            (fn [db _]
              (get-in db [:auth :user])))

(rf/reg-sub :auth/loading?
            (fn [db _]
              (get-in db [:auth :loading?])))

(rf/reg-sub :auth/login-config
            (fn [db _]
              (get-in db [:auth :login-config])))

(rf/reg-sub :auth/failures
            (fn [db _]
              (get-in db [:auth :failures] 0)))

(rf/reg-sub :auth/logged-in?
            (fn [db _]
              (boolean (get-in db [:auth :token]))))

(rf/reg-sub :i18n/locale
            (fn [db _]
              (:locale db i18n/default-locale)))

(rf/reg-sub :theme/mode
            (fn [db _]
              (get-in db [:theme :mode] :light)))

(rf/reg-sub :theme/primary-color
            (fn [db _]
              (get-in db [:theme :primary-color] "#1677ff")))

(rf/reg-sub :theme/compact?
            (fn [db _]
              (get-in db [:theme :compact?] false)))

(rf/reg-sub :theme/algorithm
            (fn [db _]
              (get-in db [:theme :algorithm] "default")))

(rf/reg-sub :theme/component-size
            (fn [db _]
              (get-in db [:theme :component-size] "middle")))

(rf/reg-sub :theme/font-size
            (fn [db _]
              (get-in db [:theme :font-size] "middle")))

(rf/reg-sub :layout/settings
            (fn [db _]
              (:layout-settings db)))

(rf/reg-sub :layout/setting
            (fn [db [_ k]]
              (get-in db [:layout-settings k])))

;; ─── Tabs ──────────────────────────────────────────────────────────

(rf/reg-sub :tabs/items
            (fn [db _]
              (get-in db [:tabs :items] [])))

(rf/reg-sub :tabs/active
            (fn [db _]
              (get-in db [:tabs :active] :dashboard)))
