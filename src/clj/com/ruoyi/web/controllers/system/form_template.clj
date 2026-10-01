(ns com.ruoyi.web.controllers.system.form-template
  "表单模板控制器。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.system.form-template :as form-template-service]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn- ->snake
  "将查询参数键统一转为 snake_case。"
  [params]
  (reduce-kv (fn [m k v]
               (assoc m
                      (keyword (str/replace (name k) #"([a-z])([A-Z])" "$1_$2"))
                      v))
             {}
             params))

(defn list-form-templates
  "查询表单模板列表。"
  [{:keys [form-template-service]} request]
  (let [params (->snake (:query-params request))]
    (res/ok (form-template-service/list-form-templates form-template-service params))))

(defn get-form-template
  "根据ID获取表单模板。"
  [{:keys [form-template-service]} request]
  (let [id (parse-long (get-in request [:path-params :id]))]
    (if-let [template (form-template-service/find-form-template-by-id form-template-service id)]
      (res/ok template)
      (res/fail "模板不存在"))))

(defn create-form-template
  "创建表单模板。"
  [{:keys [form-template-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        id (form-template-service/create-form-template! form-template-service params)]
    (res/ok (str "创建成功: " id))))

(defn update-form-template
  "更新表单模板。"
  [{:keys [form-template-service]} request]
  (let [id (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :id id)
                   (assoc :update_by (current-user-name request)))]
    (form-template-service/update-form-template! form-template-service params)
    (res/ok "更新成功")))

(defn delete-form-template
  "删除表单模板。"
  [{:keys [form-template-service]} request]
  (let [id (parse-long (get-in request [:path-params :id]))]
    (form-template-service/delete-form-template! form-template-service id)
    (res/ok "删除成功")))
