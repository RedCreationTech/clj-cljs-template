(ns tasks.scaffold.sql
  "生成两套迁移(建表 + 菜单)与 HugSQL 查询文件。所有 SQL 只用 SQLite/MySQL 的交集语法。"
  (:require
   [clojure.string :as str]
   [tasks.scaffold.model :as m]))

(defn- column-ddl [db {:keys [col type required?]}]
  (str "  " col " " (get-in m/types [type db])
       (when (= type :bool) " DEFAULT '0'")
       (when required? " NOT NULL")))

(defn- create-table [db {:keys [table fields]}]
  (let [[id-col ts-type] (case db
                           :sqlite ["id INTEGER PRIMARY KEY AUTOINCREMENT" "TEXT"]
                           :mysql ["id BIGINT AUTO_INCREMENT PRIMARY KEY" "TIMESTAMP NULL"])]
    (str "CREATE TABLE " table " (\n"
         "  " id-col ",\n"
         (str/join ",\n" (map #(column-ddl db %) fields)) ",\n"
         "  create_by VARCHAR(64) DEFAULT '',\n"
         "  create_time " ts-type " DEFAULT CURRENT_TIMESTAMP,\n"
         "  update_by VARCHAR(64) DEFAULT '',\n"
         "  update_time " ts-type " DEFAULT CURRENT_TIMESTAMP\n"
         ");")))

(defn- module-menu
  "本模块菜单(C)的查询条件:挂在 path = 'biz' 的顶级目录下,path = 模块名。"
  [module]
  (str "SELECT c.menu_id FROM sys_menu c JOIN sys_menu p ON c.parent_id = p.menu_id "
       "WHERE p.parent_id = 0 AND p.path = 'biz' AND c.path = '" module "'"))

(defn- buttons-up
  "按钮权限(F):查询/新增/修改/删除,挂在本模块菜单下并授权给 admin 角色;标识与路由的 :perms 一致。"
  [{:keys [module label perm-prefix]}]
  (let [rows (for [[i [act k]] (map-indexed vector [["查询" "query"] ["新增" "add"] ["修改" "edit"] ["删除" "remove"]])]
               (str "SELECT '" label act "' AS name, " (inc i) " AS ord, '" perm-prefix ":" k "' AS perms"))]
    [(str "INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)\n"
          "SELECT b.name, m.menu_id, b.ord, NULL, NULL, 'F', '0', '0', b.perms, '#'\n"
          "FROM (" (module-menu module) ") m\n"
          "CROSS JOIN (" (str/join "\n  UNION ALL " rows) ") b;")
     (str "INSERT INTO sys_role_menu (role_id, menu_id)\n"
          "SELECT 1, f.menu_id FROM sys_menu f WHERE f.menu_type = 'F' AND f.parent_id IN (" (module-menu module) ");")]))

(defn- menu-up
  "菜单:共享的「业务管理」目录(不存在才建)+ 本模块菜单 + 按钮权限,并授权给 admin 角色(role_id = 1)。
   用派生表 (SELECT 1) AS one 代替 DUAL,两库通用。"
  [{:keys [module label perm-prefix] :as ctx}]
  (into
   [(str "INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)\n"
         "SELECT '业务管理', 0, 10, 'biz', NULL, 'M', '0', '0', NULL, 'AppstoreOutlined' FROM (SELECT 1 AS x) one\n"
         "WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'biz');")
    (str "INSERT INTO sys_role_menu (role_id, menu_id)\n"
         "SELECT 1, m.menu_id FROM sys_menu m WHERE m.parent_id = 0 AND m.path = 'biz'\n"
         "  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);")
    (str "INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)\n"
         "SELECT '" label "', p.menu_id, 1, '" module "', 'biz/" module "/index', 'C', '0', '0', '" perm-prefix ":list', 'ContainerOutlined'\n"
         "FROM sys_menu p WHERE p.parent_id = 0 AND p.path = 'biz';")
    (str "INSERT INTO sys_role_menu (role_id, menu_id)\n"
         "SELECT 1, c.menu_id FROM sys_menu c JOIN sys_menu p ON c.parent_id = p.menu_id\n"
         "WHERE p.parent_id = 0 AND p.path = 'biz' AND c.path = '" module "';")]
   (buttons-up ctx)))

(defn- menu-down
  "删除本模块的按钮与菜单;「业务管理」目录下没有其它菜单时一并删除。
   子查询包一层派生表,规避 MySQL 不允许在 DELETE 的子查询里直接引用目标表的限制。"
  [{:keys [module]}]
  (let [mine (module-menu module)
        buttons (str "SELECT f.menu_id FROM sys_menu f WHERE f.parent_id IN (" mine ")")
        empty-dir (str "SELECT m.menu_id FROM sys_menu m WHERE m.parent_id = 0 AND m.path = 'biz' "
                       "AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.parent_id = m.menu_id)")]
    [(str "DELETE FROM sys_role_menu WHERE menu_id IN (" buttons ");")
     (str "DELETE FROM sys_menu WHERE menu_id IN (SELECT menu_id FROM (" buttons ") t);")
     (str "DELETE FROM sys_role_menu WHERE menu_id IN (" mine ");")
     (str "DELETE FROM sys_menu WHERE menu_id IN (SELECT menu_id FROM (" mine ") t);")
     (str "DELETE FROM sys_role_menu WHERE menu_id IN (" empty-dir ");")
     (str "DELETE FROM sys_menu WHERE menu_id IN (SELECT menu_id FROM (" empty-dir ") t);")]))

(defn- join-statements [header stmts]
  (str header "\n" (str/join "\n--;;\n" stmts) "\n"))

(defn migrations
  "返回 {相对路径 内容}:两套目录各一对 up/down。"
  [{:keys [ts module label table] :as ctx}]
  (let [base (str ts "-create-biz-" module)
        header (str "-- " label "(" table "):bb new-module 生成。两套迁移目录须同步维护。")]
    (into {}
          (for [[db dir] [[:sqlite "resources/migrations-sqlite"] [:mysql "resources/migrations"]]]
            {(str dir "/" base ".up.sql")
             (join-statements header (cons (create-table db ctx) (menu-up ctx)))
             (str dir "/" base ".down.sql")
             (join-statements header (concat (menu-down ctx) [(str "DROP TABLE IF EXISTS " table ";")]))}))))

(defn- where-clause [fields]
  (str "WHERE 1 = 1"
       (apply str (for [{:keys [col type]} (m/searchable fields)]
                    (if (= :contains (get-in m/types [type :search]))
                      (str "\n  AND (:" col " IS NULL OR INSTR(" col ", :" col ") > 0)")
                      (str "\n  AND (:" col " IS NULL OR " col " = :" col ")"))))))

(defn queries
  "HugSQL 查询:分页列表、计数、详情、新增、全量更新、删除。"
  [{:keys [module label table fields]}]
  (let [cols (map :col fields)
        where (where-clause fields)]
    (str "-- " label "(" table ")的查询,bb new-module 生成;查询名全局唯一,均以 " module " 为后缀/前缀。\n\n"
         "-- :name list-" module " :? :*\n-- :doc 分页查询" label "\n"
         "SELECT * FROM " table "\n" where "\nORDER BY id DESC\nLIMIT :page_size OFFSET :offset\n\n"
         "-- :name count-" module " :? :1\n-- :doc 统计" label "数量\n"
         "SELECT COUNT(*) AS total FROM " table "\n" where "\n\n"
         "-- :name find-" module "-by-id :? :1\n"
         "SELECT * FROM " table " WHERE id = :id\n\n"
         "-- :name create-" module "! :! :n\n"
         "INSERT INTO " table " (" (str/join ", " cols) ", create_by, create_time)\n"
         "VALUES (" (str/join ", " (map #(str ":" %) cols)) ", :create_by, :now)\n\n"
         "-- :name update-" module "! :! :n\n"
         "UPDATE " table "\nSET " (str/join ",\n    " (map #(str % " = :" %) cols))
         ",\n    update_by = :update_by,\n    update_time = :now\nWHERE id = :id\n\n"
         "-- :name delete-" module "! :! :n\n"
         "DELETE FROM " table " WHERE id = :id\n")))
