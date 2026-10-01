(ns com.ruoyi.web.controllers.system.profile
  "个人中心控制器。"
  (:require
   [clojure.string :as str]
   [com.ruoyi.domain.system.user :as user-service]
   [com.ruoyi.infra.errors :as errors]
   [com.ruoyi.infra.files :as files]
   [com.ruoyi.web.response :as res]))

(defn get-profile
  "获取当前用户个人信息。"
  [{:keys [user-service]} request]
  (let [identity (:identity request)
        user-id (:user-id identity)]
    (if-let [user (user-service/find-user-by-id user-service user-id)]
      (res/ok (select-keys user [:user_id :user_name :nick_name :avatar :email :phonenumber :sex]))
      (res/fail "用户不存在"))))

(def ^:private editable-fields
  "个人中心只能改这些字段;状态、部门、密码等不能通过这里修改(改密码走 /password,要核对旧密码)。"
  [:nick_name :phonenumber :email :sex])

(defn update-profile
  "更新当前用户个人信息(昵称、手机、邮箱、性别)。"
  [{:keys [user-service]} request]
  (let [identity (:identity request)
        params (assoc (select-keys (:body-params request) editable-fields)
                      :user-id (:user-id identity) :update_by (:user-name identity ""))]
    (user-service/update-user! user-service params)
    (res/ok "更新成功")))

(defn upload-avatar
  "上传头像:只接受图片(见 infra.files/image-policy),保存到头像目录(见 infra.files/avatar-dir),
   通过公开接口 /api/common/avatar/<文件名> 访问。写盘失败让它抛出去(5xx 通用文案 + 服务端日志)。"
  [{:keys [user-service upload-config]} request]
  (let [{:keys [tempfile filename] :as file} (get-in request [:params :avatarfile])]
    (if-let [err (files/upload-error (files/image-policy upload-config) file)]
      (res/ok 400 err nil)
      (let [target (files/store! (files/avatar-dir upload-config)
                                 tempfile (str (System/currentTimeMillis) "_" filename))
            url (str "/api/common/avatar/" (.getName target))]
        (user-service/update-user! user-service {:user-id (get-in request [:identity :user-id]) :avatar url})
        (res/ok {:avatar url})))))

(defn change-password
  "修改当前用户密码。"
  [{:keys [user-service]} request]
  (let [user-id (get-in request [:identity :user-id])
        {:keys [old_password new_password]} (:body-params request)]
    (cond
      (or (str/blank? old_password) (str/blank? new_password)) (errors/fail! "旧密码和新密码不能为空")
      (not (user-service/password-matches? user-service user-id old_password)) (errors/fail! "旧密码错误")
      :else (do (user-service/update-user! user-service {:user-id user-id :password new_password})
                (res/ok "密码修改成功")))))
