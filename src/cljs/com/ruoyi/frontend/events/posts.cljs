(ns com.ruoyi.frontend.events.posts
  "岗位管理事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api :as api]
   [re-frame.core :as rf]))

(rf/reg-event-db :posts/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db (assoc-in [:posts :items] items) (assoc-in [:posts :total] total) (assoc-in [:posts :loading?] false)))))

(rf/reg-event-db :posts/update-query
                 (fn [db [_ k v]] (assoc-in db [:posts :query-params k] v)))

(rf/reg-event-db :posts/reset-query
                 (fn [db _] (assoc-in db [:posts :query-params] {})))

(rf/reg-event-fx :posts/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:posts :loading?] true) :api/list-posts params}))

(rf/reg-fx :api/list-posts
           (fn [params]
             (api/list-posts params
                             (fn [r] (when (= 200 (:code r)) (rf/dispatch [:posts/set-list (:data r)])))
                             (fn [_]))))

(rf/reg-event-db :posts/open-modal
                 (fn [db _]
                   (-> db
                       (assoc-in [:posts :modal-visible?] true)
                       (assoc-in [:posts :editing?] false)
                       (assoc-in [:posts :editing] nil)
                       (assoc-in [:posts :form-data] {:post_sort 0 :status "0"}))))

(rf/reg-event-db :posts/close-modal
                 (fn [db _] (assoc-in db [:posts :modal-visible?] false)))

(rf/reg-event-db :posts/edit
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:posts :modal-visible?] true)
                       (assoc-in [:posts :editing?] true)
                       (assoc-in [:posts :editing] data)
                       (assoc-in [:posts :form-data] data))))

(rf/reg-event-fx :posts/submit
                 (fn [{:keys [db]} [_ values]]
                   (let [editing (get-in db [:posts :editing])]
                     (if editing
                       {:db (assoc-in db [:posts :modal-visible?] false) :api/update-post [(:post_id editing) values]}
                       {:db (assoc-in db [:posts :modal-visible?] false) :api/create-post values}))))

(rf/reg-fx :api/create-post
           (fn [params] (api/create-post params (fn [r] (when (= 200 (:code r)) (antd/success! "创建成功") (rf/dispatch [:posts/fetch {}]))) (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/update-post
           (fn [[id params]] (api/update-post id params (fn [r] (when (= 200 (:code r)) (antd/success! "更新成功") (rf/dispatch [:posts/fetch {}]))) (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :posts/delete
                 (fn [_ [_ id]] {:api/delete-post id}))

(rf/reg-event-fx :posts/change-status
                 (fn [_ [_ id status]]
                   {:api/change-post-status [id status]}))

(rf/reg-fx :api/delete-post
           (fn [id] (api/delete-post id (fn [r] (when (= 200 (:code r)) (antd/success! "删除成功") (rf/dispatch [:posts/fetch {}]))) (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/change-post-status
           (fn [[id status]]
             (api/change-post-status id status
                                     (fn [result]
                                       (when (= 200 (:code result))
                                         (antd/success! "状态修改成功")
                                         (rf/dispatch [:posts/fetch {}])))
                                     (fn [_] (antd/error! "网络错误")))))
