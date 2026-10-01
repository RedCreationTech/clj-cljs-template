(ns com.ruoyi.web.controllers.system.post-test
  "岗位控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.web.controller-test-helper :as eh]
   [com.ruoyi.web.controllers.system.post :as post]))

(def admin-identity
  {:user-id 1 :user-name "admin"})

(def mock-post-service
  {:query-fn (fn [q _p] (case q
                          :list-posts [{:post_id 1 :post_name "test"}]
                          :find-post-by-id {:post_id 1 :post_name "test"}
                          :create-post! [{:post_id 2}]
                          :last-insert-rowid {:last_insert_rowid 2}
                          :update-post! nil
                          :count-users-by-post {:total 0}
                          :delete-post! nil
                          []))})

(deftest test-list-posts
  (testing "查询岗位列表"
    (let [request {:query-params {}}
          response (post/list-posts {:post-service mock-post-service} request)]
      (is (map? response)))))

(deftest test-get-post
  (testing "获取岗位详情"
    (let [request {:path-params {:id "1"}}
          response (post/get-post {:post-service mock-post-service} request)]
      (is (map? response)))))

(deftest test-get-post-not-found
  (testing "获取不存在的岗位"
    (let [service {:query-fn (fn [q _p] (case q :find-post-by-id nil []))}
          request {:path-params {:id "999"}}
          response (post/get-post {:post-service service} request)]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (= "岗位不存在" (:msg (:body response)))))))

(deftest test-create-post
  (testing "创建岗位"
    (let [request {:body-params {:post_code "test" :post_name "test" :post_sort 1}
                   :identity admin-identity}
          response (post/create-post {:post-service mock-post-service} request)]
      (is (map? response)))))

(deftest test-update-post
  (testing "更新岗位"
    (let [request {:path-params {:id "1"} :body-params {:post_name "updated"}
                   :identity admin-identity}
          response (post/update-post {:post-service mock-post-service} request)]
      (is (map? response)))))

(deftest test-delete-post
  (testing "删除岗位"
    (let [request {:path-params {:id "1"}}
          response (post/delete-post {:post-service mock-post-service} request)]
      (is (map? response)))))

(deftest test-delete-assigned-post
  (testing "已分配给用户的岗位:删除被拒,中文原因原样回到前端"
    (let [service {:query-fn (fn [q _p] (case q :count-users-by-post {:total 2} nil))}
          response (eh/call post/delete-post {:post-service service} {:path-params {:id "2"}})]
      (is (= 200 (:status response)))
      (is (= {:code 500 :msg "该岗位已分配给用户,不能删除"} (:body response))))))

(deftest test-change-status
  (testing "修改岗位状态"
    (let [request {:path-params {:id "1"}
                   :body-params {:status "1"}
                   :identity admin-identity}
          response (post/change-status {:post-service mock-post-service} request)]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (= "状态修改成功" (:data (:body response)))))))
