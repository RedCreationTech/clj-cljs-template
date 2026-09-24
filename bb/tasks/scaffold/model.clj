(ns tasks.scaffold.model
  "bb new-module 的输入模型:模块名、字段规格解析与类型映射。
   字段规格:\"name:type[:required][:中文标签]\",多个用逗号分隔,例如
   \"title:string:required:标题,amount:decimal:金额,remark:text\"。"
  (:require
   [clojure.string :as str]))

(def types
  "字段类型 → 两库 DDL、Malli schema、是否可作为搜索条件、搜索方式。"
  {:string  {:sqlite "TEXT"       :mysql "VARCHAR(255)"  :malli ":string"          :search :contains :sample "\"示例文本\""}
   :text    {:sqlite "TEXT"       :mysql "TEXT"          :malli ":string"          :search nil       :sample "\"一段较长的说明\""}
   :int     {:sqlite "INTEGER"    :mysql "BIGINT"        :malli ":int"             :search :eq       :sample "7"}
   :decimal {:sqlite "REAL"       :mysql "DECIMAL(18,2)" :malli "number?"          :search nil       :sample "12.5"}
   :date    {:sqlite "TEXT"       :mysql "VARCHAR(10)"   :malli "[:re #\"^\\d{4}-\\d{2}-\\d{2}$\"]" :search :eq :sample "\"2026-01-01\""}
   :bool    {:sqlite "CHAR(1)"    :mysql "CHAR(1)"       :malli "[:enum \"0\" \"1\"]" :search :eq     :sample "\"1\""}})

(def reserved-columns #{"id" "create_by" "create_time" "update_by" "update_time"})

(def ^:private sql-keywords
  "两库里作列名需要转义的常见保留字;生成的 SQL 不加引号,直接拒绝。"
  #{"add" "all" "and" "as" "asc" "between" "by" "case" "check" "column" "condition" "create" "default"
    "delete" "desc" "distinct" "drop" "exists" "from" "group" "having" "in" "index" "insert" "interval"
    "into" "is" "join" "key" "keys" "like" "limit" "match" "not" "null" "or" "order" "range" "rank"
    "read" "references" "select" "set" "table" "to" "union" "update" "values" "when" "where" "write"})

(defn ->snake [s] (str/replace s "-" "_"))

(defn- parse-field [spec]
  (let [[fname ftype & opts] (map str/trim (str/split spec #":"))
        col (->snake (str/lower-case (or fname "")))
        type-kw (keyword (or (not-empty ftype) "string"))
        required? (some #{"required"} opts)
        label (first (remove #(or (str/blank? %) (= "required" %)) opts))]
    (cond
      (not (re-matches #"[a-z][a-z0-9_]*" col))
      (throw (ex-info (str "字段名不合法:" (pr-str fname) "(小写字母开头,可含数字/下划线)") {}))

      (reserved-columns col)
      (throw (ex-info (str "字段名 " col " 是保留列,会自动生成") {}))

      (sql-keywords col)
      (throw (ex-info (str "字段名 " col " 是 SQL 保留字,请换一个(如 " col "_value)") {}))

      (not (types type-kw))
      (throw (ex-info (str "字段 " col " 的类型 " (pr-str ftype) " 不支持;可用:" (str/join ", " (map name (keys types)))) {}))

      :else
      {:col col :type type-kw :required? (boolean required?) :label (or label col)})))

(defn parse-fields
  "解析 --fields;为空时给一个最小可用的默认字段集。"
  [s]
  (let [specs (->> (str/split (or s "") #",") (map str/trim) (remove str/blank?))
        fields (mapv parse-field (if (seq specs) specs ["name:string:required:名称" "remark:text:备注"]))
        dups (->> fields (map :col) frequencies (filter #(> (val %) 1)) keys)]
    (when (seq dups)
      (throw (ex-info (str "字段重复:" (str/join ", " dups)) {})))
    fields))

(defn searchable
  "最多取前 3 个可搜索字段作为查询条件。"
  [fields]
  (->> fields (filter (comp :search types :type)) (take 3) vec))

(defn build
  "由命令行参数构造生成所需的完整上下文。"
  [{:keys [module label fields project now]}]
  (when-not (re-matches #"[a-z][a-z0-9]*(-[a-z0-9]+)*" (or module ""))
    (throw (ex-info "模块名需为小写 kebab-case,例如 notice-board、customer" {})))
  (let [{:keys [ns-name sanitized]} project
        snake (->snake module)]
    {:module module
     :snake snake
     :label (or (not-empty label) module)
     :table (str "biz_" snake)
     :ns-root ns-name
     :path-root sanitized
     :fields (parse-fields fields)
     :ts (.format (java.text.SimpleDateFormat. "yyyyMMddHHmmss") now)
     :service-key (str ":app." module "/service")
     :service-arg (str module "-service")
     :api-path (str "/biz/" module)
     :menu-path (str "biz/" module)}))
