(ns com.ruoyi.web.menu-request-test
  "真实浏览器表单的字符串整数在三库使用一致的菜单写入契约。"
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [com.ruoyi.infra.json :as json]
   [com.ruoyi.test-utils :refer [system-fixture system-state]]
   [peridot.core :as p]))

(use-fixtures :once (system-fixture))

(defn- call [method path body headers]
  (let [response (-> (p/session (:handler/ring (system-state)))
                     (p/request path :request-method method :content-type "application/json"
                                :headers headers :body (when body (json/write-str body)))
                     :response)
        payload (:body response)]
    (assoc response :json (json/read-str (if (string? payload) payload (slurp payload))))))

(deftest browser-menu-integer-contract-test
  (let [login (call :post "/api/auth/login" {:username "admin" :password "admin123"} {})
        headers {"authorization" (str "Bearer " (get-in login [:json :data :token]))}
        fields {:menu_name (str "menu-contract-" (random-uuid)) :parent_id "0"
                :order_num "8" :is_frame "0" :is_cache "1" :menu_type "M" :status "0" :visible "1"}
        created (call :post "/api/system/menu" fields headers)
        id (some->> (get-in created [:json :data]) (re-find #"\d+") parse-long)
        path (str "/api/system/menu/" id)]
    (is (= 200 (:status created)))
    (is (= 200 (get-in created [:json :code])))
    (is (pos-int? id))
    (when id
      (try
        (is (= {:parent_id 0 :order_num 8 :is_frame 0 :is_cache 1}
               (select-keys (get-in (call :get path nil headers) [:json :data])
                            [:parent_id :order_num :is_frame :is_cache])))
        (is (= 200 (get-in (call :put path (assoc fields :order_num "9" :is_frame "1" :is_cache "0") headers)
                           [:json :code])))
        (is (= {:order_num 9 :is_frame 1 :is_cache 0}
               (select-keys (get-in (call :get path nil headers) [:json :data])
                            [:order_num :is_frame :is_cache])))
        (is (= 500 (get-in (call :put path (assoc fields :is_frame "bad") headers) [:json :code])))
        (is (= 1 (get-in (call :get path nil headers) [:json :data :is_frame])))
        (finally
          (is (= 200 (get-in (call :delete path nil headers) [:json :code]))))))))
