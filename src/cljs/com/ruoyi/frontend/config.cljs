(ns com.ruoyi.frontend.config
  "前端全局配置:应用名称、副标题等品牌信息集中在此,复用模板时只需改这一处。
   (`resources/public/index.html` 的 <title> 由 layout 在运行时按本配置覆盖。)")

(def app-name
  "登录页标题、侧边栏 Logo、顶部导航与浏览器标题共用的应用名称。"
  "管理系统")

(def app-subtitle
  "登录页副标题。"
  "Clojure + ClojureScript + Ant Design")

(def welcome-text
  "首页欢迎语。"
  (str "欢迎回到" app-name "，今天也是元气满满的一天！"))

(def repo-url
  "顶部工具栏 GitHub 图标跳转的仓库地址。"
  "https://github.com/zhaoyul/clojure-template")

(def docs-url
  "顶部工具栏「文档」图标跳转的地址。"
  (str repo-url "#readme"))
