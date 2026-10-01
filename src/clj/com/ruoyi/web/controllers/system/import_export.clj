(ns com.ruoyi.web.controllers.system.import-export
  "用户导入导出控制器，使用 multipart 上传与 clojure.data.csv。"
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.ruoyi.domain.system.config :as config-service]
   [com.ruoyi.domain.system.dept :as dept-service]
   [com.ruoyi.domain.system.dict :as dict-service]
   [com.ruoyi.domain.system.menu :as menu-service]
   [com.ruoyi.domain.system.post :as post-service]
   [com.ruoyi.domain.system.role :as role-service]
   [com.ruoyi.domain.system.user :as user-service]
   [com.ruoyi.infra.errors :as errors]
   [com.ruoyi.web.response :as res]
   [com.ruoyi.web.controllers.system.user :as user-ctrl]
   [ring.util.response :as response]))

(def ^:private csv-bom "\uFEFF")

(defn- parse-int [v]
  (when (and v (not (str/blank? (str v))))
    (try (Integer/parseInt (str v))
         (catch Exception _ nil))))

(defn- csv-row->user
  "将 CSV 行向量转换为用户参数映射。"
  [headers row]
  (let [m (zipmap headers row)]
    {:user_name (str/trim (get m "user_name" ""))
     :nick_name (str/trim (get m "nick_name" ""))
     :email (str/trim (get m "email" ""))
     :phonenumber (str/trim (get m "phonenumber" ""))
     :sex (or (str/trim (get m "sex" "0")) "0")
     :status (or (str/trim (get m "status" "0")) "0")
     :dept_id (or (parse-int (get m "dept_id")) 1)
     :user_type "00"
     :avatar ""
     :remark (str/trim (get m "remark" ""))}))

(defn- read-csv-rows
  "读取 multipart 上传的 CSV 文件，返回行向量列表。"
  [file]
  (let [tempfile (:tempfile file)]
    (with-open [reader (io/reader tempfile :encoding "UTF-8")]
      (doall (csv/read-csv reader)))))

(defn- row-failure-msg
  "导入结果里的行失败提示:业务校验给中文原因,意外错误(数据库等)只说导入不了,细节进日志。"
  [^Exception e]
  (if (= errors/business-type (:type (ex-data e)))
    (ex-message e)
    (do (log/warn e "导入用户失败") "数据有误,无法导入")))

(defn- import-one!
  "导入一行:业务规则不通过(用户名为空、账号重复)把提示收进这一行的结果里,
   让一次导入的其它行继续跑;整体失败(没选文件、CSV 读不出来)仍然抛给异常中间件。"
  [user-service identity headers row]
  (try
    (let [user (csv-row->user headers row)]
      (when (str/blank? (:user_name user))
        (errors/fail! "用户名不能为空"))
      (when (str/blank? (:nick_name user))
        (errors/fail! "用户昵称不能为空"))
      (user-service/create-user! user-service
                                 (assoc user
                                        :password "123456"
                                        :roles []
                                        :posts []
                                        :create_by (:user-name identity "")))
      {:user_name (:user_name user) :status "success"})
    (catch Exception e
      {:user_name (first row) :status "failed" :msg (row-failure-msg e)})))

