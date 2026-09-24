(ns com.ruoyi.web.user-request-test
  "用户管理的回归测试(走真实路由与数据库):
   - 修改资料 / 切换状态不能把密码哈希再哈希一次(否则用户再也登录不了);
   - 列表与详情不返回密码哈希。"
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.test-utils :refer [GET PUT system-fixture system-state]]
   [peridot.core :as p]))

(use-fixtures :once (system-fixture))

(defn- handler [] (:handler/ring (system-state)))

(defn- parse-json [resp]
  (try (json/read-str (:body resp) :key-fn keyword) (catch Exception _ nil)))

(defn- POST [path body headers]
  (-> (p/session (handler))
      (p/request path :request-method :post :content-type "application/json"
                 :headers headers :body (json/write-str body))
      :response
      (update :body #(if (string? %) % (some-> % slurp)))))

(defn- login [username password]
  (get-in (parse-json (POST "/api/auth/login" {:username username :password password} {}))
          [:data :token]))

(deftest edit-user-keeps-password-test
  (let [admin {"authorization" (str "Bearer " (login "admin" "admin123"))}
        user-name (str "keep" (System/currentTimeMillis))
        user-id (some->> (POST "/api/system/user" {:user_name user-name :nick_name "原昵称" :password "keep123"} admin)
                         parse-json :data (re-find #"\d+") parse-long)]
    (is (some? (login user-name "keep123")) "新建后可以登录")
    (testing "修改资料后仍可用原密码登录"
      (is (= 200 (:code (parse-json (PUT (handler) (str "/api/system/user/" user-id) {:nick_name "新昵称"} admin)))))
      (is (some? (login user-name "keep123"))))
    (testing "停用再启用后仍可用原密码登录"
      (is (= 200 (:code (parse-json (PUT (handler) (str "/api/system/user/" user-id "/status/1") {} admin)))))
      (is (= 200 (:code (parse-json (PUT (handler) (str "/api/system/user/" user-id "/status/0") {} admin)))))
      (is (some? (login user-name "keep123"))))
    (testing "本人修改密码:旧密码校验、新密码生效"
      (let [self {"authorization" (str "Bearer " (login user-name "keep123"))}]
        (is (= "旧密码错误" (:msg (parse-json (PUT (handler) "/api/system/profile/password"
                                           {:old_password "wrong" :new_password "next456"} self)))))
        (is (= 200 (:code (parse-json (PUT (handler) "/api/system/profile/password"
                                        {:old_password "keep123" :new_password "next456"} self)))))
        (is (some? (login user-name "next456")))))
    (testing "详情与列表不返回密码哈希"
      (let [detail (:data (parse-json (GET (handler) (str "/api/system/user/" user-id) {} admin)))
            rows (get-in (parse-json (GET (handler) "/api/system/user" {} admin)) [:data :rows])]
        (is (= "新昵称" (:nick_name detail)))
        (is (not (contains? detail :password)))
        (is (seq rows))
        (is (not-any? #(contains? % :password) rows))))))

(deftest avatar-upload-and-public-access-test
  (testing "上传头像后,头像地址不带令牌也能访问;非图片被拒绝"
    (let [token (login "admin" "admin123")
          png (java.io.File/createTempFile "avatar" ".png")
          _ (spit png "not-really-a-png")
          upload (fn [file]
                   (-> (p/session (handler))
                       (p/request "/api/system/profile/avatar" :request-method :post
                                  :headers {"authorization" (str "Bearer " token)}
                                  :params {"avatarfile" file})
                       :response
                       (update :body #(if (string? %) % (some-> % slurp)))
                       parse-json))
          url (get-in (upload png) [:data :avatar])]
      (try
        (is (re-matches #"/api/common/avatar/.+\.png" (str url)))
        (let [resp (GET (handler) url {} {})]
          (is (= 200 (:status resp)))
          (is (= "image/png" (get-in resp [:headers "Content-Type"]))))
        (is (= 404 (:code (parse-json (GET (handler) "/api/common/avatar/..%2F..%2Fdeps.edn" {} {}))))
            "不能借头像接口读别的文件")
        (let [exe (java.io.File/createTempFile "evil" ".exe")]
          (is (= 400 (:code (upload exe))))
          (.delete exe))
        (finally
          (.delete png)
          (when url (.delete (java.io.File. (str "uploads/avatar/" (last (re-find #"avatar/(.+)$" url)))))))))))
