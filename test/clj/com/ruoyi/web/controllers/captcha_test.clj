(ns com.ruoyi.web.controllers.captcha-test
  "验证码控制器测试。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.infra.kv :as kv]
   [com.ruoyi.web.controllers.captcha :as captcha])
  (:import
   [java.awt Color]
   [java.awt.image BufferedImage]))

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

(defn- margin-clean?
  "只画字符(不画干扰线)后,四周 2 像素的边框内是否仍然全白 —— 即字符没有顶到或超出图片边缘。"
  [code]
  (let [w 150 h 50
        img (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)
        white (.getRGB Color/WHITE)]
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 w h)
    (captcha/draw-code! g code w h)
    (.dispose g)
    (every? (fn [[x y]] (= white (.getRGB img x y)))
            (for [x (range w) y (range h)
                  :when (or (< x 2) (>= x (- w 2)) (< y 2) (>= y (- h 2)))]
              [x y]))))

(deftest test-captcha-fits-image
  (testing "每个字符(含最宽的 W、M)排满 4 位时都完整落在图片内,不会被右边裁掉"
    (doseq [ch captcha/code-chars
            _ (range 3)] ; 纵向有随机抖动,多画几次
      (is (margin-clean? (apply str (repeat 4 ch))) (str "字符 " ch)))))
