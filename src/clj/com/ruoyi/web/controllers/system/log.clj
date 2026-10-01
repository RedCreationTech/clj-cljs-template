(ns com.ruoyi.web.controllers.system.log
  "日志审计控制器。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.system.log :as log-service]
   [com.ruoyi.infra.login-guard :as guard]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- parse-ids [s]
  (->> (str/split (str s) #",")
       (map str/trim)
       (remove str/blank?)
       (map #(Long/parseLong %))))

(defn list-oper-logs
  "查询操作日志列表。"
  [{:keys [log-service]} request]
  (let [result (log-service/list-oper-logs log-service (params/query request))]
    (res/ok {:total (:total result) :rows (:rows result)})))

(defn clear-oper-logs
  "清空操作日志。"
  [{:keys [log-service]} request]
  (log-service/clear-oper-logs! log-service (params/query request))
  (res/ok "清空成功"))

(defn delete-oper-logs
  "删除操作日志。"
  [{:keys [log-service]} request]
  (log-service/delete-oper-logs! log-service (parse-ids (get-in request [:path-params :ids])))
  (res/ok "删除成功"))

(defn list-login-logs
  "查询登录日志列表。"
  [{:keys [log-service]} request]
  (let [result (log-service/list-login-logs log-service (params/query request))]
    (res/ok {:total (:total result) :rows (:rows result)})))

(defn clear-login-logs
  "清空登录日志。"
  [{:keys [log-service]} request]
  (log-service/clear-login-logs! log-service (params/query request))
  (res/ok "清空成功"))

(defn delete-login-logs
  "删除登录日志。"
  [{:keys [log-service]} request]
  (log-service/delete-login-logs! log-service (parse-ids (get-in request [:path-params :ids])))
  (res/ok "删除成功"))

(defn unlock-user
  "解除用户的登录失败锁定(见 infra.login-guard)。"
  [_ctx request]
  (let [user-name (get-in request [:path-params :userName])]
    (guard/unlock! user-name)
    (res/ok (str "用户 " user-name " 已解锁"))))

(defn list-online-users
  "查询在线用户列表。"
  [{:keys [log-service]} request]
  (let [result (log-service/list-online-users log-service (params/query request))]
    (res/ok {:total (:total result) :rows (:rows result)})))

(defn kick-online-user
  "强退在线用户。"
  [{:keys [log-service]} request]
  (let [session-id (get-in request [:path-params :id])]
    (log-service/delete-online-user! log-service session-id)
    (res/ok "强退成功")))
