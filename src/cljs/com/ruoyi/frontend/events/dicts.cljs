(ns com.ruoyi.frontend.events.dicts
  "字典管理事件。"
  (:require
   [clojure.string]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api :as api]
   [re-frame.core :as rf]))

(rf/reg-event-db :dicts/set-types
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))]
                     (-> db
                         (assoc-in [:dicts :types] items)
                         (assoc-in [:dicts :loading?] false)))))

(rf/reg-event-fx :dicts/search
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:dicts :loading?] true)
                    :api/list-dicts-search params}))

(rf/reg-fx :api/list-dicts-search
           (fn [params]
             (api/list-dict-types {}
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (let [data (:data result)
                                            items (if (sequential? data) data (:rows data []))
                                            filtered (cond->> items
                                                       (:dict_name params)
                                                       (filter #(clojure.string/includes?
                                                                 (or (:dict_name %) "")
                                                                 (:dict_name params)))
                                                       (:dict_type params)
                                                       (filter #(clojure.string/includes?
                                                                 (or (:dict_type %) "")
                                                                 (:dict_type params)))
                                                       (some? (:status params))
                                                       (filter #(= (:status params) (:status %))))]
                                        (rf/dispatch [:dicts/set-types {:rows filtered :total (count filtered)}]))))
                                  (fn [_]))))

(rf/reg-event-fx :dicts/fetch-types
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:dicts :loading?] true)
                    :api/list-dict-types params}))

(rf/reg-fx :api/list-dict-types
           (fn [params]
             (api/list-dict-types params
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (rf/dispatch [:dicts/set-types (:data result)])))
                                  (fn [_]))))

(rf/reg-event-db :dicts/set-data
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))]
                     (-> db
                         (assoc-in [:dicts :data] items)
                         (assoc-in [:dicts :loading?] false)))))

(rf/reg-event-fx :dicts/fetch-data
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:dicts :loading?] true)
                    :api/list-dict-data params}))

(rf/reg-event-db :dicts/select-type
                 (fn [db [_ dict-type]]
                   (assoc-in db [:dicts :selected-type] dict-type)))

(rf/reg-event-db :dicts/clear-selected-type
                 (fn [db _]
                   (assoc-in db [:dicts :selected-type] nil)))

(rf/reg-fx :api/list-dict-data
           (fn [params]
             (api/list-dict-data params
                                 (fn [result]
                                   (when (= 200 (:code result))
                                     (rf/dispatch [:dicts/set-data (:data result)])))
                                 (fn [_]))))

(rf/reg-event-fx :dicts/create-type
                 (fn [_ [_ params]]
                   {:api/create-dict-type params}))

(rf/reg-fx :api/create-dict-type
           (fn [params]
             (api/create-dict-type params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "创建成功")
                                       (rf/dispatch [:dicts/fetch-types {}]))
                                     (when (not= 200 (:code result))
                                       (antd/error! (:msg result))))
                                   (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :dicts/update-type
                 (fn [_ [_ id params]]
                   {:api/update-dict-type [id params]}))

(rf/reg-fx :api/update-dict-type
           (fn [[id params]]
             (api/update-dict-type id params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "更新成功")
                                       (rf/dispatch [:dicts/fetch-types {}]))
                                     (when (not= 200 (:code result))
                                       (antd/error! (:msg result))))
                                   (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :dicts/delete-type
                 (fn [_ [_ id]]
                   {:api/delete-dict-type id}))

(rf/reg-fx :api/delete-dict-type
           (fn [id]
             (api/delete-dict-type id
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "删除成功")
                                       (rf/dispatch [:dicts/fetch-types {}]))
                                     (when (not= 200 (:code result))
                                       (antd/error! (:msg result))))
                                   (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :dicts/create-data
                 (fn [_ [_ params]]
                   {:api/create-dict-data params}))

(rf/reg-fx :api/create-dict-data
           (fn [params]
             (api/create-dict-data params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "创建成功")
                                       (rf/dispatch [:dicts/fetch-data {:dict_type (:dict_type params)}]))
                                     (when (not= 200 (:code result))
                                       (antd/error! (:msg result))))
                                   (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :dicts/update-data
                 (fn [_ [_ id params]]
                   {:api/update-dict-data [id params]}))

(rf/reg-fx :api/update-dict-data
           (fn [[id params]]
             (api/update-dict-data id params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "更新成功")
                                       (rf/dispatch [:dicts/fetch-data {:dict_type (:dict_type params)}]))
                                     (when (not= 200 (:code result))
                                       (antd/error! (:msg result))))
                                   (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :dicts/delete-data
                 (fn [_ [_ id]]
                   {:api/delete-dict-data id}))

(rf/reg-fx :api/delete-dict-data
           (fn [id]
             (api/delete-dict-data id
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (antd/success! "删除成功")
                                       (rf/dispatch [:dicts/fetch-data {}]))
                                     (when (not= 200 (:code result))
                                       (antd/error! (:msg result))))
                                   (fn [_] (antd/error! "网络错误")))))
