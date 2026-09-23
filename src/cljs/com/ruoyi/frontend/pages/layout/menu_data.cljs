(ns com.ruoyi.frontend.pages.layout.menu-data
  "主布局的静态路由数据:页面 -> 菜单 key、面包屑、标签与图标。")

;; ─── 页面关键词到菜单路径映射 ─────────────────────────────────────────
(def page->menu-key
  "将路由关键词映射到菜单的 key（完整路径）。"
  {:user "system/user"
   :role "system/role"
   :menu "system/menu"
   :dept "system/dept"
   :post "system/post"
   :file "system/file"
   :dict "system/dict"
   :config "system/config"
   :notice "system/notice"
   :oper-log "system/operlog/operlog"
   :login-log "system/operlog/logininfor"
   :online "monitor/online"
   :job "monitor/job"
   :server "monitor/server"
   :cache "monitor/cache"
   :datasource "monitor/datasource"
   :integrant "monitor/integrant"
   :gen "monitor/gen"
   :swagger "monitor/swagger"
   :build "tool/build"
   :profile "system/user/profile"
   :dashboard "dashboard"})

(def page-breadcrumbs
  {:dashboard ["首页"]
   :user ["首页" "系统管理" "用户管理"]
   :role ["首页" "系统管理" "角色管理"]
   :menu ["首页" "系统管理" "菜单管理"]
   :dept ["首页" "系统管理" "部门管理"]
   :post ["首页" "系统管理" "岗位管理"]
   :dict ["首页" "系统管理" "字典管理"]
   :config ["首页" "系统管理" "参数设置"]
   :notice ["首页" "系统管理" "通知公告"]
   :oper-log ["首页" "系统管理" "日志管理" "操作日志"]
   :login-log ["首页" "系统管理" "日志管理" "登录日志"]
   :online ["首页" "系统监控" "在线用户"]
   :job ["首页" "系统监控" "定时任务"]
   :server ["首页" "系统监控" "服务监控"]
   :cache ["首页" "系统监控" "缓存监控"]
   :datasource ["首页" "系统监控" "连接池监视"]
   :build ["首页" "系统工具" "表单构建"]
   :gen ["首页" "系统工具" "代码生成"]
   :swagger ["首页" "系统工具" "系统接口"]
   :profile ["首页" "个人中心"]})

(def route-labels
  (into {} (map (fn [[k xs]] [k (last xs)]) page-breadcrumbs)))

(def route-icons
  {:dashboard "dashboard"
   :user "user"
   :role "peoples"
   :menu "tree-table"
   :dept "tree"
   :post "post"
   :dict "dict"
   :config "edit"
   :notice "message"
   :oper-log "form"
   :login-log "logininfor"
   :online "online"
   :job "job"
   :server "server"
   :cache "cache"
   :datasource "database"
   :build "build"
   :gen "code"
   :swagger "swagger"
   :profile "profile"})
