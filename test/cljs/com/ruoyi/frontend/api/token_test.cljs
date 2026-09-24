(ns com.ruoyi.frontend.api.token-test
  "令牌纯函数:JWT 载荷解码、续期时机、401 判断。"
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.ruoyi.frontend.api.token :as t]))

(defn- b64url [s]
  (-> (js/btoa s) (.replace (js/RegExp. "=+$") "") (.replace (js/RegExp. "\\+" "g") "-") (.replace (js/RegExp. "/" "g") "_")))

(defn- fake-jwt [claims]
  (str (b64url "{\"alg\":\"HS256\"}") "." (b64url (js/JSON.stringify (clj->js claims))) ".sig"))

(deftest token-claims-test
  (testing "解码 base64url 载荷"
    (is (= {:user-id 1 :iat 100 :exp 200 :jti "a-b"}
           (t/claims (fake-jwt {:user-id 1 :iat 100 :exp 200 :jti "a-b"})))))
  (testing "格式不对返回 nil"
    (is (nil? (t/claims "not-a-jwt")))
    (is (nil? (t/claims nil)))))

(deftest refresh-due-test
  (let [claims {:iat 1000 :exp 2000}]
    (testing "前半段不续期"
      (is (false? (t/refresh-due? claims 1400000))))
    (testing "过了一半、未过期时续期"
      (is (true? (t/refresh-due? claims 1600000))))
    (testing "已过期不续期(续期接口也会拒绝)"
      (is (false? (t/refresh-due? claims 2100000))))
    (testing "缺字段不续期"
      (is (false? (t/refresh-due? {} 1600000))))))

(deftest unauthorized-test
  (is (true? (t/unauthorized? false {:status 401})))
  (is (true? (t/unauthorized? true {:code 401})))
  (is (false? (t/unauthorized? true {:code 200})))
  (is (false? (t/unauthorized? false {:status 500}))))
