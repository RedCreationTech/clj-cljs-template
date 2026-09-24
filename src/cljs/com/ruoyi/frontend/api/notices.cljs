(ns com.ruoyi.frontend.api.notices
  "通知公告接口。"
  (:require
   [com.ruoyi.frontend.api.transport :as t]))

(defn list-notices
  "获取通知公告列表。"
  [params on-success on-error]
  (t/request {:method :get :uri "/system/notice" :params params
              :on-success on-success :on-error on-error}))

(defn create-notice
  "新增通知公告。"
  [params on-success on-error]
  (t/request {:method :post :uri "/system/notice" :params params
              :on-success on-success :on-error on-error}))

(defn update-notice
  "更新通知公告。"
  [id params on-success on-error]
  (t/request {:method :put :uri (str "/system/notice/" id) :params params
              :on-success on-success :on-error on-error}))

(defn delete-notice
  "删除通知公告。"
  [id on-success on-error]
  (t/request {:method :delete :uri (str "/system/notice/" id)
              :on-success on-success :on-error on-error}))

(defn latest-notices
  "顶部铃铛:最新通知与当前用户的未读数。后台请求,失败不提示。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/notice/latest" :silent? true
              :on-success on-success :on-error on-error}))

(defn read-all-notices
  "把当前所有已发布通知记为已读。"
  [on-success on-error]
  (t/request {:method :put :uri "/system/notice/read-all" :silent? true
              :on-success on-success :on-error on-error}))
