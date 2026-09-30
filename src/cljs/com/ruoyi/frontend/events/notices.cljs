(ns com.ruoyi.frontend.events.notices
  "通知公告事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.notices :as notices-api]
   [com.ruoyi.frontend.events.common :as common]
   [re-frame.core :as rf]))

(rf/reg-event-db :notices/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:notices :items] items)
                         (assoc-in [:notices :total] total)
                         (assoc-in [:notices :loading?] false)))))

(rf/reg-event-fx :notices/fetch
                 (fn [{:keys [db]} [_ overrides]]
                   (common/fetch-with-query db :notices :api/list-notices overrides)))

(rf/reg-event-fx :notices/search
                 ;; 搜索栏把条件写进 query-params 后调用;换条件回到第 1 页
                 (fn [_ [_ filters]]
                   {:dispatch [:notices/fetch (merge {:page 1} filters)]}))

(rf/reg-event-fx :notices/change-page
                 (fn [_ [_ page page-size]]
                   {:dispatch [:notices/fetch {:page page :size page-size}]}))

(rf/reg-fx :api/list-notices
           (fn [params]
             (notices-api/list-notices params
                                       (fn [result]
                                         (when (= 200 (:code result))
                                           (rf/dispatch [:notices/set-list (:data result)])))
                                       (fn [_]))))

(defn- close-modal [db]
  (-> db
      (assoc-in [:notices :modal-visible?] false)
      (assoc-in [:notices :editing] nil)
      (assoc-in [:notices :form-data] {})))

(rf/reg-event-db :notices/open-modal
                 (fn [db _] (-> (close-modal db)
                                (assoc-in [:notices :modal-visible?] true))))

(rf/reg-event-db :notices/close-modal
                 (fn [db _] (close-modal db)))

(rf/reg-event-db :notices/edit
                 (fn [db [_ item]]
                   (-> db
                       (assoc-in [:notices :modal-visible?] true)
                       (assoc-in [:notices :editing] item)
                       (assoc-in [:notices :form-data] item))))

(rf/reg-event-fx :notices/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [editing (get-in db [:notices :editing])]
                     {:db (close-modal db)
                      (if editing :api/update-notice :api/create-notice)
                      (if editing [(:notice_id editing) values] values)})))

(defn- refetch-on-ok [msg result]
  (when (= 200 (:code result))
    (antd/success! msg)
    ;; 不带参数:沿用当前页与筛选条件
    (rf/dispatch [:notices/fetch])))

(rf/reg-fx :api/create-notice
           (fn [params]
             (notices-api/create-notice params
                                        #(refetch-on-ok "创建成功" %)
                                        (fn [_]))))

(rf/reg-fx :api/update-notice
           (fn [[id params]]
             (notices-api/update-notice id params
                                        #(refetch-on-ok "更新成功" %)
                                        (fn [_]))))

(rf/reg-event-fx :notices/delete
                 (fn [_ [_ id]]
                   {:api/delete-notice id}))

(rf/reg-fx :api/delete-notice
           (fn [id]
             (notices-api/delete-notice id
                                        #(refetch-on-ok "删除成功" %)
                                        (fn [_]))))

;; ─── 顶部铃铛:最新通知与未读角标 ─────────────────────────────────
;; 已读状态按用户存在后端(sys_notice_read),换设备也一致。

(rf/reg-event-fx :notice-bell/fetch
                 (fn [_ _] {:api/latest-notices nil}))

(rf/reg-fx :api/latest-notices
           (fn [_]
             (notices-api/latest-notices
              #(when (= 200 (:code %)) (rf/dispatch [:notice-bell/set-data (:data %)]))
              (fn [_]))))

(rf/reg-event-db :notice-bell/set-data
                 (fn [db [_ {:keys [rows unread]}]]
                   (assoc db :notice-bell {:items (vec rows) :unread (or unread 0)})))

(rf/reg-event-fx :notice-bell/mark-read
                 (fn [{:keys [db]} _]
                   (when (pos? (get-in db [:notice-bell :unread] 0))
                     {:db (assoc-in db [:notice-bell :unread] 0)
                      :api/read-all-notices nil})))

(rf/reg-fx :api/read-all-notices
           (fn [_]
             (notices-api/read-all-notices #(rf/dispatch [:notice-bell/fetch]) (fn [_]))))
