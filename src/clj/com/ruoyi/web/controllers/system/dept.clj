(ns com.ruoyi.web.controllers.system.dept
  "部门管理控制器。"
  (:require
   [com.ruoyi.domain.system.dept :as dept-service]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn list-depts
  "查询部门列表。"
  [{:keys [dept-service]} request]
  (res/ok (dept-service/list-depts dept-service (params/query request))))

(defn dept-tree
  "获取部门树（用于用户管理左侧选择）。"
  [{:keys [dept-service]} _request]
  (res/ok (dept-service/list-depts dept-service {})))

(defn get-dept
  [{:keys [dept-service]} request]
  (let [dept-id (parse-long (get-in request [:path-params :id]))]
    (if-let [dept (dept-service/find-dept-by-id dept-service dept-id)]
      (res/ok dept)
      (res/fail "部门不存在"))))

(defn create-dept
  [{:keys [dept-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        dept-id (dept-service/create-dept! dept-service params)]
    (res/ok (str "创建成功: " dept-id))))

(defn update-dept
  [{:keys [dept-service]} request]
  (let [dept-id (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :dept_id dept-id)
                   (assoc :update_by (current-user-name request)))]
    (dept-service/update-dept! dept-service params)
    (res/ok "更新成功")))

(defn delete-dept
  [{:keys [dept-service]} request]
  (let [dept-id (parse-long (get-in request [:path-params :id]))]
    (dept-service/delete-dept! dept-service dept-id)
    (res/ok "删除成功")))

(defn change-status
  "修改部门状态。"
  [{:keys [dept-service]} request]
  (let [dept-id (parse-long (get-in request [:path-params :id]))
        status (get-in request [:body-params :status])]
    (dept-service/update-dept! dept-service {:dept_id dept-id :status status :update_by (current-user-name request)})
    (res/ok "状态修改成功")))
