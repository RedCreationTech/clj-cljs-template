(ns com.ruoyi.frontend.api.configs
  "参数配置接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-configs
  "获取参数配置列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/config" :params params
              :on-success on-success :on-error on-error}))

(defn create-config
  "新增参数配置。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/config" :params params
              :on-success on-success :on-error on-error}))

(defn update-config
  "更新参数配置。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/config/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-config
  "删除参数配置。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/config/" id)
              :on-success on-success :on-error on-error}))
