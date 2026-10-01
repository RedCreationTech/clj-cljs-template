(ns com.ruoyi.web.controllers.system.menu
  "菜单管理控制器。"
  (:require
   [com.ruoyi.domain.system.menu :as menu-service]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn list-menus
  "查询菜单列表。"
  [{:keys [menu-service]} request]
  (res/ok (menu-service/list-menus menu-service (params/query request))))

(defn menu-tree
  [{:keys [menu-service]} _]
  (res/ok (menu-service/menu-tree menu-service)))

(defn get-menu
  [{:keys [menu-service]} request]
  (let [menu-id (parse-long (get-in request [:path-params :id]))]
    (if-let [menu (menu-service/find-menu-by-id menu-service menu-id)]
      (res/ok menu)
      (res/fail "菜单不存在"))))

(defn create-menu
  [{:keys [menu-service]} request]
  (let [params (assoc (:body-params request) :create_by (current-user-name request))
        menu-id (menu-service/create-menu! menu-service params)]
    (res/ok (str "创建成功: " menu-id))))

(defn update-menu
  [{:keys [menu-service]} request]
  (let [menu-id (parse-long (get-in request [:path-params :id]))
        params (-> (:body-params request)
                   (assoc :menu_id menu-id)
                   (assoc :update_by (current-user-name request)))]
    (menu-service/update-menu! menu-service params)
    (res/ok "更新成功")))

(defn delete-menu
  [{:keys [menu-service]} request]
  (let [menu-id (parse-long (get-in request [:path-params :id]))]
    (menu-service/delete-menu! menu-service menu-id)
    (res/ok "删除成功")))

(defn change-status
  "修改菜单状态。"
  [{:keys [menu-service]} request]
  (let [menu-id (parse-long (get-in request [:path-params :id]))
        status (get-in request [:body-params :status])]
    (menu-service/update-menu! menu-service {:menu_id menu-id :status status :update_by (current-user-name request)})
    (res/ok "状态修改成功")))
