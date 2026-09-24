(ns com.ruoyi.frontend.storage
  "浏览器 localStorage 的唯一入口。
   - 键名统一为 <前缀>_<名称>(如 :layout-settings → \"ruoyi_layout_settings\"),前缀随 bb rename 一起改;
   - 隐私模式、存储被禁用等环境下 localStorage 会抛异常,这里统一吞掉并返回 nil。"
  (:require
   [clojure.string :as str]))

(def prefix "ruoyi_")

(defn storage-key
  "关键字 → 实际存储键名。"
  [k]
  (str prefix (str/replace (name k) "-" "_")))

(defn get-item
  "读取字符串值;不存在或不可用时返回 nil。"
  [k]
  (try (.getItem js/localStorage (storage-key k))
       (catch :default _ nil)))

(defn set-item!
  "写入字符串值(关键字会取 name)。"
  [k v]
  (try (.setItem js/localStorage (storage-key k) (if (keyword? v) (name v) (str v)))
       (catch :default _ nil)))

(defn remove-item! [& ks]
  (doseq [k ks]
    (try (.removeItem js/localStorage (storage-key k))
         (catch :default _ nil))))

(defn get-json
  "读取 JSON 并转成关键字化的 Clojure 数据;解析失败返回 nil。"
  [k]
  (when-let [raw (get-item k)]
    (try (js->clj (.parse js/JSON raw) :keywordize-keys true)
         (catch :default _ nil))))

(defn set-json! [k v]
  (set-item! k (.stringify js/JSON (clj->js v))))
