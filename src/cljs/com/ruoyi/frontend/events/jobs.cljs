(ns com.ruoyi.frontend.events.jobs
  "在线用户、定时任务与任务日志事件。"
  (:require
   [clojure.string]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.jobs :as jobs-api]
   [com.ruoyi.frontend.api.monitor :as monitor-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :online-users/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:online-users :items] items)
                         (assoc-in [:online-users :total] total)
                         (assoc-in [:online-users :loading?] false)))))

(rf/reg-event-fx :online-users/search
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:online-users :loading?] true)
                    :api/list-online-users-search params}))

(rf/reg-fx :api/list-online-users-search
           (fn [params]
             (monitor-api/list-online-users {}
                                            (fn [result]
                                              (when (= 200 (:code result))
                                                (let [data (:data result)
                                                      items (if (sequential? data) data (:rows data []))
                                                      filtered (cond->> items
                                                                 (:user_name params)
                                                                 (filter #(clojure.string/includes?
                                                                           (or (get % "user-name" (:user_name %)) "")
                                                                           (:user_name params))))]
                                                  (rf/dispatch [:online-users/set-list {:rows filtered :total (count filtered)}]))))
                                            (fn [_]))))

(rf/reg-event-fx :online-users/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:online-users :loading?] true)
                    :api/list-online-users params}))

(rf/reg-fx :api/list-online-users
           (fn [params]
             (monitor-api/list-online-users params
                                            (fn [result]
                                              (when (= 200 (:code result))
                                                (rf/dispatch [:online-users/set-list (:data result)])))
                                            (fn [_]))))

(rf/reg-event-fx :online-users/force-logout
                 (fn [_ [_ token-id]]
                   {:api/force-logout token-id}))

(rf/reg-fx :api/force-logout
           (fn [token-id]
             (monitor-api/force-logout token-id
                                       (fn [result]
                                         (when (= 200 (:code result))
                                           (rf/dispatch [:online-users/fetch {}])))
                                       (fn [_]))))

(rf/reg-event-db :jobs/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:jobs :items] items)
                         (assoc-in [:jobs :total] total)
                         (assoc-in [:jobs :loading?] false)))))

(rf/reg-event-fx :jobs/search
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:jobs :loading?] true)
                    :api/list-jobs-search params}))

(rf/reg-fx :api/list-jobs-search
           (fn [params]
             (jobs-api/list-jobs {}
                                 (fn [result]
                                   (when (= 200 (:code result))
                                     (let [data (:data result)
                                           items (if (sequential? data) data (:rows data []))
                                           filtered (cond->> items
                                                      (:job_name params)
                                                      (filter #(clojure.string/includes?
                                                                (or (:job_name %) "")
                                                                (:job_name params)))
                                                      (:job_group params)
                                                      (filter #(clojure.string/includes?
                                                                (or (:job_group %) "")
                                                                (:job_group params))))]
                                       (rf/dispatch [:jobs/set-list {:rows filtered :total (count filtered)}]))))
                                 (fn [_]))))

(rf/reg-event-fx :jobs/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:jobs :loading?] true)
                    :api/list-jobs params}))

(rf/reg-fx :api/list-jobs
           (fn [params]
             (jobs-api/list-jobs params
                                 (fn [result]
                                   (when (= 200 (:code result))
                                     (rf/dispatch [:jobs/set-list (:data result)])))
                                 (fn [_]))))

(rf/reg-event-fx :jobs/create
                 (fn [_ [_ params]]
                   {:api/create-job params}))

(rf/reg-fx :api/create-job
           (fn [params]
             (jobs-api/create-job params
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (rf/dispatch [:jobs/fetch {}])))
                                  (fn [_]))))

(rf/reg-event-fx :jobs/update
                 (fn [_ [_ id params]]
                   {:api/update-job [id params]}))

(rf/reg-fx :api/update-job
           (fn [[id params]]
             (jobs-api/update-job id params
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (rf/dispatch [:jobs/fetch {}])))
                                  (fn [_]))))

(rf/reg-event-fx :jobs/delete
                 (fn [_ [_ id]]
                   {:api/delete-job id}))

(rf/reg-fx :api/delete-job
           (fn [id]
             (jobs-api/delete-job id
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (rf/dispatch [:jobs/fetch {}])))
                                  (fn [_]))))

(rf/reg-event-fx :jobs/run-once
                 (fn [_ [_ job-id]]
                   {:api/run-job-once job-id}))

(rf/reg-fx :api/run-job-once
           (fn [job-id]
             (jobs-api/run-job-once job-id
                                    (fn [result]
                                      (when (= 200 (:code result))
                                        (antd/success! "执行成功")))
                                    (fn [_] (antd/error! "执行失败")))))

(rf/reg-event-db :job-logs/set-list
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:job-logs :items] (:rows data))
                       (assoc-in [:job-logs :total] (:total data))
                       (assoc-in [:job-logs :loading?] false))))

(rf/reg-event-fx :job-logs/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:job-logs :loading?] true)
                    :api/list-job-logs params}))

(rf/reg-fx :api/list-job-logs
           (fn [params]
             (jobs-api/list-job-logs params
                                     (fn [result]
                                       (when (= 200 (:code result))
                                         (rf/dispatch [:job-logs/set-list (:data result)])))
                                     (fn [_]))))
