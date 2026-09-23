(ns com.ruoyi.frontend.api.profile
  "个人中心接口。"
  (:require
   [ajax.core :as ajax]
   [com.ruoyi.frontend.api.transport :as t]))

(defn get-profile
  "获取个人信息。"
  [on-success on-error]
  (t/request {:method :get :uri "/system/profile"
              :on-success on-success :on-error on-error}))

(defn update-profile
  "更新个人信息。"
  [params on-success on-error]
  (t/request {:method :put :uri "/system/profile" :params params
              :on-success on-success :on-error on-error}))

(defn change-password
  "修改密码。"
  [params on-success on-error]
  (t/request {:method :put :uri "/system/profile/password" :params params
              :on-success on-success :on-error on-error}))

(defn upload-avatar
  "上传头像。"
  [form-data on-success on-error]
  (ajax/ajax-request
   {:method :post
    :uri (str t/api-base "/system/profile/avatar")
    :body form-data
    :headers (when-let [token (t/get-token)]
               {"Authorization" (str "Bearer " token)})
    :response-format (ajax/json-response-format {:keywords? true})
    :handler (fn [[ok result]]
               (if ok
                 (on-success result)
                 (on-error result)))}))
