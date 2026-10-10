(ns tasks.scaffold.check
  "生成模板的纯回归检查,不连接数据库或启动浏览器。"
  (:require
   [clojure.string :as str]
   [tasks.scaffold.frontend :as frontend]
   [tasks.scaffold.model :as model]))

(defn- ensure! [condition message]
  (when-not condition (throw (ex-info message {}))))

(defn- context [fields]
  (model/build {:module "ci-demo" :label "冒烟模块" :fields fields
                :project {:ns-name "com.example" :sanitized "com/example"}
                :now (java.util.Date. 0)}))

(defn- six-field-template! []
  (let [ctx (context "title:string:required:标题,qty:int:数量,price:decimal:单价,due_on:date:日期,active:bool:启用,remark:text:备注")
        generated (frontend/e2e ctx)]
    (doseq [{:keys [col type label]} (:fields ctx)]
      (doseq [literal [(str "col: " (pr-str col)) (str "type: " (pr-str (name type)))
                       (str "label: " (pr-str label))]]
        (ensure! (str/includes? generated literal) (str "生成 E2E 缺少全字段元数据:" literal))))
    (doseq [call ["generated.crud(page, config)" "generated.invalid(page, config)"
                  "generated.paging(page, config)" "config.fields.length * 2"]]
      (ensure! (str/includes? generated call) (str "生成 E2E 缺少场景:" call)))
    (ensure! (str/includes? generated "searchCols: [\"title\", \"qty\", \"due_on\"]")
             "搜索条件必须与实际页面的前三个可搜索字段相同")
    (ensure! (not (re-find #"test\.(skip|fixme|only)" generated)) "生成 E2E 不得按数据库跳过")))

(defn- non-string-template! []
  (let [generated (frontend/e2e (context "qty:int:required:数量,active:bool:启用"))]
    (ensure! (str/includes? generated "generated.crud(page, config)")
             "没有 string 字段的模块也必须执行 CRUD,不能只断言空表可见")
    (ensure! (str/includes? generated "searchCols: [\"qty\", \"active\"]") "搜索字段必须按规格生成")))

(defn check!
  "六字段及无字符串字段模板必须保留完整浏览器回归入口。"
  []
  (six-field-template!)
  (non-string-template!)
  (println "✔ 全字段脚手架浏览器模板回归通过"))
