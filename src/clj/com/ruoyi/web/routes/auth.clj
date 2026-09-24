(ns com.ruoyi.web.routes.auth
  "认证路由。login / config / register 可匿名访问;refresh / getInfo 需要有效会话(:auth? true);
   logout 不强制登录(令牌已失效时也返回成功)。认证与鉴权见 web.middleware.auth。"
  (:require
   [com.ruoyi.web.controllers.auth :as auth]
   [com.ruoyi.web.controllers.register :as register]))

(def ^:private LoginBody
  [:map
   [:username {:optional true} [:maybe :string]]
   [:password {:optional true} [:maybe :string]]
   [:captcha {:optional true} [:maybe :string]]
   [:uuid {:optional true} [:maybe :string]]])

(defn auth-routes [{:keys [user-service log-service menu-service auth-config]}]
  (let [ctx {:user-service user-service :log-service log-service
             :menu-service menu-service :auth-config auth-config}]
    ["/auth"
     {:swagger {:tags ["认证"]}}
     ["/config" {:get {:summary     "登录页配置"
                       :description "是否显示验证码、是否开放注册;开发/测试环境附带演示账号"
                       :handler     (partial auth/login-config ctx)}}]
     ["/login" {:post {:summary     "登录"
                       :description "用户名密码登录,返回 Token;连续失败会被临时锁定"
                       :parameters  {:body LoginBody}
                       :handler     (partial auth/login ctx)}}]
     ["/refresh" {:post {:summary     "令牌续期"
                         :description "用当前有效令牌换新令牌(滑动续期),旧令牌短暂宽限后失效"
                         :auth?       true
                         :handler     (partial auth/refresh ctx)}}]
     ["/logout" {:post {:summary     "退出登录"
                        :description "删除当前会话,令牌立即失效"
                        :handler     auth/logout}}]
     ["/getInfo" {:get {:summary     "获取用户信息"
                        :description "获取当前用户信息(角色/权限/菜单)"
                        :auth?       true
                        :handler     (partial auth/get-info ctx)}}]
     ["/register" {:post {:summary "用户注册"
                          :description "默认关闭;开启后只接受用户名与密码,新用户没有任何角色"
                          :handler (partial register/register ctx)}}]]))
