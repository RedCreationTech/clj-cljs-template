(ns com.ruoyi.frontend.events.menus
  "菜单管理事件。"
  (:require
   [clojure.string]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.menus :as menus-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :menus/set-list
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:menus :items] data)
                       (assoc-in [:menus :loading?] false))))

(rf/reg-event-db :menus/set-tree
                 (fn [db [_ data]]
                   (assoc-in db [:menus :tree-data] data)))

(rf/reg-event-fx :menus/fetch
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:menus :loading?] true)
                    :api/list-menus nil}))

(rf/reg-event-fx :menus/search
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:menus :loading?] true)
                    :api/list-menus-search params}))

(rf/reg-fx :api/list-menus-search
           (fn [params]
             (menus-api/list-menus
              {}
              (fn [result]
                (when (= 200 (:code result))
                  (let [data (:data result)
                        items (if (sequential? data) data (:rows data []))
                        ;; 客户端过滤
                        filtered (cond->> items
                                   (:menu_name params)
                                   (filter #(clojure.string/includes?
                                             (or (:menu_name %) "")
                                             (:menu_name params)))
                                   (some? (:status params))
                                   (filter #(= (:status params) (:status %))))]
                    (rf/dispatch [:menus/set-list filtered]))))
              (fn [_]))))

(rf/reg-event-fx :menus/fetch-tree
                 (fn [{:keys [db]} _]
                   {:db db
                    :api/menu-tree-for-menus nil}))

(rf/reg-fx :api/menu-tree-for-menus
           (fn [_]
             (menus-api/menu-tree
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:menus/set-tree (:data result)])))
              (fn [_]))))

(rf/reg-fx :api/list-menus
           (fn [params]
             (menus-api/list-menus params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (rf/dispatch [:menus/set-list (:data result)])))
                                   (fn [_]))))

(rf/reg-event-db :menus/open-modal
                 (fn [db [_ initial-data]]
                   (-> db
                       (assoc-in [:menus :modal-visible?] true)
                       (assoc-in [:menus :editing?] false)
                       (assoc-in [:menus :editing] false)
                       (assoc-in [:menus :form-data] (merge {:menu_type "M" :order_num 0 :status "0" :visible "0" :is_frame "0" :is_cache "0"} initial-data)))))

(rf/reg-event-db :menus/close-modal
                 (fn [db _]
                   (assoc-in db [:menus :modal-visible?] false)))

(rf/reg-event-db :menus/edit
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:menus :modal-visible?] true)
                       (assoc-in [:menus :editing?] true)
                       (assoc-in [:menus :editing] true)
                       (assoc-in [:menus :form-data] data))))

(rf/reg-event-fx :menus/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [data (-> values
                                  (update :order_num #(if (seq (str %)) (js/parseInt % 10) 0)))
                         editing (get-in db [:menus :editing])]
                     (if editing
                       {:db (assoc-in db [:menus :modal-visible?] false)
                        :api/update-menu [(:menu_id data) data]}
                       {:db (assoc-in db [:menus :modal-visible?] false)
                        :api/create-menu data}))))

(rf/reg-fx :api/create-menu
           (fn [params]
             (menus-api/create-menu params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "创建成功")
                                        (rf/dispatch [:menus/fetch])))
                                    (fn [_]))))

(rf/reg-fx :api/update-menu
           (fn [[id params]]
             (menus-api/update-menu id params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "更新成功")
                                        (rf/dispatch [:menus/fetch])))
                                    (fn [_]))))

(rf/reg-event-fx :menus/delete
                 (fn [_ [_ id]]
                   {:api/delete-menu id}))

(rf/reg-event-fx :menus/change-status
                 (fn [_ [_ id status]]
                   {:api/change-menu-status [id status]}))

(rf/reg-fx :api/delete-menu
           (fn [id]
             (menus-api/delete-menu id
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "删除成功")
                                        (rf/dispatch [:menus/fetch])))
                                    (fn [_]))))

(rf/reg-fx :api/change-menu-status
           (fn [[id status]]
             (menus-api/change-menu-status id status
                                           (fn [result]
                                             (when (= 200 (:code result))
                                               (antd/success! "状态修改成功")
                                               (rf/dispatch [:menus/fetch])))
                                           (fn [_]))))
