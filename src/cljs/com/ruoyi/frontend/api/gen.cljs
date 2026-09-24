(ns com.ruoyi.frontend.api.gen
  "代码生成接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn gen-tables
  "获取数据库表列表。"
  [on-success on-error]
  (t/request {:method :get :uri "/tool/gen/tables"
              :on-success on-success :on-error on-error}))

(defn gen-columns
  "获取表列信息。"
  [table-name on-success on-error]
  (t/request {:method :get :uri "/tool/gen/columns"
              :params {:tableName table-name}
              :on-success on-success :on-error on-error}))

(defn gen-preview
  "预览生成的代码。"
  [table-name on-success on-error]
  (t/request {:method :get :uri "/tool/gen/preview"
              :params {:tableName table-name}
              :on-success on-success :on-error on-error}))

(defn gen-generate
  "批量生成代码。"
  [tables on-success on-error]
  (t/request {:method :post :uri "/tool/gen/generate"
              :params {:tables tables}
              :on-success on-success :on-error on-error}))

(defn gen-download
  "下载生成的代码 ZIP。"
  [tables]
  (t/download! "/tool/gen/download" "gen-code.zip" :json-body {:tables tables}))

(defn gen-deploy
  "部署生成的代码到项目。"
  [table-name on-success on-error]
  (t/request {:method :post :uri "/tool/gen/deploy"
              :params {:tableName table-name}
              :on-success on-success :on-error on-error}))
