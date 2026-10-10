(ns com.ruoyi.frontend.events.menu-form-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.events.menu-form :as form]))

(deftest submit-menu-form-test
  (testing "更新标识来自编辑状态，不依赖缺失或伪造的表单 menu_id"
    (doseq [values [{:menu_name "修改" :order_num "2"}
                    {:menu_id 999 :menu_name "修改" :order_num "2"}]]
      (let [effects (form/submit-effects {:menus {:editing 42 :modal-visible? true}} values)]
        (is (= 42 (first (:api/update-menu effects))))
        (is (= 2 (:order_num (second (:api/update-menu effects)))))
        (is (false? (get-in effects [:db :menus :modal-visible?]))))))
  (testing "顶层菜单缺省或清空父节点都写 0，子菜单保持父节点"
    (doseq [[values parent-id] [[{} 0] [{:parent_id nil} 0] [{:parent_id 3} 3]]]
      (let [effects (form/submit-effects {:menus {:editing false}} values)]
        (is (= parent-id (get-in effects [:api/create-menu :parent_id])))
        (is (= 0 (get-in effects [:api/create-menu :order_num])))
        (is (nil? (:api/update-menu effects)))))))
