(ns com.ruoyi.web.list-query-request-test
  "列表接口的筛选条件真正生效(查询参数经 controllers.params/query 转成关键字键),
   以及清空日志等不带参数的接口可用。"
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.test-utils :refer [GET system-fixture system-state]]
   [peridot.core :as p]))

(use-fixtures :once (system-fixture))

(defn- handler [] (:handler/ring (system-state)))

(defn- parse-json [resp]
  (try (json/read-str (:body resp) :key-fn keyword) (catch Exception _ nil)))

(defn- admin []
  (let [resp (-> (p/session (handler))
                 (p/request "/api/auth/login" :request-method :post :content-type "application/json"
                            :body (json/write-str {:username "admin" :password "admin123"}))
                 :response)]
    {"authorization" (str "Bearer " (get-in (parse-json (update resp :body #(if (string? %) % (slurp %))))
                                            [:data :token]))}))

(defn- rows [path query]
  (let [data (:data (parse-json (GET (handler) path query (admin))))]
    (if (map? data) (:rows data) data)))

(deftest list-filters-test
  (testing "角色按权限字符"
    (is (= ["admin"] (map :role_key (rows "/api/system/role" {"role_key" "admin"})))))
  (testing "岗位按编码"
    (is (= ["ceo"] (map :post_code (rows "/api/system/post" {"post_code" "ceo"})))))
  (testing "字典数据按类型"
    (let [rs (rows "/api/system/dict/data" {"dict_type" "sys_user_sex"})]
      (is (= 3 (count rs)))
      (is (every? #(= "sys_user_sex" (:dict_type %)) rs))))
  (testing "菜单按名称"
    (let [rs (rows "/api/system/menu" {"menu_name" "用户"})]
      (is (seq rs))
      (is (every? #(str/includes? (:menu_name %) "用户") rs))))
  (testing "参数按键名"
    (is (= ["sys.user.initPassword"] (map :config_key (rows "/api/system/config" {"config_key" "sys.user.initPassword"})))))
  (testing "空串视为未填写"
    (is (< 1 (count (rows "/api/system/role" {"role_key" ""}))))))

(deftest clear-logs-test
  (testing "清空登录日志(不带时间范围)"
    (let [resp (-> (p/session (handler))
                   (p/request "/api/system/login-log" :request-method :delete :headers (admin))
                   :response)]
      (is (= 200 (:status resp)))
      (is (= 200 (:code (parse-json (update resp :body #(if (string? %) % (slurp %))))))))))
