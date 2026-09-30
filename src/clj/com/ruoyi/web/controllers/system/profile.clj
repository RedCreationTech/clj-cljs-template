(ns com.ruoyi.web.controllers.system.profile
  "个人中心控制器。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.system.user :as user-service]
   [com.ruoyi.infra.files :as files]
   [ring.util.response :as response]))

(defn- ok
  ([data] (ok 200 "操作成功" data))
  ([code msg data]
   (-> (response/response {:code code :msg msg :data data})
       (response/content-type "application/json"))))

(defn- fail [msg]
  (-> (response/response {:code 500 :msg msg})
      (response/content-type "application/json")))

(defn get-profile
  "获取当前用户个人信息。"
  [{:keys [user-service]} request]
  (let [identity (:identity request)
        user-id (:user-id identity)]
    (if-let [user (user-service/find-user-by-id user-service user-id)]
      (ok (select-keys user [:user_id :user_name :nick_name :avatar :email :phonenumber :sex]))
      (fail "用户不存在"))))

(def ^:private editable-fields
  "个人中心只能改这些字段;状态、部门、密码等不能通过这里修改(改密码走 /password,要核对旧密码)。"
  [:nick_name :phonenumber :email :sex])

(defn update-profile
  "更新当前用户个人信息(昵称、手机、邮箱、性别)。"
  [{:keys [user-service]} request]
  (try
    (let [identity (:identity request)
          user-id (:user-id identity)
          params (assoc (select-keys (:body-params request) editable-fields)
                        :user-id user-id :update_by (:user-name identity ""))]
      (user-service/update-user! user-service params)
      (ok "更新成功"))
    (catch Exception e
      (fail (.getMessage e)))))

(defn upload-avatar
  "上传头像:只接受图片(见 infra.files/image-policy),保存到头像目录(见 infra.files/avatar-dir),
   通过公开接口 /api/common/avatar/<文件名> 访问。"
  [{:keys [user-service upload-config]} request]
  (let [{:keys [tempfile filename] :as file} (get-in request [:params :avatarfile])]
    (if-let [err (files/upload-error (files/image-policy upload-config) file)]
      (ok 400 err nil)
      (try
        (let [target (files/store! (files/avatar-dir upload-config)
                                   tempfile (str (System/currentTimeMillis) "_" filename))
              url (str "/api/common/avatar/" (.getName target))]
          (user-service/update-user! user-service {:user-id (get-in request [:identity :user-id]) :avatar url})
          (ok {:avatar url}))
        (catch Exception e
          (fail (.getMessage e)))))))

(defn change-password
  "修改当前用户密码。"
  [{:keys [user-service]} request]
  (try
    (let [identity (:identity request)
          user-id (:user-id identity)
          {:keys [old_password new_password]} (:body-params request)]
      (cond
        (or (str/blank? old_password) (str/blank? new_password)) (fail "旧密码和新密码不能为空")
        (not (user-service/password-matches? user-service user-id old_password)) (fail "旧密码错误")
        :else (do (user-service/update-user! user-service {:user-id user-id :password new_password})
                  (ok "密码修改成功"))))
    (catch Exception e
      (fail (.getMessage e)))))
