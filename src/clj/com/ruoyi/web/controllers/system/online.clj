(ns com.ruoyi.web.controllers.system.online
  "在线用户控制器。"
  (:require
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn list-online
  "获取在线用户列表(分页)。空的筛选条件不要传 nil:list-online 的 :or 默认值只对缺失的键生效。"
  [{:keys [online-service]} request]
  (let [q (params/query request)]
    (res/ok ((:list-online online-service)
             (cond-> {:page-num (or (:page-num q) 1)
                      :page-size (or (:page-size q) 10)}
               (:user_name q) (assoc :login-name (:user_name q))
               (:ipaddr q) (assoc :ipaddr (:ipaddr q)))))))

(defn force-logout
  "强退指定用户。"
  [{:keys [online-service]} request]
  (let [token-id (get-in request [:path-params :token-id])]
    ((:force-logout online-service) token-id)
    (res/ok 200 "操作成功")))
