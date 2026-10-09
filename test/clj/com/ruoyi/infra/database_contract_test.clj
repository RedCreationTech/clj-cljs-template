(ns com.ruoyi.infra.database-contract-test
  "同一契约在 SQLite/MySQL/PostgreSQL CI 执行;测试数据事务回滚。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.config :as config]
   [com.ruoyi.domain.system.post :as post]
   [com.ruoyi.domain.system.log :as logs]
   [com.ruoyi.infra.clock :as clock]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.json :as json]
   [conman.core :as conman]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]))

(defn- queries [ds]
  (let [bound (conman/bind-connection-map ds {} "sql/system.sql" "sql/log.sql" "sql/job.sql")]
    (clock/with-now
      (fn
        ([q params] ((:fn (get (:fns bound) q)) ds params))
        ([conn q params] ((:fn (get (:fns bound) q)) conn params))))))

(defn- with-database [f]
  (let [cfg (config/system-config {:profile :test})
        ds (jdbc/get-datasource {:jdbcUrl (get-in cfg [:db.sql/connection :jdbc-url])})]
    (migratus/migrate {:store :database :db {:datasource ds}
                       :migration-dir (get-in cfg [:db.sql/migrations :migration-dir])})
    (jdbc/with-transaction [tx ds {:rollback-only true}]
      (let [q (queries ds)]
        (f tx q (fn [query params] (q tx query params)))))))

(defn- post-contract [tx q query]
  (let [ctx {:db tx :query-fn q}
        id (post/create-post! ctx {:post_code "contract_%" :post_name "中文_%岗位"
                                   :post_sort 101 :status "0" :create_by "contract"})
        control (post/create-post! ctx {:post_code "plain" :post_name "plain"
                                        :post_sort 102 :status "0" :create_by "contract"})
        read-post #(query :find-post-by-id {:post_id id})]
    (is (and (integer? id) (pos? id)))
    (is (= "中文_%岗位" (:post_name (read-post))))
    (is (re-matches #"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}" (:create_time (json/read-str (json/write-str (read-post))))))
    (doseq [needle [nil "中文" "_" "%"]]
      (let [filters {:post_code nil :post_name needle :status nil :page_size 1000 :offset 0}]
        (is (some #(= id (:post_id %)) (query :list-posts filters)))
        (is (pos? (:total (query :count-posts filters))))
        (when (#{"_" "%"} needle)
          (is (not-any? #(= control (:post_id %)) (query :list-posts filters))))))
    (is (empty? (query :list-posts {:post_code nil :post_name "contract-missing"
                                    :status nil :page_size 1 :offset 0})))
    (post/update-post! {:query-fn query} {:post_id id :post_name "updated" :update_by "contract"})
    (is (= "updated" (:post_name (read-post))))
    (is (= 101 (:post_sort (read-post))) "COALESCE 的空数字绑定保持原值")
    (post/delete-post! {:query-fn query} id)
    (is (nil? (read-post)))))

(defn- type-contract [tx q query]
  (let [json "{\"text\":\"中文_%\",\"enabled\":true,\"nested\":[1,null]}"
        id (db/insert-and-get-id! q tx :create-form-template!
                                  {:form_name "contract" :form_key "contract" :schema_json json
                                   :remark nil :create_by "contract"})]
    (is (= json (:schema_json (query :find-form-template-by-id {:id id})))))
  (let [id (db/insert-and-get-id! q tx :create-role!
                                  {:role_name "contract" :role_key "contract" :role_sort 1
                                   :data_scope "1" :menu_check_strictly true :dept_check_strictly false
                                   :status "0" :create_by "contract" :remark nil})
        role (query :find-role-by-id {:role_id id})]
    (is (contains? #{true 1} (:menu_check_strictly role)))
    (is (contains? #{false 0} (:dept_check_strictly role)))))

(defn- job-contract [tx q query]
  (let [id (db/insert-and-get-id! q tx :create-job!
                                  {:job_name "contract" :job_group "DEFAULT"
                                   :invoke_target "contract" :cron_expression "0 0 0 * * ?"
                                   :misfire_policy "1" :concurrent "1" :status "1"
                                   :create_by "contract" :remark nil}
                                  :last-insert-job-id :job_id)]
    (is (= "contract" (:job_name (query :find-job-by-id {:job_id id}))))))

(deftest database-contract-test
  (with-database
    (fn [tx q query]
      (testing "种子数据存在;新增身份不会撞种子主键"
        (is (= "admin" (:user_name (query :find-user-by-name {:user_name "admin"}))))
        (post-contract tx q query))
      (testing "HTTP 字符串数值筛选与日期范围"
        (is (map? (logs/list-oper-logs {:query-fn query}
                                       {:business_type "1" :status "0"
                                        :begin_time "2000-01-01" :end_time "2100-01-01"}))))
      (testing "JSON 文本和既有 boolean 语义" (type-contract tx q query))
      (testing "自定义 job 主键查询分支" (job-contract tx q query)))))
