(ns com.ruoyi.frontend.events.roles
  "角色管理事件。"
  (:require
   [clojure.string]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.menus :as menus-api]
   [com.ruoyi.frontend.api.roles :as roles-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :roles/update-query
                 (fn [db [_ k v]]
                   (assoc-in db [:roles :query-params k] v)))

(rf/reg-event-db :roles/reset-query
                 (fn [db _]
                   (assoc-in db [:roles :query-params] {})))

(rf/reg-event-db :roles/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:roles :items] items)
                         (assoc-in [:roles :total] total)
                         (assoc-in [:roles :loading?] false)))))

(rf/reg-event-fx :roles/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:roles :loading?] true)
                    :api/list-roles params}))

(rf/reg-fx :api/list-roles
           (fn [params]
             (roles-api/list-roles params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (rf/dispatch [:roles/set-list (:data result)])))
                                   (fn [_]))))

(rf/reg-event-db :roles/open-modal
                 (fn [db _]
                   (-> db
                       (assoc-in [:roles :modal-visible?] true)
                       (assoc-in [:roles :editing?] false)
                       (assoc-in [:roles :editing] nil)
                       (assoc-in [:roles :form-data] {:role_sort 0 :status "0" :data_scope "1"}))))

(rf/reg-event-db :roles/close-modal
                 (fn [db _]
                   (assoc-in db [:roles :modal-visible?] false)))

(rf/reg-event-db :roles/edit
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:roles :modal-visible?] true)
                       (assoc-in [:roles :editing?] true)
                       (assoc-in [:roles :editing] data)
                       (assoc-in [:roles :form-data] data))))

(rf/reg-event-fx :roles/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [editing (get-in db [:roles :editing])]
                     (if editing
                       {:db (assoc-in db [:roles :modal-visible?] false)
                        :api/update-role [(:role_id editing) values]}
                       {:db (assoc-in db [:roles :modal-visible?] false)
                        :api/create-role values}))))

(rf/reg-fx :api/create-role
           (fn [params]
             (roles-api/create-role params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "创建成功")
                                        (rf/dispatch [:roles/fetch {}])))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/update-role
           (fn [[id params]]
             (roles-api/update-role id params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "更新成功")
                                        (rf/dispatch [:roles/fetch {}])))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/update-role-and-refresh
           (fn [[id params]]
             (roles-api/update-role id params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "权限更新成功，正在刷新...")
                                        (rf/dispatch [:roles/fetch {}])
                                  ;; 刷新页面以更新菜单
                                        (js/setTimeout #(.reload js/location) 500)))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :roles/delete
                 (fn [_ [_ id]]
                   {:api/delete-role id}))

(rf/reg-event-fx :roles/change-status
                 (fn [_ [_ id status]]
                   {:api/change-role-status [id status]}))

(rf/reg-fx :api/delete-role
           (fn [id]
             (roles-api/delete-role id
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "删除成功")
                                        (rf/dispatch [:roles/fetch {}])))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/change-role-status
           (fn [[id status]]
             (roles-api/change-role-status id status
                                           (fn [result]
                                             (when (= 200 (:code result))
                                               (antd/success! "状态修改成功")
                                               (rf/dispatch [:roles/fetch {}])))
                                           (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :roles/open-permission
                 (fn [{:keys [db]} [_ role]]
                   {:db (-> db
                            (assoc-in [:roles :permission-visible?] true)
                            (assoc-in [:roles :permission-role] role))
                    :api/fetch-role-for-permission (:role_id role)}))

(rf/reg-fx :api/fetch-role-for-permission
           (fn [role-id]
             (roles-api/get-role role-id
                                 (fn [result]
                                   (when (= 200 (:code result))
                                     (let [role (:data result)]
                                       (rf/dispatch [:roles/set-permission-role role])
                                       (rf/dispatch [:roles/set-checked-keys (mapv str (:menu-ids role []))]))))
                                 (fn [_] (antd/error! "获取角色详情失败")))))

(rf/reg-event-db :roles/set-permission-role
                 (fn [db [_ role]]
                   (assoc-in db [:roles :permission-role] role)))

(rf/reg-event-db :roles/close-permission
                 (fn [db _]
                   (assoc-in db [:roles :permission-visible?] false)))

(rf/reg-event-fx :roles/fetch-menu-tree
                 (fn [{:keys [db]} _]
                   {:db db
                    :api/menu-tree nil}))

(rf/reg-fx :api/menu-tree
           (fn [_]
             (menus-api/menu-tree
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:roles/set-menu-tree (:data result)])))
              (fn [_]))))

(rf/reg-event-db :roles/set-menu-tree
                 (fn [db [_ data]]
                   (assoc-in db [:roles :menu-tree] data)))

