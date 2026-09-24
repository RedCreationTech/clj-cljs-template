(ns com.ruoyi.frontend.perm
  "按钮级权限(与后端 domain.system.permission 一致):
   - 权限标识来自 getInfo 的 permissions,admin 为 [\"*:*:*\"];
   - required 为字符串或字符串集合,满足任一即可。
   前端显隐只是体验,真正的拦截在后端路由的 :perms。

   用法:
     [page-toolbar/toolbar-button {:perm \"system:user:add\" ...}]  ; 工具栏按钮自带
     [perm/when-allowed \"system:user:edit\" [antd/button ...]]      ; 其它任意元素"
  (:require
   [re-frame.core :as rf]))

(def all-permission "*:*:*")

(defn permitted?
  "纯函数:user-perms(集合)是否满足 required;required 为 nil 表示不需要权限。"
  [user-perms required]
  (cond
    (nil? required) true
    (contains? user-perms all-permission) true
    (string? required) (contains? user-perms required)
    :else (boolean (some #(contains? user-perms %) required))))

(rf/reg-sub :auth/permissions
            (fn [db _]
              (set (get-in db [:auth :user :permissions]))))

(rf/reg-sub :auth/permitted?
            :<- [:auth/permissions]
            (fn [perms [_ required]]
              (permitted? perms required)))

(defn allowed?
  "在组件渲染中使用:当前用户是否拥有 required。"
  [required]
  @(rf/subscribe [:auth/permitted? required]))

(defn when-allowed
  "有权限时渲染子元素,否则什么都不渲染。"
  [required & children]
  (when (allowed? required)
    (into [:<>] children)))
