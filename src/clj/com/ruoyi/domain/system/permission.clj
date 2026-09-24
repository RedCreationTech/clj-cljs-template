(ns com.ruoyi.domain.system.permission
  "权限标识(若依风格 `模块:资源:动作`,如 system:user:add)。

   来源:用户 → 启用的角色 → 角色勾选的菜单(目录 M / 菜单 C / 按钮 F)上的 perms 字段,
   一个菜单可以用逗号写多个标识。角色键为 admin 的角色拥有全部权限(*:*:*)。
   后端鉴权(web.middleware.auth/authorize)与前端按钮显隐(getInfo 的 permissions)共用这里的结果。"
  (:require
   [clojure.string :as str]))

(def all-permission
  "通配权限:拥有它等于拥有全部权限。"
  "*:*:*")

(def admin-role-key
  "超级管理员角色键。"
  "admin")

(defn- split-perms [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)))

(defn rows->permissions
  "把 [{:role_key .. :perms ..}] 归并为权限集合;含 admin 角色时只返回通配权限。"
  [rows]
  (if (some #(= admin-role-key (:role_key %)) rows)
    #{all-permission}
    (into (sorted-set) (mapcat (comp split-perms :perms)) rows)))

(defn user-permissions
  "查询用户当前拥有的权限集合(每次实时查询,角色与菜单调整立即生效)。"
  [query-fn user-id]
  (if user-id
    (rows->permissions (query-fn :list-user-role-perms {:user_id user-id}))
    #{}))

(defn permitted?
  "user-perms 是否满足 required:required 为字符串或字符串集合,满足其中任一即可;
   拥有通配权限时总是满足。"
  [user-perms required]
  (let [required (if (string? required) [required] required)]
    (boolean (or (contains? user-perms all-permission)
                 (some #(contains? user-perms %) required)))))
