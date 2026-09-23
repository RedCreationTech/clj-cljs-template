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
  (let [token (t/get-token)
        headers (if token {"Authorization" (str "Bearer " token)} {})]
    (-> (js/fetch (str t/api-base "/tool/gen/download")
                  (clj->js {:method "POST"
                            :headers (clj->js (assoc headers "Content-Type" "application/json"))
                            :body (js/JSON.stringify (clj->js {:tables tables}))}))
        (.then (fn [resp]
                 (if (.-ok resp)
                   (.blob resp)
                   (throw (js/Error. (str "Download failed: " (.-status resp)))))))
        (.then (fn [blob]
                 (let [url (js/URL.createObjectURL blob)
                       a (js/document.createElement "a")]
                   (set! (.-href a) url)
                   (set! (.-download a) "gen-code.zip")
                   (.appendChild (.-body js/document) a)
                   (.click a)
                   (.removeChild (.-body js/document) a)
                   (js/URL.revokeObjectURL url)))))))

(defn gen-deploy
  "部署生成的代码到项目。"
  [table-name on-success on-error]
  (t/request {:method :post :uri "/tool/gen/deploy"
              :params {:tableName table-name}
              :on-success on-success :on-error on-error}))
