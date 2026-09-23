(ns com.ruoyi.frontend.pages.layout.page-view
  "路由关键词 -> 页面组件的分发表。"
  (:require
   [com.ruoyi.frontend.pages.dashboard :as dashboard]
   [com.ruoyi.frontend.pages.user :as user]
   [com.ruoyi.frontend.pages.role :as role]
   [com.ruoyi.frontend.pages.menu :as menu]
   [com.ruoyi.frontend.pages.dept :as dept]
   [com.ruoyi.frontend.pages.post :as post]
   [com.ruoyi.frontend.pages.notice :as notice]
   [com.ruoyi.frontend.pages.online :as online]
   [com.ruoyi.frontend.pages.job :as job]
   [com.ruoyi.frontend.pages.profile :as profile]
   [com.ruoyi.frontend.pages.dict :as dict]
   [com.ruoyi.frontend.pages.config :as config]
   [com.ruoyi.frontend.pages.oper-log :as oper-log]
   [com.ruoyi.frontend.pages.login-log :as login-log]
   [com.ruoyi.frontend.pages.server :as server]
   [com.ruoyi.frontend.pages.cache :as cache]
   [com.ruoyi.frontend.pages.datasource :as datasource]
   [com.ruoyi.frontend.pages.gen :as gen]
   [com.ruoyi.frontend.pages.swagger :as swagger]
   [com.ruoyi.frontend.pages.form-builder :as form-builder]
   [com.ruoyi.frontend.pages.file-manager :as file-manager]
   [com.ruoyi.frontend.pages.integrant :as integrant]))

;; 路由关键词 -> 页面组件。业务模块的页面在此登记,例如 :example-list [example/list-page]。
(defn render-page
  [page]
  (case page
    :dashboard [dashboard/dashboard-page]
    :user [user/user-page]
    :role [role/role-page]
    :menu [menu/menu-page]
    :dept [dept/dept-page]
    :post [post/post-page]
    :notice [notice/notice-page]
    :online [online/online-page]
    :job [job/job-page]
    :profile [profile/profile-page]
    :dict [dict/dict-page]
    :config [config/config-page]
    :oper-log [oper-log/oper-log-page]
    :login-log [login-log/login-log-page]
    :server [server/server-page]
    :cache [cache/cache-page]
    :datasource [datasource/datasource-page]
    :integrant [integrant/integrant-page]
    :gen [gen/gen-page]
    :swagger [swagger/swagger-page]
    :build [form-builder/form-builder-page]
    :file [file-manager/file-manager-page]
    [:div {:style {:padding 48 :textAlign "center" :color "#999" :fontSize 16}}
     "页面建设中"]))
