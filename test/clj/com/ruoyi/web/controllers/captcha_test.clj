(ns com.ruoyi.web.controllers.captcha-test
  "验证码控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.infra.kv :as kv]
   [com.ruoyi.web.controllers.captcha :as captcha]))

(use-fixtures :each
  (fn [test]
    (kv/use-store! (kv/memory-store))
    (test)))

(deftest test-captcha-image
  ;; 生成验证码图片
  (testing "无 r 参数时返回图片与 UUID"
    (let [request {:query-params {}}
          response (captcha/captcha-image {} request)]
      (is (= 200 (:status response)))
      (is (= "image/png" (get-in response [:headers "Content-Type"])))
      (is (string? (get-in response [:headers "Captcha-UUID"])))
      (is (pos? (count (:body response))))
      (let [uuid (get-in response [:headers "Captcha-UUID"])
            stored (kv/get-val (str "captcha:" uuid))]
        (is (= 4 (count stored)))
        (testing "一次性:取出后作废"
          (is (= stored (captcha/take-code! uuid)))
          (is (nil? (captcha/take-code! uuid))))))))

(deftest test-captcha-image-with-r
  ;; 使用指定 r 参数生成验证码
  (testing "r 参数作为 UUID 并写入验证码缓存"
    (let [request {:query-params {"r" "abc123"}}
          response (captcha/captcha-image {} request)]
      (is (= 200 (:status response)))
      (is (= "abc123" (get-in response [:headers "Captcha-UUID"])))
      (is (pos? (count (:body response))))
      (is (= 4 (count (kv/get-val "captcha:abc123")))))))
