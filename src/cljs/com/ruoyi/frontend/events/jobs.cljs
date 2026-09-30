(ns com.ruoyi.frontend.events.jobs
  "在线用户、定时任务与任务日志事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.jobs :as jobs-api]
   [com.ruoyi.frontend.api.monitor :as monitor-api]
   [com.ruoyi.frontend.events.common :as ec]
   [re-frame.core :as rf]))

(rf/reg-event-db :online-users/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:online-users :items] items)
                         (assoc-in [:online-users :total] total)
                         (assoc-in [:online-users :loading?] false)))))

(rf/reg-event-fx :online-users/fetch
                 (fn [{:keys [db]} [_ overrides]]
                   (ec/fetch-with-query db :online-users :api/list-online-users overrides)))

(rf/reg-event-fx :online-users/change-page
                 (fn [_ [_ page page-size]]
                   {:dispatch [:online-users/fetch {:page page :size page-size}]}))

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
                                           (rf/dispatch [:online-users/fetch])))
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
                 (fn [_ [_ filters]]
                   {:dispatch [:jobs/fetch (merge {:page 1} filters)]}))

(rf/reg-event-fx :jobs/change-page
                 (fn [_ [_ page page-size]]
                   {:dispatch [:jobs/fetch {:page page :size page-size}]}))

(rf/reg-event-fx :jobs/fetch
                 (fn [{:keys [db]} [_ overrides]]
                   (ec/fetch-with-query db :jobs :api/list-jobs overrides)))

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
                                      (rf/dispatch [:jobs/fetch])))
                                  (fn [_]))))

(rf/reg-event-fx :jobs/update
                 (fn [_ [_ id params]]
                   {:api/update-job [id params]}))

(rf/reg-fx :api/update-job
           (fn [[id params]]
             (jobs-api/update-job id params
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (rf/dispatch [:jobs/fetch])))
                                  (fn [_]))))

(rf/reg-event-fx :jobs/change-status
                 (fn [_ [_ id status]]
                   {:api/change-job-status [id status]}))

(rf/reg-fx :api/change-job-status
           (fn [[id status]]
             (jobs-api/change-job-status id status
                                         (fn [result]
                                           (when (= 200 (:code result))
                                             (rf/dispatch [:jobs/fetch])))
                                         (fn [_]))))

(rf/reg-event-fx :jobs/delete
                 (fn [_ [_ id]]
                   {:api/delete-job id}))

(rf/reg-fx :api/delete-job
           (fn [id]
             (jobs-api/delete-job id
                                  (fn [result]
                                    (when (= 200 (:code result))
                                      (rf/dispatch [:jobs/fetch])))
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
                                    (fn [_]))))

(rf/reg-event-db :job-logs/set-list
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:job-logs :items] (:rows data))
                       (assoc-in [:job-logs :total] (:total data))
                       (assoc-in [:job-logs :loading?] false))))

(rf/reg-event-fx :job-logs/fetch
                 (fn [{:keys [db]} [_ overrides]]
                   (ec/fetch-with-query db :job-logs :api/list-job-logs overrides)))

(rf/reg-event-fx :job-logs/change-page
                 (fn [_ [_ page page-size]]
                   {:dispatch [:job-logs/fetch {:page page :size page-size}]}))

(rf/reg-fx :api/list-job-logs
           (fn [params]
             (jobs-api/list-job-logs params
                                     (fn [result]
                                       (when (= 200 (:code result))
                                         (rf/dispatch [:job-logs/set-list (:data result)])))
                                     (fn [_]))))
