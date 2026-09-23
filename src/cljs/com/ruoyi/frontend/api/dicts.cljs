(ns com.ruoyi.frontend.api.dicts
  "字典类型与字典数据接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-dict-types
  "获取字典类型列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/dict/type" :params params
              :on-success on-success :on-error on-error}))

(defn list-dict-data
  "获取字典数据列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/dict/data" :params params
              :on-success on-success :on-error on-error}))

(defn create-dict-type
  "新增字典类型。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/dict/type" :params params
              :on-success on-success :on-error on-error}))

(defn update-dict-type
  "更新字典类型。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/dict/type/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-dict-type
  "删除字典类型。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/dict/type/" id)
              :on-success on-success :on-error on-error}))

(defn create-dict-data
  "新增字典数据。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/dict/data" :params params
              :on-success on-success :on-error on-error}))

(defn update-dict-data
  "更新字典数据。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/dict/data/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-dict-data
  "删除字典数据。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/dict/data/" id)
              :on-success on-success :on-error on-error}))
