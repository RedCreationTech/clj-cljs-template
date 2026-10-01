(ns com.ruoyi.domain.system.post
  "岗位领域服务。"
  (:require
   [com.ruoyi.domain.paging :as paging]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.errors :as errors]))

(defn list-posts
  "查询岗位列表(分页,返回 {:rows :total})。"
  [{:keys [query-fn]} params]
  (paging/paginate query-fn :list-posts :count-posts
                   {:post_code nil :post_name nil :status nil} params))

(defn find-post-by-id
  "根据ID查询岗位。"
  [{:keys [query-fn]} post-id]
  (query-fn :find-post-by-id {:post_id post-id}))

(defn create-post!
  "创建岗位。"
  [{:keys [query-fn db]} params]
  (db/insert-and-get-id! query-fn db :create-post!
                         (merge {:post_code nil :post_name nil :post_sort nil :status nil
                                 :remark nil :create_by nil}
                                params)))

(defn update-post!
  "更新岗位。"
  [{:keys [query-fn]} params]
  (query-fn :update-post! (merge {:post_code nil :post_name nil :post_sort nil :status nil :remark nil :update_by nil}
                                 params)))

(defn delete-post!
  "删除岗位。已分配给用户的岗位删不掉,把中文原因抛给异常中间件(HTTP 200 + {:code 500 :msg})。"
  [{:keys [query-fn]} post-id]
  (when (pos? (:total (query-fn :count-users-by-post {:post_id post-id})))
    (errors/fail! "该岗位已分配给用户,不能删除"))
  (query-fn :delete-post! {:post_id post-id}))
