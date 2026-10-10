(ns com.ruoyi.web.role-request-test
  "真实浏览器角色表单的字符串整数在三库使用一致的写入契约。"
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

(deftest browser-role-integer-contract-test
  (let [login (call :post "/api/auth/login" {:username "admin" :password "admin123"} {})
        headers {"authorization" (str "Bearer " (get-in login [:json :data :token]))}
        unique (str "role-contract-" (random-uuid))
        fields {:role_name unique :role_key unique :role_sort "7" :status "0" :menu-ids ["1"]}
        created (call :post "/api/system/role" fields headers)
        id (some->> (get-in created [:json :data]) (re-find #"\d+") parse-long)
        path (str "/api/system/role/" id)]
    (is (= 200 (:status created)))
    (is (= 200 (get-in created [:json :code])))
    (is (pos-int? id))
    (when id
      (try
        (is (= 7 (get-in (call :get path nil headers) [:json :data :role_sort])))
        (is (= [1] (get-in (call :get path nil headers) [:json :data :menu-ids])))
        (is (= 200 (get-in (call :put path {:role_sort "9"} headers) [:json :code])))
        (is (= 200 (get-in (call :put path {:status "1" :role_sort nil} headers) [:json :code])))
        (is (= {:role_sort 9 :status "1" :role_name unique}
               (select-keys (get-in (call :get path nil headers) [:json :data])
                            [:role_sort :status :role_name])))
        (is (= 200 (get-in (call :put "/api/system/role/dataScope"
                                    {:role_id id :data_scope "2" :dept_ids "6"} headers)
                           [:json :code])))
        (is (= [6] (get-in (call :get (str "/api/system/role/deptTree/" id) nil headers)
                           [:json :data :checked-keys])))
        (is (= 500 (get-in (call :put path {:role_sort "bad"} headers) [:json :code])))
        (is (= 9 (get-in (call :get path nil headers) [:json :data :role_sort])))
        (finally
          (is (= 200 (get-in (call :delete path nil headers) [:json :code]))))))))
