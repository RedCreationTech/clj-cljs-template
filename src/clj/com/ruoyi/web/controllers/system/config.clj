(ns com.ruoyi.web.controllers.system.config
  "参数配置控制器。"
  (:require
   [com.ruoyi.domain.system.config :as config-service]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn list-configs
  "查询参数列表。"
  [{:keys [config-service]} request]
  (res/ok (config-service/list-configs config-service (params/query request))))

(defn get-config
  [{:keys [config-service]} request]
  (let [config-id (parse-long (get-in request [:path-params :id]))]
    (if-let [cfg (config-service/find-config-by-id config-service config-id)]
      (res/ok cfg)
      (res/fail "配置不存在"))))

(defn create-config
  [{:keys [config-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        config-id (config-service/create-config! config-service params)]
    (res/ok (str "创建成功: " config-id))))

(defn update-config
  [{:keys [config-service]} request]
  (let [config-id (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :config_id config-id)
                   (assoc :update_by (current-user-name request)))]
    (config-service/update-config! config-service params)
    (res/ok "更新成功")))

(defn delete-config
  [{:keys [config-service]} request]
  (let [config-id (parse-long (get-in request [:path-params :id]))]
    (config-service/delete-config! config-service config-id)
    (res/ok "删除成功")))
