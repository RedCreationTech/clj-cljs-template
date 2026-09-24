(ns com.ruoyi.frontend.events.theme
  "主题、布局设置与界面语言事件。"
  (:require
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.events.common :as ec]
   [com.ruoyi.frontend.i18n :as i18n]
   [com.ruoyi.frontend.storage :as storage]
   [re-frame.core :as rf]))

(rf/reg-event-db :theme/toggle-mode
                 (fn [db _]
                   (update-in db [:theme :mode] #(if (= % :light) :dark :light))))

(rf/reg-event-db :theme/set-mode
                 (fn [db [_ mode]]
                   (storage/set-item! :theme-mode mode)
                   (assoc-in db [:theme :mode] mode)))

(rf/reg-event-db :theme/set-algorithm
                 (fn [db [_ algorithm]]
                   (storage/set-item! :theme-algorithm algorithm)
                   (assoc-in db [:theme :algorithm] algorithm)))

(rf/reg-event-db :theme/set-primary-color
                 (fn [db [_ color]]
                   (storage/set-item! :primary-color color)
                   (assoc-in db [:theme :primary-color] color)))

(rf/reg-event-db :theme/set-component-size
                 (fn [db [_ size]]
                   (storage/set-item! :component-size size)
                   (assoc-in db [:theme :component-size] size)))

(rf/reg-event-db :theme/set-density
                 (fn [db [_ size]]
                   (storage/set-item! :component-size size)
                   (storage/set-item! :theme-algorithm (if (= size "small") "compact" "default"))
                   (-> db
                       (assoc-in [:theme :component-size] size)
                       (assoc-in [:theme :algorithm] (if (= size "small") "compact" "default")))))

(rf/reg-event-db :theme/set-font-size
                 (fn [db [_ size]]
                   (storage/set-item! :font-size size)
                   (assoc-in db [:theme :font-size] size)))

(rf/reg-event-db :theme/load-from-storage
                 (fn [db _]
                   (let [mode (storage/get-item :theme-mode)
                         algorithm (storage/get-item :theme-algorithm)
                         color (storage/get-item :primary-color)
                         size (storage/get-item :component-size)
                         font-size (storage/get-item :font-size)
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
                     (storage/set-item! :primary-color "#409eff")
                     (-> db
                         (assoc :layout-settings settings)
                         (assoc-in [:theme :primary-color] "#409eff")
                         (ec/apply-theme-style settings)))))

(rf/reg-event-db :i18n/set-locale
                 (fn [db [_ k]]
                   (assoc db :locale (i18n/set-locale! k))))
