(ns com.ruoyi.frontend.i18n
  "界面多语言(gettext 风格):以中文原文作为键,`(tr \"用户管理\")` 在英文界面返回译文,
   没有译文时原样返回中文。这样页面可以逐步迁移:把字面量包进 tr、在 en-US 里补一行即可,
   漏翻的文案退回中文而不是显示一个键名。菜单名(来自数据库)、面包屑、Tab 标题也经 tr 翻译。
   切换语言时根组件以语言为 key 整体重新挂载(见 app.cljs),所有 tr 调用随之刷新。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.frontend.storage :as storage]))

(def locales
  "可选语言(顺序即切换菜单里的顺序)。"
  [{:key :zh-CN :label "简体中文"}
   {:key :en-US :label "English"}])

(def default-locale :zh-CN)

(def ^:private en-US
  {;; 菜单与页面(菜单名来自 sys_menu,按原文翻译)
   "首页" "Home"
   "系统管理" "System"
   "系统监控" "Monitor"
   "系统工具" "Tools"
   "业务管理" "Business"
   "日志管理" "Logs"
   "用户管理" "Users"
   "角色管理" "Roles"
   "菜单管理" "Menus"
   "部门管理" "Departments"
   "岗位管理" "Posts"
   "文件管理" "Files"
   "字典管理" "Dictionaries"
   "参数管理" "Parameters"
   "参数设置" "Parameters"
   "通知公告" "Notices"
   "操作日志" "Operation Logs"
   "登录日志" "Login Logs"
   "在线用户" "Online Users"
   "定时任务" "Scheduled Jobs"
   "服务监控" "Server"
   "缓存监控" "Cache"
   "数据监控" "Data Source"
   "连接池监视" "Connection Pool"
   "Integrant 依赖" "Integrant Graph"
   "系统接口" "API Docs"
   "表单构建" "Form Builder"
   "个人中心" "Profile"
   "页面" "Page"
   "页面建设中" "Page under construction"
   ;; 登录
   "用户名" "Username"
   "密码" "Password"
   "验证码" "Captcha"
   "点击刷新验证码" "Click to refresh"
   "登 录" "Log in"
   "登录中..." "Logging in..."
   "登录失败" "Login failed"
   "网络错误,请稍后重试" "Network error, please retry later"
   "登录状态已过期,请重新登录" "Your session has expired, please log in again"
   ;; 接口失败的统一提示(api.errors / 后端通用文案)
   "没有操作权限" "Permission denied"
   "未登录或令牌已过期" "Not logged in or session expired"
   "接口不存在" "API not found"
   "请求参数不合法" "Invalid request parameters"
   "服务器错误,请稍后重试" "Server error, please retry later"
   "服务器内部错误,请稍后重试" "Internal server error, please retry later"
   "响应格式错误" "Malformed response"
   "操作失败" "Operation failed"
   ;; 头部
   "布局设置" "Layout"
   "退出登录" "Log out"
   "显示设置" "Display"
   "布局密度" "Density"
   "紧凑" "Compact"
   "默认" "Default"
   "宽松" "Comfortable"
   "字体大小" "Font size"
   "小" "Small"
   "中" "Medium"
   "大" "Large"
   "语言" "Language"
   "文档" "Docs"
   "搜索菜单" "Search menus"
   "通知" "Notifications"
   "公告" "Announcement"
   "暂无通知" "No notifications"
   "查看全部" "View all"
   ;; 标签页
   "刷新页面" "Reload"
   "关闭当前" "Close"
   "关闭其他" "Close others"
   "关闭右侧" "Close to the right"
   "全部关闭" "Close all"
   "向左滚动" "Scroll left"
   "向右滚动" "Scroll right"
   "刷新当前页" "Reload current page"
   ;; 通用工具栏 / 分页
   "搜索" "Search"
   "重置" "Reset"
   "刷新" "Refresh"
   "显示搜索" "Show search"
   "隐藏搜索" "Hide search"
   "显隐列" "Columns"
   "新增" "Add"
   "修改" "Edit"
   "编辑" "Edit"
   "删除" "Delete"
   "导入" "Import"
   "导出" "Export"
   "共 {0} 条" "Total {0}"})

(def ^:private dictionaries {:en-US en-US})

(defonce ^:private current (atom default-locale))

(defn locale [] @current)

(defn set-locale!
  "切换当前语言并记住选择;不认识的语言退回默认语言。"
  [k]
  (let [k (if (some #(= k (:key %)) locales) k default-locale)]
    (reset! current k)
    (storage/set-item! :locale k)
    k))

(defn load-locale!
  "启动时恢复上次选择的语言。"
  []
  (reset! current (let [k (some-> (storage/get-item :locale) keyword)]
                    (if (some #(= k (:key %)) locales) k default-locale))))

(defn tr
  "翻译界面文案;args 依次替换 {0} {1} …。"
  [text & args]
  (let [s (get-in dictionaries [@current text] text)]
    (reduce-kv (fn [acc i v] (str/replace acc (str "{" i "}") (str v))) s (vec args))))
