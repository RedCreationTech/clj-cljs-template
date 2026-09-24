(ns com.ruoyi.frontend.pages.layout.menu-build-test
  "后端菜单树 → antd Menu 结构与派生数据。"
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.pages.layout.menu-build :as mb]))

(def menus
  [{:menu_id 1 :menu_name "系统管理" :path "system" :menu_type "M" :icon "system"
    :children [{:menu_id 2 :menu_name "用户管理" :path "user" :menu_type "C" :icon "user"
                :children [{:menu_id 3 :menu_name "新增" :menu_type "F"}]}
               {:menu_id 4 :menu_name "角色管理" :path "role" :menu_type "C" :icon "#"}]}])

(deftest filter-visible-menus-test
  (testing "去掉 F(按钮权限)类型"
    (let [visible (mb/filter-visible-menus menus)]
      (is (empty? (get-in (vec visible) [0 :children 0 :children]))))))

(deftest menu-items-test
  (let [items (js->clj (mb/menu->antd-items (mb/filter-visible-menus menus)) :keywordize-keys true)]
    (testing "key 为完整路径"
      (is (= "system" (:key (first items))))
      (is (= ["system/user" "system/role"] (mapv :key (:children (first items))))))))

(deftest page-labels-and-icons-test
  (testing "调用方先过滤掉按钮(F)再派生,否则按钮会继承父级路径覆盖页面名"
    (let [visible (mb/filter-visible-menus menus)]
      (is (= {:user "用户管理" :role "角色管理"} (mb/page-labels visible)))
      (is (= "ContainerOutlined" (:role (mb/page-icons visible))) "icon 为 # 时使用默认图标"))))

(deftest menu-ancestor-keys-test
  (is (= ["system"] (mb/menu-ancestor-keys menus "system/user")))
  (is (= [] (mb/menu-ancestor-keys menus "nowhere"))))
