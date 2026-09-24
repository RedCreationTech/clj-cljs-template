(ns com.ruoyi.frontend.router-test
  "前端路由表:URL ↔ 页面关键字。"
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.router :as router]))

(deftest match-route-test
  (testing "已登记的路径匹配到页面关键字"
    (is (= :user (:handler (router/match-route "/system/user"))))
    (is (= :dashboard (:handler (router/match-route "/")))))
  (testing "未登记的路径不匹配"
    (is (nil? (router/match-route "/no/such/page")))))

(deftest page-path-test
  (is (= "/system/user" (router/page-path :user)))
  (is (= "/" (router/page-path :no-such-page)) "未知页面回到首页"))

(deftest page-names-cover-routes-test
  (testing "每个路由关键字都有页面名称(Tab 与面包屑要用)"
    (let [route-keys (set (vals (second router/routes)))]
      (is (empty? (remove router/page-names route-keys))))))
