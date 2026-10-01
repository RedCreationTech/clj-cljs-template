(ns com.ruoyi.web.controllers.register
  "用户自助注册。默认关闭(:auth-config :register-enabled?)。
   只接受用户名与密码,其余字段一律忽略:新用户不带任何角色、岗位与部门,需要管理员分配权限。"
  (:require
   [com.ruoyi.domain.system.user :as user-service]
   [com.ruoyi.web.controllers.auth :as auth]
   [com.ruoyi.web.response :as res]))

(defn- new-user
  "注册用户的完整字段(HugSQL 要求参数齐全);不接受请求里的任何其它字段。"
  [username password]
  {:dept_id nil :user_name username :nick_name username :user_type "00"
   :email "" :phonenumber "" :sex "0" :avatar "" :password password
   :status "0" :create_by "register" :remark ""})

(defn- invalid-reason
  "校验注册参数,合法返回 nil。"
  [cfg {:keys [username password] :as body}]
  (cond
    (not (:register-enabled? cfg)) [403 "当前系统没有开启注册功能"]
    (not (auth/captcha-ok? (:captcha-enabled? cfg) body)) [400 "验证码错误或已过期"]
    (not (re-matches #"[A-Za-z0-9_]{2,20}" (str username))) [400 "用户名须为 2~20 位字母、数字或下划线"]
    (not (<= 5 (count (str password)) 20)) [400 "密码长度须在 5~20 个字符之间"]))

(defn register
  "用户注册。参数校验不通过回对应的业务码;意外错误(数据库等)不在这里吞掉,
   由异常中间件走 5xx 通用文案,细节只进服务端日志。"
  [{:keys [user-service auth-config]} request]
  (let [{:keys [username password] :as body} (:body-params request)]
    (if-let [[code msg] (invalid-reason (auth/config-of auth-config) body)]
      (res/ok code msg)
      (if (user-service/find-user-by-name user-service username)
        (res/fail "注册账号已存在")
        (do (user-service/create-user! user-service (new-user username password))
            (res/ok 200 "注册成功"))))))
