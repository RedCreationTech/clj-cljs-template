(ns com.ruoyi.frontend.api.form-template
  "表单模板接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-form-templates
  "获取表单模板列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/form-template" :params params
              :on-success on-success :on-error on-error}))

(defn get-form-template
  "获取表单模板详情。"
  [id on-success on-error]
  (t/request {:method :get :uri (str "/system/form-template/" id)
              :on-success on-success :on-error on-error}))

(defn save-form-template
  "保存表单模板。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/form-template" :params params
              :on-success on-success :on-error on-error}))

(defn update-form-template
  "更新表单模板。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/form-template/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-form-template
  "删除表单模板。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/form-template/" id)
              :on-success on-success :on-error on-error}))
