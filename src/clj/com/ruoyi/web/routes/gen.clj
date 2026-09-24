(ns com.ruoyi.web.routes.gen
  "代码生成器路由。"
  (:require
   [com.ruoyi.web.controllers.gen :as gen]))

(defn gen-routes [{:keys [gen-service env]}]
  ["/tool"
   {:auth? true
    :swagger {:tags ["代码生成"]}}
   ["/gen"
    ["/tables" {:get {:perms      "tool:gen:list"
                      :summary    "查询数据库表"
                      :description "查询系统所有数据库表（供选择生成）"
                      :handler    (partial gen/list-tables {:gen-service gen-service})}}]
    ["/columns" {:get {:perms      "tool:gen:list"
                       :summary    "查询表列信息"
                       :description "查询指定表的字段元数据"
                       :handler    (partial gen/table-columns {:gen-service gen-service})}}]
    ["/preview" {:get {:perms      "tool:gen:preview"
                       :summary    "预览代码"
                       :description "预览生成的代码模板内容"
                       :parameters {:query [:map [:tableName :string]]}
                       :handler    (partial gen/preview-code {:gen-service gen-service})}}]
    ["/generate" {:post {:perms      "tool:gen:code"
                         :summary    "批量生成代码"
                         :description "选择表并生成完整 CRUD 代码文件"
                         :handler    (partial gen/batch-generate {:gen-service gen-service})}}]
    ["/deploy" {:post {:perms      "tool:gen:code"
                       :summary    "部署代码(仅开发环境)"
                       :description "将生成的代码写入项目源码目录;非 dev profile 返回 403"
                       :handler    (partial gen/deploy-code {:gen-service gen-service :env env})}}]
    ["/download" {:post {:perms      "tool:gen:code"
                         :summary    "下载代码"
                         :description "批量生成代码并打包成 ZIP 下载"
                         :handler    (partial gen/download-code {:gen-service gen-service})}}]]])
