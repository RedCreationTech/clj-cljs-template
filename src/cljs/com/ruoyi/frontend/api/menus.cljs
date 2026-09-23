(ns com.ruoyi.frontend.api.menus
  "菜单管理相关接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-menus
  "获取菜单列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/menu" :params params
              :on-success on-success :on-error on-error}))

(defn create-menu
  "新增菜单。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/menu" :params params
              :on-success on-success :on-error on-error}))

(defn update-menu
  "更新菜单。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/menu/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-menu
  "删除菜单。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/menu/" id)
              :on-success on-success :on-error on-error}))

(defn change-menu-status
  "修改菜单状态。"
  [id status on-success on-error]
  (t/request {:method :put :uri (str "/system/menu/" id) :params {:status status}
              :on-success on-success :on-error on-error}))

(defn menu-tree
  "获取菜单树（用于角色权限分配）。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/menu/treeselect"
              :on-success on-success :on-error on-error}))
