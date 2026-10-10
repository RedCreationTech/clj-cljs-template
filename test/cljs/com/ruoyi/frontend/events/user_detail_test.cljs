(ns com.ruoyi.frontend.events.user-detail-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.events.user-detail :as detail]))

(def user {:user_id 7 :user_name "example" :remark "详情备注"
           :roles [{:role_id 3 :role_name "审核角色"}]
           :posts [{:post_id 2 :post_name "审核岗位"}]})

(deftest user-detail-load-test
  (let [initial {:users {:items [{:user_id 7 :user_name "列表行"}]
                        :detail-data {:user_id 1}}}
        effects (detail/open-effects initial 7 :first)
        request (:api/get-user-detail effects)
        loading (:db effects)]
    (testing "打开触发详情 GET，清空旧数据而不把列表行当详情"
      (is (= {:user-id 7 :request-id :first} request))
      (is (true? (get-in loading [:users :detail-visible?])))
      (is (true? (get-in loading [:users :detail-loading?])))
      (is (nil? (get-in loading [:users :detail-data])))
      (is (= (get-in initial [:users :items]) (get-in loading [:users :items]))))
    (testing "成功保留完整角色、岗位、备注并结束加载"
      (let [loaded (detail/receive-detail loading request user)]
        (is (= user (get-in loaded [:users :detail-data])))
        (is (false? (get-in loaded [:users :detail-loading?])))
        (is (false? (get-in loaded [:users :detail-error?])))))
    (testing "失败或响应用户不匹配时不显示错误用户，复位加载"
      (doseq [response [nil (assoc user :user_id 9)]]
        (let [failed (detail/receive-detail loading request response)]
          (is (nil? (get-in failed [:users :detail-data])))
          (is (false? (get-in failed [:users :detail-loading?])))
          (is (true? (get-in failed [:users :detail-error?]))))))))

(deftest user-detail-stale-response-test
  (let [first-open (detail/open-effects {} 7 :first)
        first-request (:api/get-user-detail first-open)
        closed (detail/close-detail (:db first-open))
        other-open (detail/open-effects (:db first-open) 9 :second)
        reopened (detail/open-effects closed 7 :third)]
    (testing "关闭后到达的成功和失败不重开抽屉或写入数据"
      (doseq [response [user nil]]
        (is (= closed (detail/receive-detail closed first-request response)))))
    (testing "切换用户后的旧成功和失败不覆盖新请求"
      (doseq [response [user nil]]
        (is (= (:db other-open) (detail/receive-detail (:db other-open) first-request response)))))
    (testing "同一用户关闭重开也不能接受上一请求"
      (is (= (:db reopened) (detail/receive-detail (:db reopened) first-request user)))
      (is (= user (get-in (detail/receive-detail (:db reopened) (:api/get-user-detail reopened) user)
                         [:users :detail-data]))))))