(rf/reg-event-db :roles/set-checked-keys
                 (fn [db [_ keys]]
                   (assoc-in db [:roles :checked-keys] keys)))

(rf/reg-event-fx :roles/save-permission
                 (fn [{:keys [db]} _]
                   (let [role-id (get-in db [:roles :permission-role :role_id])
                         menu-ids (get-in db [:roles :checked-keys] [])
                         menu-ids-int (mapv (fn [x] (if (string? x) (parse-long x) x)) menu-ids)]
                     {:db (assoc-in db [:roles :permission-visible?] false)
                      :api/update-role-and-refresh [role-id {:role_id role-id :menu-ids menu-ids-int}]})))

(rf/reg-event-fx :roles/open-data-scope
                 (fn [{:keys [db]} [_ role]]
                   {:db (-> db
                            (assoc-in [:roles :data-scope-visible?] true)
                            (assoc-in [:roles :data-scope-role] role)
                            (assoc-in [:roles :data-scope] (or (:data_scope role) "1"))
                            (assoc-in [:roles :data-scope-checked-keys] []))
                    :api/fetch-role-dept-tree (:role_id role)}))

(rf/reg-event-db :roles/close-data-scope
                 (fn [db _]
                   (assoc-in db [:roles :data-scope-visible?] false)))

(rf/reg-event-db :roles/set-data-scope
                 (fn [db [_ data-scope]]
                   (assoc-in db [:roles :data-scope] data-scope)))

(rf/reg-event-db :roles/set-data-scope-checked-keys
                 (fn [db [_ keys]]
                   (assoc-in db [:roles :data-scope-checked-keys] keys)))

(rf/reg-event-db :roles/set-dept-tree-and-keys
                 (fn [db [_ result]]
                   (-> db
                       (assoc-in [:roles :data-scope-dept-tree] (:depts result []))
                       (assoc-in [:roles :data-scope-checked-keys] (mapv str (:checked-keys result []))))))

(rf/reg-fx :api/fetch-role-dept-tree
           (fn [role-id]
             (roles-api/get-role-dept-tree role-id
                                           (fn [result]
                                             (when (= 200 (:code result))
                                               (rf/dispatch [:roles/set-dept-tree-and-keys (:data result)])))
                                           (fn [_] (antd/error! "获取部门树失败")))))

(rf/reg-event-fx :roles/save-data-scope
                 (fn [{:keys [db]} _]
                   (let [role-id (get-in db [:roles :data-scope-role :role_id])
                         data-scope (get-in db [:roles :data-scope] "1")
                         dept-ids (get-in db [:roles :data-scope-checked-keys] [])]
                     {:db (assoc-in db [:roles :data-scope-visible?] false)
                      :api/save-data-scope {:role_id role-id
                                            :data_scope data-scope
                                            :dept_ids (clojure.string/join "," dept-ids)}})))

(rf/reg-fx :api/save-data-scope
           (fn [params]
             (roles-api/set-role-data-scope params
                                            (fn [result]
                                              (when (= 200 (:code result))
                                                (antd/success! "数据权限设置成功")
                                                (rf/dispatch [:roles/fetch {}])))
                                            (fn [_] (antd/error! "设置失败")))))

(rf/reg-event-fx :roles/open-user-alloc
                 (fn [{:keys [db]} [_ role]]
                   {:db (-> db
                            (assoc-in [:roles :user-alloc-visible?] true)
                            (assoc-in [:roles :user-alloc-role] role)
                            (assoc-in [:roles :user-alloc-active-tab] "allocated")
                            (assoc-in [:roles :allocated-query] {})
                            (assoc-in [:roles :unallocated-query] {})
                            (assoc-in [:roles :allocated-selected] [])
                            (assoc-in [:roles :unallocated-selected] [])
                            (assoc-in [:roles :allocated-items] [])
                            (assoc-in [:roles :unallocated-items] [])
                            (assoc-in [:roles :allocated-total] 0)
                            (assoc-in [:roles :unallocated-total] 0))
                    :api/list-role-allocated-users {:role_id (:role_id role)}}))

(rf/reg-event-db :roles/close-user-alloc
                 (fn [db _]
                   (assoc-in db [:roles :user-alloc-visible?] false)))

(rf/reg-event-db :roles/set-user-alloc-active-tab
                 (fn [db [_ tab]]
                   (assoc-in db [:roles :user-alloc-active-tab] tab)))

(rf/reg-event-db :roles/set-allocated-query
                 (fn [db [_ k v]]
                   (assoc-in db [:roles :allocated-query k] v)))

(rf/reg-event-db :roles/reset-allocated-query
                 (fn [db _]
                   (assoc-in db [:roles :allocated-query] {})))

