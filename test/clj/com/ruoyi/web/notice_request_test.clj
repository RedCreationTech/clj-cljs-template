(ns com.ruoyi.web.notice-request-test
  "顶部铃铛:未读数按用户计算,标记已读只影响自己。"
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

(defn- auth [user password]
  {"authorization" (str "Bearer " (get-in (parse-json (POST "/api/auth/login" {:username user :password password} {}))
                                          [:data :token]))})

(defn- bell [headers] (:data (parse-json (GET (handler) "/api/system/notice/latest" {} headers))))

(deftest notice-read-per-user-test
  (let [admin (auth "admin" "admin123")
        other-name (str "reader" (System/currentTimeMillis))
        _ (POST "/api/system/user" {:user_name other-name :nick_name "读者" :password "reader123"} admin)
        other (auth other-name "reader123")
        title (str "通知" (System/currentTimeMillis))]
    (PUT (handler) "/api/system/notice/read-all" {} admin)
    (PUT (handler) "/api/system/notice/read-all" {} other)
    (POST "/api/system/notice" {:notice_name title :notice_type "1" :status "0"} admin)
    (testing "新通知对两个用户都是未读"
      (is (= 1 (:unread (bell admin))))
      (is (= 1 (:unread (bell other))))
      (is (= [title 0] ((juxt :notice_name :is_read) (first (:rows (bell other)))))))
    (testing "一个用户标记已读不影响另一个"
      (is (= 200 (:code (parse-json (PUT (handler) "/api/system/notice/read-all" {} other)))))
      (is (= 0 (:unread (bell other))))
      (is (= 1 (:is_read (first (:rows (bell other))))))
      (is (= 1 (:unread (bell admin)))))
    (testing "未登录不能访问"
      (is (= 401 (:status (GET (handler) "/api/system/notice/latest" {} {})))))))
