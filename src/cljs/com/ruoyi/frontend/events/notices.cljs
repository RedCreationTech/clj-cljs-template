(ns com.ruoyi.frontend.events.notices
  "通知公告事件。"
  (:require
   [clojure.string]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.notices :as notices-api]
   [com.ruoyi.frontend.storage :as storage]
   [re-frame.core :as rf]))

(rf/reg-event-fx :notices/search
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:notices :loading?] true)
                    :api/list-notices-search params}))

(rf/reg-fx :api/list-notices-search
           (fn [params]
             (notices-api/list-notices {}
                                       (fn [result]
                                         (when (= 200 (:code result))
                                           (let [data (:data result)
                                                 items (if (sequential? data) data (:rows data []))
                                                 filtered (cond->> items
                                                            (:notice_name params)
                                                            (filter #(clojure.string/includes?
                                                                      (or (:notice_name %) "")
                                                                      (:notice_name params))))]
                                             (rf/dispatch [:notices/set-list {:rows filtered :total (count filtered)}]))))
                                       (fn [_]))))

(rf/reg-event-fx :notices/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:notices :loading?] true)
                    :api/list-notices params}))

(rf/reg-fx :api/list-notices
           (fn [params]
             (notices-api/list-notices params
                                       (fn [result]
                                         (when (= 200 (:code result))
                                           (rf/dispatch [:notices/set-list (:data result)])))
                                       (fn [_]))))

(rf/reg-event-db :notices/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (assoc db :notices {:items items :total total :loading? false
                                         :modal-visible? false :editing nil :form-data {}}))))

(rf/reg-event-db :notices/open-modal
                 (fn [db _]
                   (assoc db :notices {:items (get-in db [:notices :items] [])
                                       :total (get-in db [:notices :total] 0)
                                       :loading? false
                                       :modal-visible? true :editing nil :form-data {}})))

(rf/reg-event-db :notices/close-modal
                 (fn [db _]
                   (assoc-in db [:notices :modal-visible?] false)))

(rf/reg-event-db :notices/edit
                 (fn [db [_ item]]
                   (-> db
                       (assoc-in [:notices :modal-visible?] true)
                       (assoc-in [:notices :editing] item)
                       (assoc-in [:notices :form-data] item))))

(rf/reg-event-fx :notices/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [editing (get-in db [:notices :editing])]
                     (if editing
                       {:api/update-notice [(:notice_id editing) values]}
                       {:api/create-notice values}))))

(rf/reg-fx :api/create-notice
           (fn [params]
             (notices-api/create-notice params
                                        (fn [result]
                                          (when (= 200 (:code result))
                                            (antd/success! "创建成功")
                                            (rf/dispatch [:notices/fetch {}])))
                                        (fn [_]))))

(rf/reg-fx :api/update-notice
           (fn [[id params]]
             (notices-api/update-notice id params
                                        (fn [result]
                                          (when (= 200 (:code result))
                                            (antd/success! "更新成功")
                                            (rf/dispatch [:notices/fetch {}])))
                                        (fn [_]))))

(rf/reg-event-fx :notices/delete
                 (fn [_ [_ id]]
                   {:api/delete-notice id}))

(rf/reg-fx :api/delete-notice
           (fn [id]
             (notices-api/delete-notice id
                                        (fn [result]
                                          (when (= 200 (:code result))
                                            (antd/success! "删除成功")
                                            (rf/dispatch [:notices/fetch {}])))
                                        (fn [_]))))

;; ─── 顶部铃铛:最新通知与未读角标 ─────────────────────────────────
;; 「已读」按浏览器记住上次打开铃铛时看到的最新 notice_id(localStorage),不做服务端已读回执。

(rf/reg-event-fx :notice-bell/fetch
                 (fn [_ _] {:api/latest-notices nil}))

(rf/reg-fx :api/latest-notices
           (fn [_]
             (notices-api/latest-notices
              #(when (= 200 (:code %)) (rf/dispatch [:notice-bell/set-items (:data %)]))
              (fn [_]))))

(rf/reg-event-db :notice-bell/set-items
                 (fn [db [_ items]]
                   (-> db
                       (assoc-in [:notice-bell :items] (vec items))
                       (update-in [:notice-bell :seen-id]
                                  #(or % (some-> (storage/get-item :notice-seen) js/parseInt))))))

(rf/reg-event-db :notice-bell/mark-seen
                 (fn [db _]
                   (let [latest (reduce max 0 (map :notice_id (get-in db [:notice-bell :items])))]
                     (storage/set-item! :notice-seen latest)
                     (assoc-in db [:notice-bell :seen-id] latest))))
