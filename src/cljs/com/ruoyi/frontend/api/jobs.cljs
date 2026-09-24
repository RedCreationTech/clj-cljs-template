(ns com.ruoyi.frontend.api.jobs
  "定时任务接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-jobs
  "获取定时任务列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/job" :params params
              :on-success on-success :on-error on-error}))

(defn list-job-logs
  "获取定时任务日志。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/job-log" :params params
              :on-success on-success :on-error on-error}))

(defn create-job
  "新增定时任务。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/job" :params params
              :on-success on-success :on-error on-error}))

(defn update-job
  "更新定时任务。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/job/" id) :params params
              :on-success on-success :on-error on-error}))

(defn change-job-status
  "暂停(\"1\")/恢复(\"0\")任务:同时更新调度器。"
  [job-id status on-success on-error]
  (t/request {:method :put :uri (str "/system/job/" job-id "/changeStatus") :params {:status status}
              :on-success on-success :on-error on-error}))

(defn delete-job
  "删除定时任务。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/job/" id)
              :on-success on-success :on-error on-error}))

(defn run-job-once
  "立即执行一次定时任务。"
  [job-id on-success on-error]
  (t/request {:method :put :uri (str "/system/job/" job-id "/run")
              :on-success on-success :on-error on-error}))
