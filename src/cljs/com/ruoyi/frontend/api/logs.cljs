(ns com.ruoyi.frontend.api.logs
  "操作日志与登录日志接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-oper-logs
  "获取操作日志列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/oper-log" :params params
              :on-success on-success :on-error on-error}))

(defn list-login-logs
  "获取登录日志列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/login-log" :params params
              :on-success on-success :on-error on-error}))

(defn clear-oper-logs
  "清空操作日志。"
  [on-success on-error]
  (t/request {:method :delete :uri "/system/oper-log"
              :on-success on-success :on-error on-error}))

(defn delete-oper-logs
  "删除操作日志。"
  [ids on-success on-error]
  (t/request {:method :delete :uri (str "/system/oper-log/" ids)
              :on-success on-success :on-error on-error}))

(defn unlock-user
  "解除用户的登录失败锁定。"
  [user-name on-success on-error]
  (t/request {:method :put :uri (str "/system/login-log/unlock/" (js/encodeURIComponent user-name))
              :on-success on-success :on-error on-error}))

(defn clear-login-logs
  "清空登录日志。"
  [on-success on-error]
  (t/request {:method :delete :uri "/system/login-log"
              :on-success on-success :on-error on-error}))

(defn delete-login-logs
  "删除登录日志。"
  [ids on-success on-error]
  (t/request {:method :delete :uri (str "/system/login-log/" ids)
              :on-success on-success :on-error on-error}))
