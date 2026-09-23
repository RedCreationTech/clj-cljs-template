(ns com.ruoyi.frontend.api.auth
  "认证:登录/登出/当前用户信息。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn login
  "用户登录。"
  [params on-success on-error]
  (t/request {:method :post :uri "/auth/login" :params params
              :on-success on-success :on-error on-error}))

(defn get-info
  "获取当前用户信息。"
  [on-success on-error]
  (t/request {:method :get :uri "/auth/getInfo"
              :on-success on-success :on-error on-error}))

(defn logout
  "用户登出。"
  [on-success on-error]
  (t/request {:method :post :uri "/auth/logout"
              :on-success on-success :on-error on-error}))
