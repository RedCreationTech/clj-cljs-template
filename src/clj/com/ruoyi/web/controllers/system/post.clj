(ns com.ruoyi.web.controllers.system.post
  "岗位管理控制器。"
  (:require
   [com.ruoyi.domain.system.post :as post-service]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn list-posts
  "查询岗位列表。"
  [{:keys [post-service]} request]
  (res/ok (post-service/list-posts post-service (params/query request))))

(defn get-post
  [{:keys [post-service]} request]
  (let [post-id (parse-long (get-in request [:path-params :id]))]
    (if-let [post (post-service/find-post-by-id post-service post-id)]
      (res/ok post)
      (res/fail "岗位不存在"))))

(defn create-post
  [{:keys [post-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        post-id (post-service/create-post! post-service params)]
    (res/ok (str "创建成功: " post-id))))

(defn update-post
  [{:keys [post-service]} request]
  (let [post-id (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :post_id post-id)
                   (assoc :update_by (current-user-name request)))]
    (post-service/update-post! post-service params)
    (res/ok "更新成功")))

(defn delete-post
  [{:keys [post-service]} request]
  (let [post-id (parse-long (get-in request [:path-params :id]))]
    (post-service/delete-post! post-service post-id)
    (res/ok "删除成功")))

(defn change-status
  "修改岗位状态。"
  [{:keys [post-service]} request]
  (let [post-id (parse-long (get-in request [:path-params :id]))
        status (get-in request [:body-params :status])]
    (post-service/update-post! post-service {:post_id post-id :status status :update_by (current-user-name request)})
    (res/ok "状态修改成功")))
