(ns com.ruoyi.web.controllers.params
  "查询参数规范化。reitit 的 :query-params 是字符串键(\"role_name\"),领域层按关键字键取值
   (:role_name),直接传过去筛选条件会被静默忽略。列表接口统一经 query 转换:
   - 键转关键字;空串视为未填写(去掉,由领域层的默认 nil 接管);
   - 分页参数 page/size(兼容 pageNum/pageSize)转成整数的 :page-num / :page-size。"
  (:require
   [clojure.string :as str]))

(def ^:private page-keys {"page" :page-num "pageNum" :page-num "size" :page-size "pageSize" :page-size})

(defn query
  "request 的查询参数 → 关键字键的 map(见命名空间说明)。"
  [request]
  (reduce-kv (fn [m k v]
               (let [k (name k)]
                 (cond
                   (or (nil? v) (and (string? v) (str/blank? v))) m
                   (page-keys k) (if-let [n (parse-long (str v))] (assoc m (page-keys k) n) m)
                   :else (assoc m (keyword k) v))))
             {}
             (:query-params request)))
