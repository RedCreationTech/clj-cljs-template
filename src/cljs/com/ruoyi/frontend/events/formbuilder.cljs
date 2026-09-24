(ns com.ruoyi.frontend.events.formbuilder
  "表单构建器与模板事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.form-template :as form-template-api]
   [re-frame.core :as rf]))

(let [counter (atom 0)]
  (rf/reg-event-db :fb/add-item
                   (fn [db [_ comp]]
                     (let [id (swap! counter inc)]
                       (update-in db [:fb :items] conj {:id id :type (:type comp) :props (:defaults comp)})))))

(rf/reg-event-db :fb/remove-item
                 (fn [db [_ id]]
                   (update-in db [:fb :items] #(filterv (fn [i] (not= (:id i) id)) %))))

(rf/reg-event-db :fb/select-item
                 (fn [db [_ id]]
                   (assoc-in db [:fb :selected-id] id)))

(rf/reg-event-db :fb/update-prop
                 (fn [db [_ k v]]
                   (let [id (get-in db [:fb :selected-id])]
                     (update-in db [:fb :items]
                                (fn [items] (mapv (fn [i] (if (= (:id i) id) (assoc-in i [:props k] v) i)) items))))))

(rf/reg-event-db :fb/toggle-code
                 (fn [db _]
                   (update-in db [:fb :code-visible?] not)))

(rf/reg-event-db :fb/clear
                 (fn [db _]
                   (assoc db :fb {:items [] :selected-id nil :code-visible? false})))

(rf/reg-event-db :fb/load-items
                 (fn [db [_ items]]
                   (assoc-in db [:fb :items] items)))

(rf/reg-event-db :form-template/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))]
                     (-> db
                         (assoc-in [:form-templates :items] items)
                         (assoc-in [:form-templates :loading?] false)))))

(rf/reg-event-fx :form-template/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:form-templates :loading?] true)
                    :api/list-form-templates params}))

(rf/reg-fx :api/list-form-templates
           (fn [params]
             (form-template-api/list-form-templates params
                                                    (fn [result]
                                                      (when (= 200 (:code result))
                                                        (rf/dispatch [:form-template/set-list (:data result)])))
                                                    (fn [_] (antd/error! "获取模板列表失败")))))

(rf/reg-event-db :form-template/open-save-modal
                 (fn [db _]
                   (assoc-in db [:form-templates :modal-visible?] true)))

(rf/reg-event-db :form-template/close-save-modal
                 (fn [db _]
                   (assoc-in db [:form-templates :modal-visible?] false)))

(rf/reg-event-db :form-template/open-load-drawer
                 (fn [db _]
                   (-> db
                       (assoc-in [:form-templates :drawer-visible?] true)
                       (assoc-in [:form-templates :loading?] true))))

(rf/reg-event-fx :form-template/open-load-drawer-and-fetch
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:form-templates :drawer-visible?] true)
                            (assoc-in [:form-templates :loading?] true))
                    :dispatch [:form-template/fetch {}]}))

(rf/reg-event-db :form-template/close-load-drawer
                 (fn [db _]
                   (assoc-in db [:form-templates :drawer-visible?] false)))

(rf/reg-event-fx :form-template/save
                 (fn [{:keys [db]} [_ values]]
                   (let [items (get-in db [:fb :items] [])
                         schema-json (js/JSON.stringify (clj->js items))
                         params (-> values
                                    (assoc :schema_json schema-json)
                                    (dissoc :schema-json))]
                     {:db (assoc-in db [:form-templates :saving?] true)
                      :api/save-form-template params})))

(rf/reg-fx :api/save-form-template
           (fn [params]
             (form-template-api/save-form-template params
                                                   (fn [result]
                                                     (rf/dispatch [:form-template/saved])
                                                     (when (= 200 (:code result))
                                                       (antd/success! "保存成功")
                                                       (rf/dispatch [:form-template/close-save-modal]))
                                                     (when (not= 200 (:code result))
                                                       (antd/error! (:msg result))))
                                                   (fn [_]
                                                     (rf/dispatch [:form-template/saved])
                                                     (antd/error! "保存失败")))))

(rf/reg-event-db :form-template/saved
                 (fn [db _]
                   (assoc-in db [:form-templates :saving?] false)))

(rf/reg-event-fx :form-template/load
                 (fn [_ [_ template]]
                   (let [items (js->clj (.parse js/JSON (:schema_json template "")) :keywordize-keys true)]
                     {:dispatch-n [[:fb/load-items items]
                                   [:fb/select-item nil]
                                   [:form-template/close-load-drawer]
                                   [:form-template/fetch {}]]})))

(rf/reg-event-fx :form-template/delete
                 (fn [_ [_ id]]
                   {:api/delete-form-template id}))

(rf/reg-fx :api/delete-form-template
           (fn [id]
             (form-template-api/delete-form-template id
                                                     (fn [result]
                                                       (when (= 200 (:code result))
                                                         (antd/success! "删除成功")
                                                         (rf/dispatch [:form-template/fetch {}]))
                                                       (when (not= 200 (:code result))
                                                         (antd/error! (:msg result))))
                                                     (fn [_] (antd/error! "删除失败")))))
