(ns com.ruoyi.frontend.perm-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.perm :as perm]))

(deftest permitted?-test
  (testing "不需要权限"
    (is (perm/permitted? #{} nil)))
  (testing "单个标识"
    (is (perm/permitted? #{"system:user:add"} "system:user:add"))
    (is (not (perm/permitted? #{"system:user:list"} "system:user:add"))))
  (testing "集合:满足任一即可"
    (is (perm/permitted? #{"system:user:edit"} ["system:user:add" "system:user:edit"]))
    (is (not (perm/permitted? #{} ["system:user:add"]))))
  (testing "通配权限"
    (is (perm/permitted? #{"*:*:*"} "anything:at:all"))))
