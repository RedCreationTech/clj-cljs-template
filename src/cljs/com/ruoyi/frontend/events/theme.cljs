(ns com.ruoyi.frontend.events.theme
  "主题与布局设置事件。"
  (:require
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.events.common :as ec]
   [re-frame.core :as rf]))

(rf/reg-event-db :theme/toggle-mode
                 (fn [db _]
                   (update-in db [:theme :mode] #(if (= % :light) :dark :light))))

(rf/reg-event-db :theme/set-mode
                 (fn [db [_ mode]]
                   (js/localStorage.setItem "rouyi-theme-mode" (name mode))
                   (assoc-in db [:theme :mode] mode)))

(rf/reg-event-db :theme/set-algorithm
                 (fn [db [_ algorithm]]
                   (js/localStorage.setItem "rouyi-theme-algorithm" algorithm)
                   (assoc-in db [:theme :algorithm] algorithm)))

(rf/reg-event-db :theme/set-primary-color
                 (fn [db [_ color]]
                   (js/localStorage.setItem "rouyi-primary-color" color)
                   (assoc-in db [:theme :primary-color] color)))

(rf/reg-event-db :theme/set-component-size
                 (fn [db [_ size]]
                   (js/localStorage.setItem "rouyi-component-size" size)
                   (assoc-in db [:theme :component-size] size)))

(rf/reg-event-db :theme/set-density
                 (fn [db [_ size]]
                   (js/localStorage.setItem "rouyi-component-size" size)
                   (js/localStorage.setItem "rouyi-theme-algorithm"
                                            (if (= size "small") "compact" "default"))
                   (-> db
                       (assoc-in [:theme :component-size] size)
                       (assoc-in [:theme :algorithm] (if (= size "small") "compact" "default")))))

(rf/reg-event-db :theme/set-font-size
                 (fn [db [_ size]]
                   (js/localStorage.setItem "rouyi-font-size" size)
                   (assoc-in db [:theme :font-size] size)))

(rf/reg-event-db :theme/load-from-storage
                 (fn [db _]
                   (let [mode (js/localStorage.getItem "rouyi-theme-mode")
                         algorithm (js/localStorage.getItem "rouyi-theme-algorithm")
                         color (js/localStorage.getItem "rouyi-primary-color")
                         size (js/localStorage.getItem "rouyi-component-size")
                         font-size (js/localStorage.getItem "rouyi-font-size")
                         layout-settings (ec/stored-layout-settings)]
                     (cond-> db
                       mode (assoc-in [:theme :mode] (keyword mode))
                       algorithm (assoc-in [:theme :algorithm] algorithm)
                       color (assoc-in [:theme :primary-color] color)
                       size (assoc-in [:theme :component-size] size)
                       font-size (assoc-in [:theme :font-size] font-size)
                       layout-settings (assoc :layout-settings layout-settings)))))

(rf/reg-event-db :layout/set-setting
                 (fn [db [_ k value]]
                   (let [settings (assoc (merge db/default-layout-settings (:layout-settings db)) k value)
                         db* (assoc db :layout-settings settings)]
                     (ec/persist-layout-settings! settings)
                     (if (= k :theme-style)
                       (ec/apply-theme-style db* settings)
                       db*))))

(rf/reg-event-db :layout/reset-settings
                 (fn [db _]
                   (let [settings db/default-layout-settings]
                     (ec/persist-layout-settings! settings)
                     (js/localStorage.setItem "rouyi-primary-color" "#409eff")
                     (-> db
                         (assoc :layout-settings settings)
                         (assoc-in [:theme :primary-color] "#409eff")
                         (ec/apply-theme-style settings)))))
