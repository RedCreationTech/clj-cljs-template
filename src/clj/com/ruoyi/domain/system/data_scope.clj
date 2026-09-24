(ns com.ruoyi.domain.system.data-scope
  "数据权限(若依的五种数据范围,按角色配置,多个角色取并集):

     1 全部数据            3 本部门            5 仅本人
     2 自定义部门(sys_role_dept)            4 本部门及以下

   visible-scope 算出当前用户可见的范围:{:all? true} 或 {:dept-ids #{..} :self-id ..};
   查询时用 sql-params 转成参数,配合 SQL 里的固定写法(不拼接 SQL 字符串,两种数据库通用):

     AND (:scope_all = 1 OR t.dept_id IN (:v*:scope_dept_ids) OR t.user_id = :scope_user_id)

   单条记录(详情/修改/删除)用 allows? 判断。admin 角色、或任一角色为「全部数据」时不过滤;
   没有任何启用角色的用户只能看到自己。")

(def scope-labels
  {"1" "全部数据" "2" "自定义数据" "3" "本部门数据" "4" "本部门及以下数据" "5" "仅本人数据"})

(defn descendant-dept-ids
  "dept-id 及其全部下级部门;depts 为 [{:dept_id :parent_id}]。按 parent_id 遍历,不依赖 ancestors 字段。"
  [depts dept-id]
  (let [children (group-by :parent_id depts)]
    (loop [todo (list dept-id) acc #{}]
      (cond
        (empty? todo) acc
        (contains? acc (first todo)) (recur (rest todo) acc) ; 防御脏数据里的环
        :else (let [id (first todo)]
                (recur (into (rest todo) (map :dept_id (children id))) (conj acc id)))))))

(defn- role-dept-ids
  "单个角色贡献的部门;返回 {:dept-ids #{..} :self? bool}。"
  [{:keys [data_scope role_id]} {:keys [dept-id custom-depts all-depts]}]
  (case (str data_scope)
    "2" {:dept-ids (set (custom-depts role_id))}
    "3" {:dept-ids (if dept-id #{dept-id} #{})}
    "4" {:dept-ids (if dept-id (descendant-dept-ids (all-depts) dept-id) #{})}
    {:self? true}))

(defn resolve-scope
  "纯函数:由角色与部门信息计算可见范围。
   roles        [{:role_id :role_key :data_scope}](仅启用的角色)
   ctx          {:user-id :dept-id :custom-depts (fn [role-id] -> [dept-id]) :all-depts (fn [] -> depts)}"
  [roles {:keys [user-id] :as ctx}]
  (cond
    (some #(or (= "admin" (:role_key %)) (= "1" (str (:data_scope %)))) roles) {:all? true}
    (empty? roles) {:dept-ids #{} :self-id user-id}
    :else (let [parts (map #(role-dept-ids % ctx) roles)]
            {:dept-ids (into #{} (mapcat :dept-ids) parts)
             :self-id (when (some :self? parts) user-id)})))

(defn visible-scope
  "查询当前用户的数据范围(每次实时计算,调整角色后立即生效)。"
  [query-fn user-id]
  (let [user (query-fn :find-user-by-id {:user_id user-id})
        depts (delay (query-fn :list-depts {:status nil :dept_name nil}))]
    (resolve-scope (query-fn :list-user-data-scopes {:user_id user-id})
                   {:user-id user-id
                    :dept-id (:dept_id user)
                    :custom-depts #(map :dept_id (query-fn :list-role-dept-ids {:role_id %}))
                    :all-depts (fn [] @depts)})))

(defn sql-params
  "数据范围 → SQL 参数(见命名空间说明)。IN 列表不能为空,空时放一个不存在的 ID。"
  [{:keys [all? dept-ids self-id]}]
  {:scope_all (if all? 1 0)
   :scope_dept_ids (if (seq dept-ids) (vec (sort dept-ids)) [-1])
   :scope_user_id self-id})

(def unrestricted
  "不做数据过滤的参数(内部调用、定时任务等没有当前用户的场景)。"
  (sql-params {:all? true}))

(defn allows?
  "单条记录是否在可见范围内;row 需要 :dept_id 与 :user_id。"
  [{:keys [all? dept-ids self-id]} row]
  (boolean (or all?
               (contains? dept-ids (:dept_id row))
               (and self-id (= self-id (:user_id row))))))
