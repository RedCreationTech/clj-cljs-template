(ns com.ruoyi.frontend.events.depts
  "部门管理事件。"
  (:require
   [com.ruoyi.frontend.events.common :as ec]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api :as api]
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.router :as router]
   [re-frame.core :as rf]))

(rf/reg-event-db :depts/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         tree (ec/build-dept-tree items 0)
                         _ (js/console.log "[depts/set-list] tree count:" (count tree) "first:" (clj->js (first tree)))]
                     (-> db
                         (assoc-in [:depts :items] items)
                         (assoc-in [:depts :tree] tree)
                         (assoc-in [:depts :loading?] false)))))

(rf/reg-event-fx :depts/search
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:depts :loading?] true)
                    :api/list-depts-search params}))

(rf/reg-fx :api/list-depts-search
           (fn [params]
             (api/list-depts {}
                             (fn [result]
                               (when (= 200 (:code result))
                                 (let [items (:data result [])
                                       filtered (cond->> items
                                                  (:dept_name params)
                                                  (filter #(clojure.string/includes?
                                                            (or (:dept_name %) "")
                                                            (:dept_name params)))
                                                  (some? (:status params))
                                                  (filter #(= (:status params) (:status %))))]
                                   (rf/dispatch [:depts/set-list filtered]))))
                             (fn [_]))))

(rf/reg-event-fx :depts/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:depts :loading?] true)
                    :api/list-depts params}))

(rf/reg-fx :api/list-depts
           (fn [params]
             (api/list-depts params
                             (fn [result]
                               (when (= 200 (:code result))
                                 (rf/dispatch [:depts/set-list (:data result)])))
                             (fn [_]))))

(rf/reg-event-db :depts/open-modal
                 (fn [db [_ initial-data]]
                   (-> db (assoc-in [:depts :modal-visible?] true) (assoc-in [:depts :editing] nil)
                       (assoc-in [:depts :form-data] (merge {:order_num 0 :status "0"} initial-data)))))

(rf/reg-event-db :depts/close-modal
                 (fn [db _] (assoc-in db [:depts :modal-visible?] false)))

(rf/reg-event-db :depts/edit
                 (fn [db [_ data]]
                   (-> db (assoc-in [:depts :modal-visible?] true) (assoc-in [:depts :editing] data)
                       (assoc-in [:depts :form-data] data))))

(rf/reg-event-fx :depts/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [editing (get-in db [:depts :editing])
                         values (update values :order_num #(if (string? %) (js/parseInt % 10) %))]
                     (if editing
                       {:db (assoc-in db [:depts :modal-visible?] false) :api/update-dept [(:dept_id editing) values]}
                       {:db (assoc-in db [:depts :modal-visible?] false) :api/create-dept values}))))

(rf/reg-fx :api/create-dept
           (fn [params]
             (api/create-dept params (fn [r] (when (= 200 (:code r)) (antd/success! "创建成功") (rf/dispatch [:depts/fetch {}]))) (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/update-dept
           (fn [[id params]]
             (api/update-dept id params (fn [r] (when (= 200 (:code r)) (antd/success! "更新成功") (rf/dispatch [:depts/fetch {}]))) (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :depts/delete
                 (fn [_ [_ id]] {:api/delete-dept id}))

(rf/reg-event-fx :depts/change-status
                 (fn [_ [_ id status]]
                   {:api/change-dept-status [id status]}))

(rf/reg-fx :api/delete-dept
           (fn [id]
             (api/delete-dept id (fn [r] (when (= 200 (:code r)) (antd/success! "删除成功") (rf/dispatch [:depts/fetch {}]))) (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/change-dept-status
           (fn [[id status]]
             (api/change-dept-status id status
                                     (fn [result]
                                       (when (= 200 (:code result))
                                         (antd/success! "状态修改成功")
                                         (rf/dispatch [:depts/fetch {}])))
                                     (fn [_] (antd/error! "网络错误")))))
