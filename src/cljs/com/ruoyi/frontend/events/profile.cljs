(ns com.ruoyi.frontend.events.profile
  "个人中心事件。"
  (:require
   [com.ruoyi.frontend.api.profile :as profile-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :profile/set-data
                 (fn [db [_ data]]
                   (assoc-in db [:profile :data] data)))

(rf/reg-event-db :profile/set-loading
                 (fn [db [_ loading?]]
                   (assoc-in db [:profile :loading?] loading?)))

(rf/reg-event-fx :profile/fetch
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:profile :loading?] true)
                    :api/get-profile nil}))

(rf/reg-fx :api/get-profile
           (fn [_]
             (profile-api/get-profile
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:profile/set-data (:data result)])))
              (fn [_]))))

(rf/reg-event-fx :profile/update
                 (fn [_ [_ params]]
                   {:api/update-profile params}))

(rf/reg-fx :api/update-profile
           (fn [params]
             (profile-api/update-profile params
                                         (fn [result]
                                           (when (= 200 (:code result))
                                             (js/alert "更新成功")
                                             (rf/dispatch [:profile/fetch])))
                                         (fn [_]))))

(rf/reg-event-fx :profile/change-password
                 (fn [_ [_ params]]
                   {:api/change-password params}))

(rf/reg-fx :api/change-password
           (fn [params]
             (profile-api/change-password params
                                          (fn [result]
                                            (when (= 200 (:code result))
                                              (js/alert "密码修改成功"))
                                            (when (not= 200 (:code result))
                                              (js/alert (:msg result))))
                                          (fn [_]))))

(rf/reg-fx :api/upload-avatar
           (fn [form-data]
             (profile-api/upload-avatar form-data
                                        (fn [result]
                                          (when (= 200 (:code result))
                                            (rf/dispatch [:profile/fetch])))
                                        (fn [_]))))
