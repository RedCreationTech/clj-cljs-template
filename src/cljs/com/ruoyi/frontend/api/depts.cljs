(ns com.ruoyi.frontend.api.depts
  "部门管理相关接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-depts
  "获取部门列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/dept" :params params
              :on-success on-success :on-error on-error}))

(defn create-dept
  "新增部门。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/dept" :params params
              :on-success on-success :on-error on-error}))

(defn update-dept
  "更新部门。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/dept/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-dept
  "删除部门。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/dept/" id)
              :on-success on-success :on-error on-error}))

(defn change-dept-status
  "修改部门状态。"
  [id status on-success on-error]
  (t/request {:method :put :uri (str "/system/dept/" id) :params {:status status}
              :on-success on-success :on-error on-error}))
