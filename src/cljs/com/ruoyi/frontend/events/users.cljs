(ns com.ruoyi.frontend.events.users
  "用户管理事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.impexp :as impexp-api]
   [com.ruoyi.frontend.api.posts :as posts-api]
   [com.ruoyi.frontend.api.roles :as roles-api]
   [com.ruoyi.frontend.api.users :as users-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :users/set-list
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:users :items] (:rows data))
                       (assoc-in [:users :total] (:total data))
                       (assoc-in [:users :loading?] false))))

(rf/reg-event-fx :users/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:users :loading?] true)
                    :api/list-users params}))

(rf/reg-event-db :users/open-import
                 (fn [db _]
                   (-> db
                       (assoc-in [:users :import-visible?] true)
                       (assoc-in [:users :import-file] nil))))

(rf/reg-event-db :users/close-import
                 (fn [db _]
                   (assoc-in db [:users :import-visible?] false)))

(rf/reg-event-db :users/set-import-file
                 (fn [db [_ file]]
                   (assoc-in db [:users :import-file] file)))

(rf/reg-event-db :users/set-import-loading
                 (fn [db [_ loading?]]
                   (assoc-in db [:users :import-loading?] loading?)))

(rf/reg-event-fx :users/import
                 (fn [{:keys [db]} _]
                   (let [file (get-in db [:users :import-file])]
                     (if file
                       {:db (assoc-in db [:users :import-loading?] true)
                        :api/import-users file}
                       {:db db}))))

(rf/reg-fx :api/import-users
           (fn [file]
             (impexp-api/import-users-csv file
                                          (fn [r]
                                            (rf/dispatch [:users/set-import-loading false])
                                            (when (= 200 (:code r))
                                              (antd/success! (str "导入完成：成功 " (:success (:data r)) " 条，失败 " (:failed (:data r)) " 条"))
                                              (rf/dispatch [:users/close-import])
                                              (rf/dispatch [:users/fetch {}])))
                                          (fn [_]
                                            (rf/dispatch [:users/set-import-loading false])
                                            (antd/error! "导入失败")))))

(rf/reg-event-fx :users/export
                 (fn [{:keys [db]} _]
                   (let [params (get-in db [:users :query-params] {})
                         ids (get-in db [:users :selected-ids] [])
                         params (cond-> params
                                  (seq ids) (assoc :ids (.join (clj->js ids) ",")))]
                     {:db db :api/export-users params})))

(rf/reg-fx :api/export-users
           (fn [params]
             (impexp-api/export-users-csv
              params
              (fn [csv-data]
                (let [blob (js/Blob. #js [csv-data] #js {:type "text/csv;charset=utf-8"})
                      url (js/URL.createObjectURL blob)
                      link (.createElement js/document "a")]
                  (set! (.-href link) url)
                  (.setAttribute link "download" "users_export.csv")
                  (.appendChild js/document.body link)
                  (.click link)
                  (.removeChild js/document.body link)
                  (js/URL.revokeObjectURL url)))
              (fn [_] (antd/error! "导出失败")))))

(rf/reg-event-db :users/open-add
                 (fn [db _]
                   (-> db
                       (assoc-in [:users :modal-visible?] true)
                       (assoc-in [:users :editing] nil)
                       (assoc-in [:users :form-data] {}))))

(rf/reg-event-fx :users/open-edit
                 (fn [_ [_ user-id]]
                   {:api/get-user user-id}))

(rf/reg-event-db :users/edit-user
                 (fn [db [_ user]]
                   (-> db
                       (assoc-in [:users :modal-visible?] true)
                       (assoc-in [:users :editing] user)
                       (assoc-in [:users :form-data] (or user {})))))

(rf/reg-event-fx :users/open-edit-selected
                 (fn [{:keys [db]} _]
                   (let [ids (get-in db [:users :selected-ids] [])]
                     (if (seq ids)
                       {:api/get-user (first ids)}
                       (do (antd/error! "请先选择要修改的用户")
                           {:db db})))))

(rf/reg-event-db :users/close-modal
                 (fn [db _]
                   (assoc-in db [:users :modal-visible?] false)))

(rf/reg-event-db :users/update-query
                 (fn [db [_ field value]]
                   (assoc-in db [:users :query-params field] value)))

(rf/reg-event-fx :users/search
                 (fn [{:keys [db]} _]
                   (let [params (get-in db [:users :query-params] {})
                         size (get-in db [:users :page-size] 10)]
                     {:db (assoc-in db [:users :page] 1)
                      :api/list-users (merge params {:page 1 :size size})})))

(rf/reg-event-fx :users/reset-query
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:users :query-params] {})
                            (assoc-in [:users :selected-ids] [])
                            (assoc-in [:users :page] 1)
                            (assoc-in [:users :page-size] 10))
                    :api/list-users {:page 1 :size 10}}))

(rf/reg-event-fx :users/fetch-with-params
                 (fn [{:keys [db]} _]
                   (let [params (get-in db [:users :query-params] {})
                         page (get-in db [:users :page] 1)
                         size (get-in db [:users :page-size] 10)]
                     {:api/list-users (merge params {:page page :size size})})))

