(ns com.ruoyi.web.controllers.job
  "定时任务控制器。"
  (:require
   [clojure.walk :as walk]
   [com.ruoyi.domain.paging :as paging]
   [com.ruoyi.infra.cron :as cron]
   [com.ruoyi.infra.db :as db]
   [com.ruoyi.infra.errors :as errors]
   [com.ruoyi.infra.scheduler :as scheduler-core]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- current-user-name [request]
  (get-in request [:identity :user-name] ""))

(defn- body-params [request]
  (walk/keywordize-keys (:body-params request {})))

(defn list-jobs
  [{:keys [query-fn]} request]
  (res/ok (paging/paginate query-fn :list-jobs :count-jobs
                           {:job_name nil :job_group nil :status nil}
                           (params/query request))))

(defn get-job
  [{:keys [query-fn]} request]
  (let [job-id (parse-long (get-in request [:path-params :id]))]
    (if-let [job (query-fn :find-job-by-id {:job_id job-id})]
      (res/ok job)
      (res/fail "任务不存在"))))

(defn- validate-job! [job]
  (when-not (cron/valid? (:cron_expression job))
    (errors/fail! "cron 表达式不合法"))
  (when-not (scheduler-core/invoke-target-allowed? (:invoke_target job))
    (errors/fail! "调用目标不合法或不在允许命名空间内")))

(defn create-job
  [{:keys [query-fn db]} request]
  (let [params (-> {:job_name nil :job_group nil :invoke_target nil :cron_expression nil
                    :misfire_policy nil :concurrent nil :status nil :remark nil :create_by nil}
                   (merge (body-params request))
                   (assoc :create_by (current-user-name request)))
        _ (validate-job! params)
        id (db/insert-and-get-id! query-fn db :create-job! params :last-insert-job-id :job_id)]
    (when-let [job (query-fn :find-job-by-id {:job_id id})]
      (scheduler-core/schedule-job! job))
    (res/ok {:job_id id})))

(defn update-job
  [{:keys [query-fn]} request]
  (let [job-id (parse-long (get-in request [:path-params :id]))
        params (-> {:job_name nil :job_group nil :invoke_target nil :cron_expression nil
                    :misfire_policy nil :concurrent nil :status nil :remark nil :update_by nil}
                   (merge (body-params request))
                   (assoc :job_id job-id)
                   (assoc :update_by (current-user-name request)))
        _ (validate-job! params)]
    (query-fn :update-job! params)
    (when-let [job (query-fn :find-job-by-id {:job_id job-id})]
      (scheduler-core/reschedule-job! job))
    (res/ok "更新成功")))

(defn delete-job
  [{:keys [query-fn]} request]
  (let [job-id (parse-long (get-in request [:path-params :id]))
        job (query-fn :find-job-by-id {:job_id job-id})]
    (query-fn :delete-job! {:job_id job-id})
    (when job
      (scheduler-core/unschedule-job! job-id (:job_group job)))
    (res/ok "删除成功")))

(defn list-job-logs
  [{:keys [query-fn]} request]
  (res/ok (paging/paginate query-fn :list-job-logs :count-job-logs
                           {:job_name nil :job_group nil :status nil}
                           (params/query request))))

(defn execute-job
  [{:keys [query-fn]} request]
  (let [job-id (parse-long (get-in request [:path-params :id]))]
    (query-fn :execute-job! {:job_id job-id})
    (res/ok "执行成功")))

(defn change-status
  "修改任务状态。"
  [{:keys [query-fn]} request]
  (let [job-id (parse-long (get-in request [:path-params :id]))
        status (:status (body-params request))
        job (query-fn :find-job-by-id {:job_id job-id})]
    (query-fn :update-job! (merge {:job_id nil :job_name nil :job_group nil :invoke_target nil
                                   :cron_expression nil :misfire_policy nil :concurrent nil
                                   :status nil :remark nil :update_by nil}
                                  {:job_id job-id :status status :update_by (current-user-name request)}))
    (when job
      (if (= "0" status)
        (scheduler-core/resume-job! job-id (:job_group job))
        (scheduler-core/pause-job! job-id (:job_group job))))
    (res/ok "状态修改成功")))

(defn run-once
  "立即执行一次任务。"
  [_ request]
  (let [job-id (parse-long (get-in request [:path-params :id]))]
    (scheduler-core/trigger-job! job-id "DEFAULT")
    (res/ok (str "任务 " job-id " 已触发执行"))))

(defn clean-logs
  "清空任务日志。"
  [{:keys [query-fn]} _]
  (query-fn :clean-job-logs! {})
  (res/ok "日志已清空"))
