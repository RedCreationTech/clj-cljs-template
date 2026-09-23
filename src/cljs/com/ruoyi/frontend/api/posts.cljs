(ns com.ruoyi.frontend.api.posts
  "岗位管理相关接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-posts
  "获取岗位列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/post" :params params
              :on-success on-success :on-error on-error}))

(defn create-post
  "新增岗位。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/post" :params params
              :on-success on-success :on-error on-error}))

(defn update-post
  "更新岗位。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/post/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-post
  "删除岗位。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/post/" id)
              :on-success on-success :on-error on-error}))

(defn change-post-status
  "修改岗位状态。"
  [id status on-success on-error]
  (t/request {:method :put :uri (str "/system/post/" id) :params {:status status}
              :on-success on-success :on-error on-error}))
