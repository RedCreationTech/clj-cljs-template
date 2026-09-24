(ns com.ruoyi.frontend.api.auth
  "认证:登录页配置 / 登录 / 续期 / 登出 / 当前用户信息。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn login-config
  "登录页公开配置:是否显示验证码、是否开放注册、开发环境的演示账号。"
  [on-success on-error]
  (t/request {:method :get :uri "/auth/config"
              :on-success on-success :on-error on-error}))

(defn login
  "用户登录。"
  [params on-success on-error]
  (t/request {:method :post :uri "/auth/login" :params params
              :on-success on-success :on-error on-error}))

(defn refresh
  "用当前令牌换新令牌(滑动续期)。"
  [on-success on-error]
  (t/request {:method :post :uri "/auth/refresh"
              :on-success on-success :on-error on-error}))

(defn get-info
  "获取当前用户信息。"
  [on-success on-error]
  (t/request {:method :get :uri "/auth/getInfo"
              :on-success on-success :on-error on-error}))

(defn logout
  "用户登出。token 显式传入:调用时 app-db 里的令牌通常已经清掉了。"
  [token on-success on-error]
  (t/request {:method :post :uri "/auth/logout" :token token
              :on-success on-success :on-error on-error}))
