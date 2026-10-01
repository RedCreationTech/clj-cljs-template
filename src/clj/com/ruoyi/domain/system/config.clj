(ns com.ruoyi.domain.system.config
  "参数配置领域服务。"
  (:require
   [com.ruoyi.domain.paging :as paging]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.errors :as errors]))

(defn list-configs
  "查询参数配置列表(分页,返回 {:rows :total})。"
  [{:keys [query-fn]} params]
  (paging/paginate query-fn :list-configs :count-configs
                   {:config_name nil :config_key nil :config_type nil} params))

(defn find-config-by-id
  "根据ID查询配置。"
  [{:keys [query-fn]} config-id]
  (query-fn :find-config-by-id {:config_id config-id}))

(defn find-config-by-key
  "根据键名查询配置值。"
  [{:keys [query-fn]} config-key]
  (query-fn :find-config-by-key {:config_key config-key}))

(defn- ensure-unique-config-key!
  "config_key 上有唯一索引,重复插入会被数据库拒绝;先给出中文提示。"
  [{:keys [query-fn]} config-key current-config-id]
  (when-let [existing (and (seq config-key)
                           (query-fn :find-config-by-key {:config_key config-key}))]
    (when (not= (:config_id existing) current-config-id)
      (errors/fail! "参数键名已存在" {:config_key config-key}))))

(defn create-config!
  "创建参数配置。"
  [{:keys [query-fn db] :as ctx} params]
  (ensure-unique-config-key! ctx (:config_key params) nil)
  (db/insert-and-get-id! query-fn db :create-config!
                         (merge {:config_name nil :config_key nil :config_value nil :config_type nil :remark nil :create_by nil} params)))

(defn update-config!
  "更新参数配置。"
  [{:keys [query-fn] :as ctx} params]
  (ensure-unique-config-key! ctx (:config_key params) (:config_id params))
  (query-fn :update-config! (merge {:config_id nil :config_name nil :config_key nil :config_value nil :config_type nil :remark nil :update_by nil} params)))

(defn delete-config!
  "删除参数配置。"
  [{:keys [query-fn]} config-id]
  (query-fn :delete-config! {:config_id config-id}))
