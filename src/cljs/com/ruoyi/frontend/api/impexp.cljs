(ns com.ruoyi.frontend.api.impexp
  "通用导入/导出接口。"
  (:require
   [ajax.core :as ajax]
   [clojure.string :as str]
   [com.ruoyi.frontend.api.transport :as t]))

(defn export-users-csv
  "导出用户CSV。"
  [params on-success on-error]
  (ajax/ajax-request
   {:method :get
    :uri (str t/api-base "/system/user/export")
    :params params
    :headers (when-let [token (t/get-token)]
               {"Authorization" (str "Bearer " token)})
    :response-format {:content-type "text/csv"
                      :description "CSV"
                      :read (fn [xhrio] (.-responseText xhrio))
                      :type :text}
    :handler (fn [[ok result]]
               (if ok
                 (on-success (:body result))
                 (on-error result)))}))

(defn download-user-import-template
  "下载用户导入的 CSV 模板(带令牌)。"
  []
  (t/download! "/system/user/importTemplate" "user_import_template.csv"))

(defn import-users-csv
  "导入用户CSV。"
  [file on-success on-error]
  (let [form-data (js/FormData.)]
    (.append form-data "file" file)
    (ajax/ajax-request
     {:method :post
      :uri (str t/api-base "/system/user/import")
      :body form-data
      :headers (when-let [token (t/get-token)] {"Authorization" (str "Bearer " token)})
      :response-format (ajax/json-response-format {:keywords? true})
      :handler (fn [[ok result]]
                 (if ok (on-success result) (on-error result)))})))

(defn export-generic-csv
  "通用导出CSV。"
  [url filename params]
  (let [query-str (when (seq params)
                    (str "?" (str/join "&"
                                       (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent (str v))))
                                            params))))]
    (t/download! (str url query-str) (or filename "export.csv"))))

(defn export-roles
  [params]
  (export-generic-csv "/system/role/export" "角色数据.csv" params))

(defn export-menus
  [params]
  (export-generic-csv "/system/menu/export" "菜单数据.csv" params))

(defn export-depts
  [params]
  (export-generic-csv "/system/dept/export" "部门数据.csv" params))

(defn export-posts
  [params]
  (export-generic-csv "/system/post/export" "岗位数据.csv" params))

(defn export-dicts
  [params]
  (export-generic-csv "/system/dict/type/export" "字典数据.csv" params))

(defn export-configs
  [params]
  (export-generic-csv "/system/config/export" "参数数据.csv" params))

(defn export-operlogs
  [params]
  (export-generic-csv "/monitor/operlog/export" "操作日志.csv" params))

(defn export-loginlogs
  [params]
  (export-generic-csv "/monitor/logininfor/export" "登录日志.csv" params))
