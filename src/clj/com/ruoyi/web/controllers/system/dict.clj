(ns com.ruoyi.web.controllers.system.dict
  "字典管理控制器。"
  (:require
   [com.ruoyi.domain.system.dict :as dict-service]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn list-dict-types
  "查询字典类型列表。"
  [{:keys [dict-service]} request]
  (res/ok (dict-service/list-dict-types dict-service (params/query request))))

(defn get-dict-type
  [{:keys [dict-service]} request]
  (let [dict-id (parse-long (get-in request [:path-params :id]))]
    (if-let [dt (dict-service/find-dict-type-by-id dict-service dict-id)]
      (res/ok dt)
      (res/fail "字典类型不存在"))))

(defn create-dict-type
  [{:keys [dict-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        dict-id (dict-service/create-dict-type! dict-service params)]
    (res/ok (str "创建成功: " dict-id))))

(defn update-dict-type
  [{:keys [dict-service]} request]
  (let [dict-id (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :dict_id dict-id)
                   (assoc :update_by (current-user-name request)))]
    (dict-service/update-dict-type! dict-service params)
    (res/ok "更新成功")))

(defn delete-dict-type
  [{:keys [dict-service]} request]
  (let [dict-id (parse-long (get-in request [:path-params :id]))]
    (dict-service/delete-dict-type! dict-service dict-id)
    (res/ok "删除成功")))

(defn list-dict-data
  "查询字典数据列表。"
  [{:keys [dict-service]} request]
  (res/ok (dict-service/list-dict-data dict-service (params/query request))))

(defn get-dict-data
  [{:keys [dict-service]} request]
  (let [dict-code (parse-long (get-in request [:path-params :id]))]
    (if-let [dd (dict-service/find-dict-data-by-id dict-service dict-code)]
      (res/ok dd)
      (res/fail "字典数据不存在"))))

(defn create-dict-data
  [{:keys [dict-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        dict-code (dict-service/create-dict-data! dict-service params)]
    (res/ok (str "创建成功: " dict-code))))

(defn update-dict-data
  [{:keys [dict-service]} request]
  (let [dict-code (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :dict_code dict-code)
                   (assoc :update_by (current-user-name request)))]
    (dict-service/update-dict-data! dict-service params)
    (res/ok "更新成功")))

(defn delete-dict-data
  [{:keys [dict-service]} request]
  (let [dict-code (parse-long (get-in request [:path-params :id]))]
    (dict-service/delete-dict-data! dict-service dict-code)
    (res/ok "删除成功")))

(defn option-select
  "获取字典类型选项列表（下拉框用）。"
  [{:keys [dict-service]} _]
  (res/ok (dict-service/list-dict-types dict-service {:limit 999 :offset 0})))

(defn refresh-cache
  "刷新字典缓存。"
  [_ _]
  (res/ok "缓存已刷新"))
