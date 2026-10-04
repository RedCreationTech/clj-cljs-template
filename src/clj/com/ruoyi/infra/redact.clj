(ns com.ruoyi.infra.redact
  "监控配置与调用追踪共用的脱敏规则，不影响实际传给组件的值。"
  (:require [clojure.string :as str]
            [integrant.core :as ig]))

(def ^:private marker "<redacted>")

(defn- key-name [key]
  (-> (if (instance? clojure.lang.Named key) (name key) (str key))
      str/lower-case
      (str/replace #"[^a-z0-9]" "")))

(defn secret-key?
  "匹配凭据字段，支持 keyword、字符串、CamelCase 与命名空间键。"
  [key]
  (let [key (key-name key)]
    (or (= key "pwd")
        (boolean (some #(str/includes? key %)
                       ["password" "passwd" "secret" "apikey" "privatekey"
                        "credential" "authorization" "cookie" "token"])))))

(defn url
  "脱敏 URL/JDBC 连接串中的用户信息密码与凭据查询参数。"
  [value]
  (when value
    (-> value
        (str/replace #"(?i)([a-z][a-z0-9+.-]*://[^/@\s:]+:)([^/@\s]*)(@)"
                     "$1<redacted>$3")
        (str/replace #"(?i)([?&;]|^-D|^)([a-z0-9_.-]*(?:password|passwd|secret|api[-_.]?key|private[-_.]?key|credential|authorization|cookie|token)[a-z0-9_.-]*|pwd)=([^&#;]*)"
                     "$1$2=<redacted>"))))

(defn config
  "保留配置结构与 Integrant 引用，凭据字段及连接串密码不可回到客户端。"
  [value]
  (cond
    (ig/ref? value) {:__ig_ref true :key (str (:key value))}
    (map? value) (into {} (map (fn [[key value]]
                                 [key (if (secret-key? key) marker (config value))])) value)
    (sequential? value) (mapv config value)
    (set? value) (into #{} (map config value))
    (string? value) (url value)
    (or (nil? value) (number? value) (boolean? value) (keyword? value)) value
    (fn? value) "<function>"
    :else (str "<" (.getName (class value)) ">")))

(def ^:private payload-keys
  #{"body" "bodyparams" "requestbody" "responsebody" "rawbody"
    "multipartparams" "formparams"})

(defn- private-key? [key extra-keys]
  (or (secret-key? key)
      (contains? payload-keys (key-name key))
      (contains? extra-keys (key-name key))))

(defn- short-string [value limit]
  (let [value (url value)]
    (if (> (count value) limit) (str (subs value 0 limit) "...") value)))

(declare snapshot-value)

(defn- snapshot-map [value options depth]
  (into {} (map (fn [[key value]]
                  [(str key) (if (private-key? key (:sensitive-keys options))
                               marker
                               (snapshot-value value options (inc depth) true))]))
        (take (:max-entries options) value)))

(defn- snapshot-value [value options depth named-field?]
  (cond
    (nil? value) nil
    (>= depth (:max-depth options)) "<depth-limit>"
    (map? value) (snapshot-map value options depth)
    (or (sequential? value) (set? value))
    (mapv #(snapshot-value % options (inc depth) false) (take (:max-entries options) value))
    (fn? value) "<function>"
    (instance? Throwable value) (str "<" (.getName (class value)) ">")
    (and (string? value) (not named-field?)) (str "<string length=" (count value) ">")
    (or (string? value) (number? value) (boolean? value) (keyword? value) (symbol? value))
    (short-string (pr-str value) (:max-string options))
    :else (str "<" (.getName (class value)) ">")))

(defn snapshot
  "生成有界 JSON 摘要；无字段名的字符串只留长度，额外敏感键不能关闭默认保护。"
  ([value] (snapshot value {}))
  ([value options]
   (let [options (-> (merge {:max-depth 8 :max-entries 100 :max-string 400} options)
                     (update :sensitive-keys #(into #{} (map key-name) %)))]
     (snapshot-value value options 0 false))))
