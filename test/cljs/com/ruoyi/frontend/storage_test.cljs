(ns com.ruoyi.frontend.storage-test
  "localStorage 封装:键名规则与不可用时的降级。"
  (:require
   [cljs.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.frontend.storage :as storage]))

(defn- fake-local-storage []
  (let [m (atom {})]
    #js {:getItem (fn [k] (get @m k))
         :setItem (fn [k v] (swap! m assoc k (str v)))
         :removeItem (fn [k] (swap! m dissoc k))}))

(use-fixtures :each
  {:before #(set! (.-localStorage js/globalThis) (fake-local-storage))
   :after #(js-delete js/globalThis "localStorage")})

(deftest storage-key-test
  (is (= "ruoyi_token" (storage/storage-key :token)))
  (is (= "ruoyi_layout_settings" (storage/storage-key :layout-settings))))

(deftest round-trip-test
  (storage/set-item! :theme-mode :dark)
  (is (= "dark" (storage/get-item :theme-mode)) "关键字按 name 存")
  (storage/set-json! :user {:user_name "admin" :roles ["admin"]})
  (is (= {:user_name "admin" :roles ["admin"]} (storage/get-json :user)))
  (storage/remove-item! :theme-mode :user)
  (is (nil? (storage/get-item :theme-mode)))
  (is (nil? (storage/get-json :user))))

(deftest unavailable-storage-test
  (testing "localStorage 不存在或抛异常时静默返回 nil"
    (js-delete js/globalThis "localStorage")
    (is (nil? (storage/get-item :token)))
    (is (nil? (storage/set-item! :token "x")))
    (is (nil? (storage/get-json :user)))))