(rf/reg-event-fx :roles/fetch-allocated
                 (fn [{:keys [db]} _]
                   (let [role (get-in db [:roles :user-alloc-role])
                         query (get-in db [:roles :allocated-query] {})]
                     {:db (assoc-in db [:roles :allocated-loading?] true)
                      :api/list-role-allocated-users (merge {:role_id (:role_id role)} query)})))

(rf/reg-event-db :roles/set-allocated-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:roles :allocated-items] items)
                         (assoc-in [:roles :allocated-total] total)
                         (assoc-in [:roles :allocated-loading?] false)))))

(rf/reg-event-db :roles/set-allocated-selected
                 (fn [db [_ keys]]
                   (assoc-in db [:roles :allocated-selected] keys)))

(rf/reg-event-db :roles/set-unallocated-query
                 (fn [db [_ k v]]
                   (assoc-in db [:roles :unallocated-query k] v)))

(rf/reg-event-db :roles/reset-unallocated-query
                 (fn [db _]
                   (assoc-in db [:roles :unallocated-query] {})))

(rf/reg-event-fx :roles/fetch-unallocated
                 (fn [{:keys [db]} _]
                   (let [role (get-in db [:roles :user-alloc-role])
                         query (get-in db [:roles :unallocated-query] {})]
                     {:db (assoc-in db [:roles :unallocated-loading?] true)
                      :api/list-role-unallocated-users (merge {:role_id (:role_id role)} query)})))

(rf/reg-event-db :roles/set-unallocated-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:roles :unallocated-items] items)
                         (assoc-in [:roles :unallocated-total] total)
                         (assoc-in [:roles :unallocated-loading?] false)))))

(rf/reg-event-db :roles/set-unallocated-selected
                 (fn [db [_ keys]]
                   (assoc-in db [:roles :unallocated-selected] keys)))

(rf/reg-fx :api/list-role-allocated-users
           (fn [params]
             (roles-api/list-role-allocated-users
              params
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:roles/set-allocated-list (:data result)])))
              (fn [_] (rf/dispatch [:roles/set-allocated-list []])))))

(rf/reg-fx :api/list-role-unallocated-users
           (fn [params]
             (roles-api/list-role-unallocated-users
              params
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:roles/set-unallocated-list (:data result)])))
              (fn [_] (rf/dispatch [:roles/set-unallocated-list []])))))

(rf/reg-event-fx :roles/cancel-user
                 (fn [{:keys [db]} [_ user-id]]
                   (let [role (get-in db [:roles :user-alloc-role])]
                     {:api/cancel-role-auth-user {:role_id (:role_id role) :user_id user-id}})))

(rf/reg-fx :api/cancel-role-auth-user
           (fn [params]
             (roles-api/cancel-role-auth-user
              params
              (fn [result]
                (when (= 200 (:code result))
                  (antd/success! "取消授权成功")
                  (rf/dispatch [:roles/fetch-allocated])))
              (fn [_] (antd/error! "取消授权失败")))))

(rf/reg-event-fx :roles/cancel-all-users
                 (fn [{:keys [db]} _]
                   (let [role (get-in db [:roles :user-alloc-role])
                         ids (get-in db [:roles :allocated-selected] [])]
                     (if (seq ids)
                       {:api/cancel-role-auth-user-all {:role_id (:role_id role)
                                                        :user_ids (clojure.string/join "," ids)}}
                       (do (antd/warning! "请选择要取消授权的用户")
                           {:db db})))))

(rf/reg-fx :api/cancel-role-auth-user-all
           (fn [params]
             (roles-api/cancel-role-auth-user-all
              params
              (fn [result]
                (when (= 200 (:code result))
                  (antd/success! "批量取消授权成功")
                  (rf/dispatch [:roles/fetch-allocated])
                  (rf/dispatch [:roles/set-allocated-selected []])))
              (fn [_] (antd/error! "批量取消授权失败")))))

(rf/reg-event-fx :roles/select-all-users
                 (fn [{:keys [db]} _]
                   (let [role (get-in db [:roles :user-alloc-role])
                         ids (get-in db [:roles :unallocated-selected] [])]
                     (if (seq ids)
                       {:api/select-role-auth-user-all {:role_id (:role_id role)
                                                        :user_ids (clojure.string/join "," ids)}}
                       (do (antd/warning! "请选择要授权的用户")
                           {:db db})))))

(rf/reg-fx :api/select-role-auth-user-all
           (fn [params]
             (roles-api/select-role-auth-user-all
              params
              (fn [result]
                (when (= 200 (:code result))
                  (antd/success! "批量授权成功")
                  (rf/dispatch [:roles/fetch-unallocated])
                  (rf/dispatch [:roles/set-unallocated-selected []])))
              (fn [_] (antd/error! "批量授权失败")))))
