(ns com.ruoyi.domain.system.user
  "用户领域服务，处理用户 CRUD、密码管理与角色关联。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.paging :as paging]
   [com.ruoyi.domain.system.data-scope :as data-scope]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.security :as security]))

(defn- dept-with-children
  "按部门筛选时包含下级部门;未选部门时给一个占位 ID(IN 列表不能为空)。"
  [query-fn dept-id]
  (if dept-id
    (vec (sort (data-scope/descendant-dept-ids (query-fn :list-depts {:status nil :dept_name nil}) dept-id)))
    [-1]))

(defn list-users
  "查询用户列表，支持分页和条件筛选。params 里的 :scope_* 为数据范围(见 domain.system.data-scope/sql-params),
   不传时不做数据过滤——对外接口必须由控制器按当前用户传入。"
  [{:keys [query-fn]} params]
  (paging/paginate query-fn :list-users :count-users
                   (merge {:user_name nil :phonenumber nil :status nil :dept_id nil}
                          data-scope/unrestricted)
                   params
                   :filters-fn #(assoc % :dept_ids (dept-with-children query-fn (:dept_id %)))))

(defn find-user-by-id
  "根据ID查询用户详情，包含部门、角色、岗位信息。不含密码哈希(校验密码用 password-matches?)。"
  [{:keys [query-fn]} user-id]
  (when-let [user (query-fn :find-user-by-id {:user_id user-id})]
    (-> user
        (dissoc :password)
        (assoc :roles (query-fn :list-roles-by-user-id {:user_id user-id})
               :posts (query-fn :list-posts-by-user-id {:user_id user-id})))))

(defn password-matches?
  "校验用户当前密码(修改密码时核对旧密码)。"
  [{:keys [query-fn]} user-id raw-password]
  (let [hash (:password (query-fn :find-user-by-id {:user_id user-id}))]
    (boolean (and hash (security/verify-password raw-password hash)))))

(defn find-user-by-name
  "根据用户名查询用户（用于登录）。"
  [{:keys [query-fn]} user-name]
  (query-fn :find-user-by-name {:user_name user-name}))

(defn- ensure-unique-user-name!
  [{:keys [query-fn]} user-name current-user-id]
  (when-let [existing (and (seq user-name)
                           (query-fn :find-user-by-name {:user_name user-name}))]
    (when (not= (:user_id existing) current-user-id)
      (throw (ex-info "登录账号不能重复" {:user_name user-name})))))

(defn create-user!
  "创建新用户，自动加密密码。"
  [{:keys [query-fn db]} {:keys [password roles posts] :as params}]
  (ensure-unique-user-name! {:query-fn query-fn} (:user_name params) nil)
  (let [hashed (security/hash-password password)
        user-id (db/insert-and-get-id! query-fn db :create-user!
                                       (-> params
                                           (assoc :password hashed)
                                           (dissoc :roles :posts)))]
    ;; 关联角色
    (doseq [role-id roles]
      (query-fn :insert-user-role! {:user_id user-id :role_id role-id}))
    ;; 关联岗位
    (doseq [post-id posts]
      (query-fn :insert-user-post! {:user_id user-id :post_id post-id}))
    user-id))

(def ^:private update-defaults
  "update-user! SQL 的全部参数;没给的字段为 nil,SQL 里 COALESCE 保留原值。"
  {:dept_id nil :nick_name nil :user_type nil :email nil :phonenumber nil :sex nil
   :avatar nil :password nil :status nil :update_by "" :remark nil})

(defn update-user!
  "更新用户信息(只改给出的字段);给出 :password(明文)时重新哈希。"
  [{:keys [query-fn]} {:keys [user-id password roles posts] :as params}]
  (ensure-unique-user-name! {:query-fn query-fn} (:user_name params) user-id)
  (let [update-data (-> (merge update-defaults params)
                        (dissoc :roles :posts :user-id)
                        (assoc :user_id user-id))
        ;; 空密码视为不修改(编辑表单里密码框留空)
        update-data (assoc update-data :password (when-not (str/blank? password)
                                                   (security/hash-password password)))]
    (query-fn :update-user! update-data)
    ;; 更新角色关联
    (when roles
      (query-fn :delete-user-roles! {:user_id user-id})
      (doseq [role-id roles]
        (query-fn :insert-user-role! {:user_id user-id :role_id role-id})))
    ;; 更新岗位关联
    (when posts
      (query-fn :delete-user-posts! {:user_id user-id})
      (doseq [post-id posts]
        (query-fn :insert-user-post! {:user_id user-id :post_id post-id})))
    user-id))

(defn delete-user!
  "逻辑删除用户。"
  [{:keys [query-fn]} user-id]
  (let [user (query-fn :find-user-by-id {:user_id user-id})]
    (when (or (= 1 user-id) (= "admin" (:user_name user)))
      (throw (ex-info "admin 用户不能删除" {:user_id user-id})))
    (query-fn :delete-user! {:user_id user-id})))

(defn get-user-roles
  "获取用户角色列表。"
  [{:keys [query-fn]} user-id]
  (query-fn :list-roles-by-user-id {:user_id user-id}))

(defn update-user-roles!
  "更新用户角色（先删后插）。"
  [{:keys [query-fn]} {:keys [user-id role-ids]}]
  (query-fn :delete-user-roles! {:user_id user-id})
  (doseq [rid role-ids]
    (query-fn :insert-user-role! {:user_id user-id :role_id rid})))
