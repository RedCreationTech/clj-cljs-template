(ns com.ruoyi.web.controllers.system.profile-test
  "个人中心控制器测试。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.security :as security]
   [com.ruoyi.web.controllers.system.profile :as profile]
   [com.ruoyi.web.controller-test-helper :as eh])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(defn mock-user-service
  "返回指定用户的 mock 用户服务。"
  [{:keys [password]}]
  {:query-fn (fn [q p]
               (case q
                 :find-user-by-id {:user_id (:user_id p)
                                   :user_name "admin"
                                   :nick_name "管理员"
                                   :avatar "/uploads/avatar/default.png"
                                   :email "admin@ruoyi.vip"
                                   :phonenumber "13800138000"
                                   :sex "0"
                                   :password password}
                 :list-roles-by-user-id [{:role_id 1 :role_name "超级管理员"}]
                 :list-posts-by-user-id [{:post_id 1 :post_name "董事长"}]
                 :update-user! nil
                 nil))})

(deftest test-get-profile
  (testing "获取当前用户个人信息"
    (let [user-service (mock-user-service {:password (security/hash-password "admin123")})
          request {:identity {:user-id 1}}
          response (profile/get-profile {:user-service user-service} request)]
      (is (map? response))
      (is (= 200 (get-in response [:body :code]))))))

(deftest test-get-profile-not-found
  (testing "获取个人信息时用户不存在"
    (let [user-service {:query-fn (fn [q _]
                                    (case q
                                      :find-user-by-id nil
                                      nil))}
          request {:identity {:user-id 999}}
          response (profile/get-profile {:user-service user-service} request)]
      (is (map? response))
      (is (= 500 (get-in response [:body :code]))))))

(deftest test-update-profile
  (testing "只更新昵称/手机/邮箱/性别;状态、部门、密码等字段被忽略"
    (let [updates (atom [])
          user-service {:query-fn (fn [q p]
                                    (when (= q :update-user!) (swap! updates conj p))
                                    nil)}
          request {:identity {:user-id 1 :user-name "admin"}
                   :body-params {:nick_name "新昵称" :email "new@ruoyi.vip"
                                 :status "1" :dept_id 9 :password "hack" :user_id 2}}
          response (profile/update-profile {:user-service user-service} request)
          p (first @updates)]
      (is (= 200 (get-in response [:body :code])))
      (is (= "新昵称" (:nick_name p)))
      (is (= 1 (:user_id p)) "只能改自己")
      (is (nil? (:status p)))
      (is (nil? (:dept_id p)))
      (is (nil? (:password p)) "改密码必须走 /password 并核对旧密码"))))

(defn- with-avatar-dir
  "在临时上传目录里跑测试:头像落在它下面的 avatar/。f 是 (fn [ctx dir])。"
  [f]
  (let [dir (.toFile (Files/createTempDirectory "avatar-test" (make-array FileAttribute 0)))]
    (try
      (f {:upload-config {:dir (.getPath dir)}} dir)
      (finally
        (doseq [x (file-seq dir)] (.delete x))
        (.delete dir)))))

(defn- upload [filename content]
  (let [tmp (.toFile (Files/createTempFile "upload" ".bin" (make-array FileAttribute 0)))]
    (spit tmp content)
    {:filename filename :tempfile tmp :size (.length tmp)}))

(deftest test-upload-avatar
  (with-avatar-dir
    (fn [ctx dir]
      (let [user-service (mock-user-service {:password (security/hash-password "admin123")})
            call #(profile/upload-avatar (merge {:user-service user-service} ctx)
                                         {:identity {:user-id 1} :params {:avatarfile %}})
            avatar-dir (io/file dir "avatar")]
        (testing "图片保存到头像目录,返回公开访问地址"
          (let [response (call (upload "me.png" "fake image content"))
                url (get-in response [:body :data :avatar])]
            (is (= 200 (get-in response [:body :code])))
            (is (re-matches #"/api/common/avatar/\d+_me\.png" url))
            (is (= 1 (count (.listFiles avatar-dir))))
            (is (= ["avatar"] (vec (.list dir)))
                "落在 avatar/ 子目录,不污染上传根目录")))
        (testing "不是图片:拒绝"
          (let [response (call (upload "run.exe" "MZ"))]
            (is (= 400 (get-in response [:body :code])))
            (is (= "不支持的文件类型:exe" (get-in response [:body :msg])))))
        (testing "超过 2MB:拒绝"
          (is (= 400 (get-in (call (assoc (upload "big.png" "x") :size (* 3 1024 1024))) [:body :code]))))
        (testing "未选择文件"
          (is (= "请选择要上传的文件" (get-in (call nil) [:body :msg]))))))))

(deftest test-change-password
  (testing "修改当前用户密码成功"
    (let [old-password "admin123"
          user-service (mock-user-service {:password (security/hash-password old-password)})
          request {:identity {:user-id 1}
                   :body-params {:old_password old-password
                                 :new_password "newpass123"}}
          response (profile/change-password {:user-service user-service} request)]
      (is (map? response))
      (is (= 200 (get-in response [:body :code]))))))

(deftest test-change-password-blank
  (testing "修改密码时旧密码或新密码为空"
    (let [user-service (mock-user-service {:password (security/hash-password "admin123")})
          request {:identity {:user-id 1}
                   :body-params {:old_password ""
                                 :new_password "newpass123"}}
          response (eh/call profile/change-password {:user-service user-service} request)]
      (is (= 200 (:status response)) "业务失败仍是 HTTP 200")
      (is (= 500 (get-in response [:body :code])))
      (is (= "旧密码和新密码不能为空" (get-in response [:body :msg]))))))

(deftest test-change-password-wrong-old
  (testing "修改密码时旧密码错误"
    (let [user-service (mock-user-service {:password (security/hash-password "admin123")})
          request {:identity {:user-id 1}
                   :body-params {:old_password "wrongpass"
                                 :new_password "newpass123"}}
          response (eh/call profile/change-password {:user-service user-service} request)]
      (is (= 200 (:status response)) "业务失败仍是 HTTP 200")
      (is (= 500 (get-in response [:body :code])))
      (is (= "旧密码错误" (get-in response [:body :msg]))))))