(defn import-users
  "批量导入用户（multipart CSV）。"
  [{:keys [user-service]} request]
  (let [file (get-in request [:multipart-params "file"])
        identity (:identity request)]
    (when (or (nil? file) (str/blank? (:filename file "")))
      (errors/fail! "请选择要上传的文件"))
    (let [rows (read-csv-rows file)
          headers (mapv str/trim (first rows))
          results (mapv #(import-one! user-service identity headers %) (rest rows))
          counted (fn [s] (count (filter #(= s (:status %)) results)))]
      (res/ok {:total   (count results)
               :success (counted "success")
               :failed  (counted "failed")
               :details results}))))

(defn- user->csv-row
  "将用户映射转换为 CSV 行向量。"
  [user]
  [(:user_name user)
   (:nick_name user)
   (:email user)
   (:phonenumber user)
   (:sex user)
   (:status user)
   (:dept_id user)
   (:remark user)])

(defn- parse-id-list [ids]
  (->> (str/split (str ids) #",")
       (map str/trim)
       (remove str/blank?)
       (mapv parse-long)))

(defn- csv-response
  "CSV 文本 → 下载响应;带 BOM,Excel 打开才是 UTF-8。"
  [filename header rows]
  (let [out (java.io.StringWriter.)]
    (csv/write-csv out (cons header rows) :separator \, :quote \")
    (-> (response/response (str csv-bom out))
        (response/header "Content-Type" "text/csv; charset=utf-8")
        (response/header "Content-Disposition" (str "attachment; filename=" filename)))))

(defn export-users
  "导出用户为 CSV 文件（带数据权限过滤）。"
  [{:keys [user-service]} request]
  (let [selected-ids (set (parse-id-list (get-in request [:query-params "ids"])))
        params (assoc (user-ctrl/list-params user-service request) :page-num 1 :page-size 10000)
        rows (cond->> (:rows (user-service/list-users user-service params))
               (seq selected-ids) (filter #(contains? selected-ids (:user_id %))))]
    (csv-response "users.csv"
                  ["user_name" "nick_name" "email" "phonenumber" "sex" "status" "dept_id" "remark"]
                  (mapv user->csv-row rows))))

(defn import-template
  "下载用户导入模板。"
  [_ _]
  (csv-response "user_import_template.csv"
                ["user_name" "nick_name" "email" "phonenumber" "sex" "status" "dept_id" "remark"]
                [["admin" "管理员" "admin@ruoyi.vip" "13800138000" "0" "0" "1" ""]]))

;; ─── 通用导出函数 ──────────────────────────────────────────────────────

(defn- generic-export
  "通用导出函数。"
  [list-fn service params header csv-fn filename _request]
  (let [result (list-fn service (merge {:page-num 1 :page-size 10000} params))
        rows (if (sequential? result) result (:rows result []))]
    (csv-response filename header (mapv csv-fn rows))))

(defn export-roles
  "导出角色数据。"
  [{:keys [role-service]} request]
  (let [header ["role_id" "role_name" "role_key" "role_sort" "status"]
        csv-fn (fn [r] [(:role_id r) (:role_name r) (:role_key r) (:role_sort r) (:status r)])]
    (generic-export role-service/list-roles role-service {} header csv-fn "roles.csv" request)))

(defn export-menus
  "导出菜单数据。"
  [{:keys [menu-service]} request]
  (let [header ["menu_id" "menu_name" "parent_id" "order_num" "path" "component" "menu_type" "status"]
        csv-fn (fn [m] [(:menu_id m) (:menu_name m) (:parent_id m) (:order_num m) (:path m) (:component m) (:menu_type m) (:status m)])]
    (generic-export menu-service/list-menus menu-service {} header csv-fn "menus.csv" request)))

(defn export-depts
  "导出部门数据。"
  [{:keys [dept-service]} request]
  (let [header ["dept_id" "parent_id" "dept_name" "order_num" "leader" "status"]
        csv-fn (fn [d] [(:dept_id d) (:parent_id d) (:dept_name d) (:order_num d) (:leader d) (:status d)])]
    (generic-export dept-service/list-depts dept-service {} header csv-fn "depts.csv" request)))

(defn export-posts
  "导出岗位数据。"
  [{:keys [post-service]} request]
  (let [header ["post_id" "post_code" "post_name" "post_sort" "status"]
        csv-fn (fn [p] [(:post_id p) (:post_code p) (:post_name p) (:post_sort p) (:status p)])]
    (generic-export post-service/list-posts post-service {} header csv-fn "posts.csv" request)))

(defn export-dict-types
  "导出字典类型数据。"
  [{:keys [dict-service]} request]
  (let [header ["dict_id" "dict_name" "dict_type" "status"]
        csv-fn (fn [d] [(:dict_id d) (:dict_name d) (:dict_type d) (:status d)])]
    (generic-export dict-service/list-dict-types dict-service {} header csv-fn "dict_types.csv" request)))

(defn export-dict-data
  "导出字典数据。"
  [{:keys [dict-service]} request]
  (let [header ["dict_code" "dict_sort" "dict_label" "dict_value" "dict_type" "status"]
        csv-fn (fn [d] [(:dict_code d) (:dict_sort d) (:dict_label d) (:dict_value d) (:dict_type d) (:status d)])]
    (generic-export dict-service/list-dict-data dict-service {} header csv-fn "dict_data.csv" request)))

(defn export-configs
  "导出参数配置数据。"
  [{:keys [config-service]} request]
  (let [header ["config_id" "config_name" "config_key" "config_value" "config_type"]
        csv-fn (fn [c] [(:config_id c) (:config_name c) (:config_key c) (:config_value c) (:config_type c)])]
    (generic-export config-service/list-configs config-service {} header csv-fn "configs.csv" request)))
