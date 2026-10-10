(ns com.ruoyi.frontend.events.user-detail
  "用户详情异步请求状态，独立于列表和编辑表单。")

(defn open-effects
  "清除上次详情并为每次打开分配独立请求标识。"
  [db user-id request-id]
  (let [request {:user-id user-id :request-id request-id}]
    {:db (update db :users merge {:detail-visible? true
                                  :detail-data nil
                                  :detail-loading? true
                                  :detail-error? false
                                  :detail-request request})
     :api/get-user-detail request}))

(defn close-detail
  "关闭时使在途请求失效，重新打开同一用户也不接受旧响应。"
  [db]
  (update db :users merge {:detail-visible? false
                           :detail-data nil
                           :detail-loading? false
                           :detail-error? false
                           :detail-request nil}))

(defn receive-detail
  "只有当前可见抽屉的当前请求可以写入；nil 或错用户结果按失败处理。"
  [db request user]
  (if (and (get-in db [:users :detail-visible?])
           (= request (get-in db [:users :detail-request])))
    (let [valid? (and (some? user) (= (:user-id request) (:user_id user)))]
      (update db :users merge {:detail-data (when valid? user)
                               :detail-loading? false
                               :detail-error? (not valid?)
                               :detail-request nil}))
    db))