(rf/reg-event-db :users/toggle-search
                 (fn [db _]
                   (update-in db [:users :show-search?] not)))

(rf/reg-event-db :users/toggle-column
                 (fn [db [_ col-key]]
                   (update-in db [:users :columns col-key :visible?] not)))

(rf/reg-event-fx :users/create
                 (fn [{:keys [db]} [_ params]]
                   {:api/create-user params}))

(rf/reg-event-fx :users/update
                 (fn [{:keys [db]} [_ id params]]
                   {:api/update-user [id params]}))

(rf/reg-event-fx :users/delete
                 (fn [{:keys [db]} [_ id]]
                   (let [user (first (filter #(= id (:user_id %)) (get-in db [:users :items] [])))]
                     (if (or (= 1 id) (= "admin" (:user_name user)))
                       (do (antd/error! "admin 用户不能删除")
                           {:db db})
                       {:api/delete-user id}))))

(rf/reg-event-fx :users/batch-delete
                 (fn [{:keys [db]} _]
                   (let [ids (get-in db [:users :selected-ids] [])
                         items (get-in db [:users :items] [])
                         admin-ids (->> items
                                        (filter #(= "admin" (:user_name %)))
                                        (map :user_id)
                                        set)]
                     (cond
                       (empty? ids)
                       (do (antd/error! "请先选择要删除的用户") {})

                       (some admin-ids ids)
                       (do (antd/error! "admin 用户不能删除") {:db db})

                       :else
                       {:api/batch-delete-users ids}))))

(rf/reg-event-db :users/toggle-select
                 (fn [db [_ id]]
                   (let [ids (get-in db [:users :selected-ids] [])]
                     (assoc-in db [:users :selected-ids]
                               (if (some #{id} ids)
                                 (filterv #(not= id %) ids)
                                 (conj ids id))))))

(rf/reg-event-db :users/toggle-select-all
                 (fn [db [_ selected?]]
                   (if selected?
                     (assoc-in db [:users :selected-ids] (mapv :user_id (get-in db [:users :items] [])))
                     (assoc-in db [:users :selected-ids] []))))

(rf/reg-event-fx :users/change-status
                 (fn [{:keys [db]} [_ user-id status]]
                   {:api/change-user-status [user-id status]}))

(rf/reg-event-fx :users/reset-password
                 (fn [{:keys [db]} [_ user-id]]
                   {:db (-> db
                            (assoc-in [:users :reset-pwd-visible?] true)
                            (assoc-in [:users :reset-pwd-username] user-id)
                            (assoc-in [:users :reset-pwd-value] "123456"))}))

(rf/reg-event-fx :users/submit-reset-password
                 (fn [{:keys [db]} [_ values]]
                   (let [user-id (get-in db [:users :reset-pwd-username])
                         new-pwd (:password values "123456")]
                     {:db (assoc-in db [:users :reset-pwd-visible?] false)
                      :api/reset-user-password [user-id new-pwd]})))

(rf/reg-event-db :users/view-detail
                 (fn [db [_ user-id]]
                   (let [items (get-in db [:users :items] [])
                         user (first (filter #(= user-id (:user_id %)) items))]
                     (-> db
                         (assoc-in [:users :detail-visible?] true)
                         (assoc-in [:users :detail-data] user)))))

(rf/reg-event-db :users/close-detail
                 (fn [db _]
                   (assoc-in db [:users :detail-visible?] false)))

(rf/reg-event-fx :users/auth-role
                 (fn [{:keys [db]} [_ user-id]]
                   {:db (-> db
                            (assoc-in [:users :auth-role-visible?] true)
                            (assoc-in [:users :auth-role-user]
                                      (first (filter #(= user-id (:user_id %))
                                                     (get-in db [:users :items] []))))
                            (assoc-in [:users :auth-role-ids] []))
                    :api/get-user-roles user-id
                    :api/list-role-options nil}))

(rf/reg-fx :api/list-users
           (fn [params]
             (users-api/list-users params
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (rf/dispatch [:users/set-list (:data result)])))
                                   (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/create-user
           (fn [params]
             (users-api/create-user params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "创建成功")
                                        (rf/dispatch [:users/close-modal])
                                        (rf/dispatch [:users/fetch {}]))
                                      (when (not= 200 (:code result))
                                        (antd/error! (:msg result))))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/update-user
           (fn [[id params]]
             (users-api/update-user id params
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "更新成功")
                                        (rf/dispatch [:users/close-modal])
                                        (rf/dispatch [:users/fetch {}]))
                                      (when (not= 200 (:code result))
                                        (antd/error! (:msg result))))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/delete-user
           (fn [id]
             (users-api/delete-user id
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "删除成功")
                                        (rf/dispatch [:users/fetch {}]))
                                      (when (not= 200 (:code result))
                                        (antd/error! (:msg result))))
                                    (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/get-user
           (fn [user-id]
             (users-api/get-user user-id
                                 (fn [result]
                                   (when (= 200 (:code result))
                                     (rf/dispatch [:users/edit-user (:data result)])))
                                 (fn [_] (antd/error! "获取用户详情失败")))))

(rf/reg-fx :api/batch-delete-users
           (fn [ids]
             (doseq [id ids]
               (users-api/delete-user id
                                      (fn [result]
                                        (when (= 200 (:code result))
                                          (antd/success! "删除成功")))
                                      (fn [_] (antd/error! "网络错误"))))
             (rf/dispatch [:users/fetch {}])))

(rf/reg-fx :api/change-user-status
           (fn [[user-id status]]
             (users-api/change-user-status user-id status
                                           (fn [result]
                                             (when (= 200 (:code result))
                                               (antd/success! "状态修改成功")
                                               (rf/dispatch [:users/fetch {}])))
                                           (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/reset-user-password
           (fn [[user-id new-pwd]]
             (users-api/reset-user-password user-id new-pwd
                                            (fn [result]
                                              (when (= 200 (:code result))
                                                (antd/success! "密码重置成功")))
                                            (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/get-user-roles
           (fn [user-id]
             (users-api/get-user-roles user-id
                                       (fn [result]
                                         (when (= 200 (:code result))
                                           (rf/dispatch [:users/set-auth-role-ids (:data result)])))
                                       (fn [_] (antd/error! "获取用户角色失败")))))

(rf/reg-fx :api/update-user-roles
           (fn [[user-id role-ids]]
             (users-api/update-user-roles user-id role-ids
                                          (fn [result]
                                            (when (= 200 (:code result))
                                              (antd/success! "角色分配成功")
                                              (rf/dispatch [:users/close-auth-role])
                                              (rf/dispatch [:users/fetch-with-params]))
                                            (when (not= 200 (:code result))
                                              (antd/error! (:msg result))))
                                          (fn [_] (antd/error! "角色分配失败")))))

(rf/reg-event-fx :users/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [editing (get-in db [:users :editing])]
                     (if editing
                       {:api/update-user [(:user_id editing) values]}
                       {:api/create-user values}))))

(rf/reg-event-db :users/close-reset-password
                 (fn [db _]
                   (assoc-in db [:users :reset-pwd-visible?] false)))

(rf/reg-event-db :users/close-auth-role
                 (fn [db _]
                   (assoc-in db [:users :auth-role-visible?] false)))

(rf/reg-event-db :users/set-auth-role-ids
                 (fn [db [_ roles]]
                   (assoc-in db [:users :auth-role-ids] (mapv :role_id roles))))

(rf/reg-event-db :users/set-auth-role-selection
                 (fn [db [_ role-ids]]
                   (assoc-in db [:users :auth-role-ids] (mapv #(js/parseInt % 10) role-ids))))

(rf/reg-event-fx :users/submit-auth-role
                 (fn [{:keys [db]} _]
                   (let [user-id (:user_id (get-in db [:users :auth-role-user]))
                         role-ids (get-in db [:users :auth-role-ids] [])]
                     {:api/update-user-roles [user-id role-ids]})))

(rf/reg-event-db :users/select-dept
                 (fn [db [_ dept-id]]
                   (assoc-in db [:users :selected-dept-id] dept-id)))

(rf/reg-event-fx :users/change-page
                 (fn [{:keys [db]} [_ page page-size]]
                   {:db (-> db
                            (assoc-in [:users :page] page)
                            (assoc-in [:users :page-size] page-size))
                    :dispatch [:users/fetch-with-params]}))

(rf/reg-event-db :users/set-selected
                 (fn [db [_ ids]]
                   (assoc-in db [:users :selected-ids] (mapv #(js/parseInt % 10) ids))))

(rf/reg-event-db :users/set-role-options
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))]
                     (assoc-in db [:users :role-options] items))))

(rf/reg-event-db :users/set-post-options
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))]
                     (assoc-in db [:users :post-options] items))))

(rf/reg-event-fx :users/fetch-options
                 (fn [_ _]
                   {:api/list-role-options nil
                    :api/list-post-options nil}))

(rf/reg-fx :api/list-role-options
           (fn [_]
             (roles-api/list-roles {:page 1 :size 1000}
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (rf/dispatch [:users/set-role-options (:data result)])))
                                   (fn [_]))))

(rf/reg-fx :api/list-post-options
           (fn [_]
             (posts-api/list-posts {:page 1 :size 1000}
                                   (fn [result]
                                     (when (= 200 (:code result))
                                       (rf/dispatch [:users/set-post-options (:data result)])))
                                   (fn [_]))))

(rf/reg-event-db :users/toggle-dept-expand
                 (fn [db [_ dept-id]]
                   (let [expanded (get-in db [:users :expanded-dept-ids] #{})]
                     (assoc-in db [:users :expanded-dept-ids]
                               (if (contains? expanded dept-id)
                                 (disj expanded dept-id)
                                 (conj expanded dept-id))))))

(rf/reg-event-db :users/collapse-all-depts
                 (fn [db _]
                   (assoc-in db [:users :expanded-dept-ids] #{})))
