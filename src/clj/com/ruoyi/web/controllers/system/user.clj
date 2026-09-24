(ns com.ruoyi.web.controllers.system.user
  "用户管理控制器。按钮权限由路由的 :perms 拦截;数据权限(能看/能改哪些用户)在这里按
   domain.system.data-scope 处理:列表按范围过滤,单条操作先检查目标用户与目标部门是否在范围内。
   导入导出见 controllers.system.import-export。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.system.data-scope :as data-scope]
   [com.ruoyi.domain.system.user :as user-service]
   [ring.util.response :as response]))

(defn- ok
  ([data] (ok 200 "操作成功" data))
  ([code msg data]
   (-> (response/response {:code code :msg msg :data data})
       (response/content-type "application/json"))))

(defn- fail [msg]
  (-> (response/response {:code 500 :msg msg})
      (response/content-type "application/json")))

(defn- out-of-scope []
  (-> (response/response {:code 403 :msg "没有权限访问该用户的数据"})
      (response/status 403)
      (response/content-type "application/json")))

(defn- parse-int [v]
  (when-not (str/blank? (str v)) (parse-long (str v))))

(defn request-scope
  "当前请求用户的数据范围。"
  [user-service request]
  (data-scope/visible-scope (:query-fn user-service) (get-in request [:identity :user-id])))

(defn list-params
  "查询参数(字符串键)→ user-service/list-users 的参数:分页、筛选条件与数据范围。"
  [user-service request]
  (let [raw (:query-params request)]
    (merge {:page-num (or (parse-int (get raw "page")) 1)
            :page-size (or (parse-int (get raw "size")) 10)
            :user_name (not-empty (get raw "user_name"))
            :phonenumber (not-empty (get raw "phonenumber"))
            :status (not-empty (get raw "status"))
            :dept_id (parse-int (get raw "dept_id"))}
           (data-scope/sql-params (request-scope user-service request)))))

(defn- with-user-in-scope
  "目标用户存在且在数据范围内时调用 (f user),否则返回 403 / 不存在。
   extra-dept:本次操作要写入的部门(修改时),也必须在范围内。"
  ([user-service request user-id f] (with-user-in-scope user-service request user-id nil f))
  ([user-service request user-id extra-dept f]
   (let [scope (request-scope user-service request)
         user (user-service/find-user-by-id user-service user-id)]
     (cond
       (nil? user) (fail "用户不存在")
       (not (data-scope/allows? scope user)) (out-of-scope)
       (and extra-dept (not (data-scope/allows? scope {:dept_id extra-dept}))) (out-of-scope)
       :else (f user)))))

(defn- path-id [request]
  (parse-long (get-in request [:path-params :id])))

(defn- operator [request]
  (get-in request [:identity :user-name] ""))

(defn list-users
  "查询用户列表(按数据范围过滤;选中部门时包含其下级部门)。"
  [{:keys [user-service]} request]
  (let [result (user-service/list-users user-service (list-params user-service request))]
    (ok {:total (:total result) :rows (:rows result)})))

(defn get-user
  "获取用户详情。"
  [{:keys [user-service]} request]
  (with-user-in-scope user-service request (path-id request) ok))

(defn- validate-new-user [params]
  (cond
    (str/blank? (:user_name params)) "用户名不能为空"
    (str/blank? (:nick_name params)) "用户昵称不能为空"
    (str/blank? (:password params)) "密码不能为空"
    :else nil))

(defn create-user
  "创建用户;所属部门必须在当前用户的数据范围内。"
  [{:keys [user-service]} request]
  (let [params (merge {:dept_id 1 :user_type "00" :sex "0" :status "0"
                       :email "" :phonenumber "" :avatar "" :remark ""
                       :create_by (operator request) :roles [] :posts []}
                      (:body-params request))]
    (cond
      (validate-new-user params) (fail (validate-new-user params))
      (not (data-scope/allows? (request-scope user-service request) params)) (out-of-scope)
      :else (try
              (ok (str "创建成功: " (user-service/create-user! user-service params)))
              (catch Exception e (fail (.getMessage e)))))))

(defn update-user
  "更新用户(只改提交的字段,密码为空表示不修改)。"
  [{:keys [user-service]} request]
  (let [user-id (path-id request)
        body (:body-params request)]
    (with-user-in-scope user-service request user-id (:dept_id body)
      (fn [_]
        (try
          (user-service/update-user! user-service
                                     (cond-> (merge (dissoc body :roles :posts :user_name)
                                                    {:user-id user-id :update_by (operator request)})
                                       (:roles body) (assoc :roles (:roles body))
                                       (:posts body) (assoc :posts (:posts body))))
          (ok "更新成功")
          (catch Exception e (fail (.getMessage e))))))))

(defn delete-user
  "删除用户。"
  [{:keys [user-service]} request]
  (let [user-id (path-id request)]
    (with-user-in-scope user-service request user-id
      (fn [_]
        (try
          (user-service/delete-user! user-service user-id)
          (ok "删除成功")
          (catch Exception e (fail (.getMessage e))))))))

(defn change-status
  "修改用户状态。"
  [{:keys [user-service]} request]
  (let [user-id (path-id request)]
    (with-user-in-scope user-service request user-id
      (fn [_]
        (user-service/update-user! user-service {:user-id user-id
                                                 :status (get-in request [:path-params :status])
                                                 :update_by (operator request)})
        (ok "状态修改成功")))))

(defn reset-password
  "重置用户密码(未提交新密码时重置为 123456)。"
  [{:keys [user-service]} request]
  (let [user-id (path-id request)
        password (get-in request [:body-params :password])]
    (with-user-in-scope user-service request user-id
      (fn [_]
        (user-service/update-user! user-service {:user-id user-id
                                                 :password (if (str/blank? password) "123456" password)
                                                 :update_by (operator request)})
        (ok "密码重置成功")))))

(defn auth-role
  "获取用户角色列表。"
  [{:keys [user-service]} request]
  (let [user-id (path-id request)]
    (with-user-in-scope user-service request user-id
      (fn [_] (ok (user-service/get-user-roles user-service user-id))))))

(defn update-auth-role
  "分配用户角色。"
  [{:keys [user-service]} request]
  (let [user-id (path-id request)
        role-ids (get-in request [:body-params :role_ids])]
    (with-user-in-scope user-service request user-id
      (fn [_]
        (user-service/update-user-roles! user-service {:user-id user-id :role-ids role-ids})
        (ok "角色分配成功")))))
