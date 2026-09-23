(ns com.ruoyi.frontend.events.configs
  "参数配置事件。"
  (:require
   [com.ruoyi.frontend.events.common :as ec]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api :as api]
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.router :as router]
   [re-frame.core :as rf]))

(rf/reg-event-db :configs/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:configs :items] items)
                         (assoc-in [:configs :total] total)
                         (assoc-in [:configs :loading?] false)))))

(rf/reg-event-fx :configs/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:configs :loading?] true)
                    :api/list-configs params}))

(rf/reg-fx :api/list-configs
           (fn [params]
             (api/list-configs params
                               (fn [result]
                                 (when (= 200 (:code result))
                                   (rf/dispatch [:configs/set-list (:data result)])))
                               (fn [_]))))

(rf/reg-event-fx :configs/create
                 (fn [{:keys [db]} [_ params]]
                   {:db db
                    :api/create-config params}))

(rf/reg-fx :api/create-config
           (fn [params]
             (api/create-config params
                                (fn [result]
                                  (when (= 200 (:code result))
                                    (rf/dispatch [:configs/created])
                                    (antd/success! "创建成功"))
                                  (when (not= 200 (:code result))
                                    (antd/error! (:msg result))))
                                (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :configs/created
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:notification] nil)
                    :dispatch [:configs/fetch {}]}))

(rf/reg-event-fx :configs/update
                 (fn [{:keys [db]} [_ id params]]
                   {:db db
                    :api/update-config [id params]}))

(rf/reg-fx :api/update-config
           (fn [[id params]]
             (api/update-config id params
                                (fn [result]
                                  (when (= 200 (:code result))
                                    (rf/dispatch [:configs/updated])
                                    (antd/success! "更新成功"))
                                  (when (not= 200 (:code result))
                                    (antd/error! (:msg result))))
                                (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :configs/updated
                 (fn [{:keys [db]} _]
                   {:db db
                    :dispatch [:configs/fetch {}]}))

(rf/reg-event-fx :configs/delete
                 (fn [{:keys [db]} [_ id]]
                   {:db db
                    :api/delete-config id}))

(rf/reg-fx :api/delete-config
           (fn [id]
             (api/delete-config id
                                (fn [result]
                                  (when (= 200 (:code result))
                                    (rf/dispatch [:configs/deleted])
                                    (antd/success! "删除成功"))
                                  (when (not= 200 (:code result))
                                    (antd/error! (:msg result))))
                                (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :configs/deleted
                 (fn [{:keys [db]} _]
                   {:db db
                    :dispatch [:configs/fetch {}]}))
