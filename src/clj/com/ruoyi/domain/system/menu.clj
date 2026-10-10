(ns com.ruoyi.domain.system.menu
  "菜单领域服务，处理菜单树构建与 CRUD。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.errors :as errors]))

(defn list-menus
  "查询菜单列表。"
  [{:keys [query-fn]} params]
  (query-fn :list-menus (merge {:menu_name nil :status nil :menu_type nil} params)))

(defn find-menu-by-id
  "根据ID查询菜单。"
  [{:keys [query-fn]} menu-id]
  (query-fn :find-menu-by-id {:menu_id menu-id}))

(defn- integer-field [field value]
  (when (some? value)
    (let [number (cond
                   (integer? value) value
                   (string? value) (parse-long (str/trim value))
                   :else nil)]
      (when-not (and (some? number) (<= Long/MIN_VALUE number Long/MAX_VALUE))
        (errors/fail! (str "菜单字段 " (name field) " 必须是整数")))
      (when (and (#{:is_frame :is_cache} field) (not (#{0 1} number)))
        (errors/fail! (str "菜单字段 " (name field) " 必须是 0 或 1")))
      (long number))))

(defn- normalize-integers [params]
  (reduce (fn [result field]
            (if (contains? params field)
              (assoc result field (integer-field field (get params field)))
              result))
          params [:menu_id :parent_id :order_num :is_frame :is_cache]))

(defn create-menu!
  "创建菜单。"
  [{:keys [query-fn db]} params]
  (db/insert-and-get-id! query-fn db :create-menu!
                         (merge {:menu_name nil :parent_id 0 :order_num nil :path nil
                                 :component nil :query nil :route_name nil :is_frame nil
                                 :is_cache nil :menu_type nil :visible nil :status nil
                                 :perms nil :icon nil :create_by nil}
                                (normalize-integers (update params :parent_id #(or % 0))))))

(defn update-menu!
  "更新菜单。"
  [{:keys [query-fn]} params]
  (query-fn :update-menu! (merge {:menu_name nil :parent_id nil :order_num nil :path nil
                                  :component nil :query nil :route_name nil :is_frame nil
                                  :is_cache nil :menu_type nil :visible nil :status nil
                                  :perms nil :icon nil :create_by nil :create_time nil :update_by nil}
                                 (normalize-integers params))))

(defn delete-menu!
  "删除菜单。"
  [{:keys [query-fn]} menu-id]
  (query-fn :delete-menu! {:menu_id menu-id}))

(defn- build-tree
  "将扁平菜单列表构建为树形结构。"
  [items parent-id]
  (let [children (filter #(= (:parent_id %) parent-id) items)]
    (mapv (fn [child]
            (assoc child :children (build-tree items (:menu_id child))))
          children)))

(defn menu-tree
  "获取菜单树。"
  [{:keys [query-fn]}]
  (let [menus (query-fn :list-menus {:menu_name nil :status nil :menu_type nil})]
    (build-tree menus 0)))

(defn menu-tree-by-roles
  "根据角色ID列表构建菜单树。"
  [{:keys [query-fn]} role-ids]
  (if (seq role-ids)
    (let [menus (query-fn :list-menus-by-role-ids {:role-ids role-ids})]
      (build-tree menus 0))
    []))
