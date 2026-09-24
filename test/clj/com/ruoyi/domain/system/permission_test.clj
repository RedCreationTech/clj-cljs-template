(ns com.ruoyi.domain.system.permission-test
  "权限标识计算与匹配。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.domain.system.permission :as permission]))

(deftest test-rows->permissions
  (testing "合并各角色菜单上的标识,逗号分隔、去空白、去重"
    (is (= #{"system:user:list" "system:user:add" "system:user:edit"}
           (permission/rows->permissions [{:role_key "a" :perms "system:user:list"}
                                          {:role_key "a" :perms " system:user:add , system:user:edit "}
                                          {:role_key "b" :perms "system:user:list"}
                                          {:role_key "b" :perms nil}
                                          {:role_key "b" :perms ""}]))))
  (testing "含 admin 角色时只返回通配权限"
    (is (= #{"*:*:*"} (permission/rows->permissions [{:role_key "common" :perms "x:y:z"}
                                                     {:role_key "admin" :perms nil}]))))
  (testing "没有角色时为空集"
    (is (= #{} (permission/rows->permissions [])))))

(deftest test-user-permissions
  (testing "未登录(无 user-id)不查库"
    (is (= #{} (permission/user-permissions (fn [& _] (throw (Exception. "不应调用"))) nil))))
  (testing "按用户查询"
    (is (= #{"a:b:c"} (permission/user-permissions
                       (fn [q {:keys [user_id]}]
                         (is (= :list-user-role-perms q))
                         (is (= 7 user_id))
                         [{:role_key "r" :perms "a:b:c"}])
                       7)))))

(deftest test-permitted?
  (is (permission/permitted? #{"a:b:c"} "a:b:c"))
  (is (not (permission/permitted? #{"a:b:c"} "a:b:d")))
  (is (permission/permitted? #{"a:b:c"} ["x:y:z" "a:b:c"]) "集合满足任一即可")
  (is (not (permission/permitted? #{} ["x:y:z"])))
  (is (permission/permitted? #{"*:*:*"} "任何:权限:都行") "通配权限")
  (is (not (permission/permitted? #{"system:user:*"} "system:user:add")) "只支持完整通配 *:*:*,与若依一致"))
