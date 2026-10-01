(ns com.ruoyi.domain.system.config-test
  "参数设置领域服务测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.domain.system.config :as config]))

(def mock-configs
  [{:config_id 1 :config_name "主框架页-默认皮肤" :config_key "sys.index.skinName" :config_value "skin-blue" :config_type "Y"}
   {:config_id 2 :config_name "用户管理-账号初始密码" :config_key "sys.user.initPassword" :config_value "123456" :config_type "Y"}])

(defn- by-key
  "按 config_key 精确命中,模拟唯一索引的查询语义。"
  [params]
  (first (filter #(= (:config_key %) (:config_key params)) mock-configs)))

(defn- mock-query-fn [query-name params]
  (case query-name
    :list-configs mock-configs
    :find-config-by-id (first mock-configs)
    :find-config-by-key (by-key params)
    :create-config! [{:config_id 3}]
    :last-insert-rowid {:last_insert_rowid 3}
    :update-config! nil
    :delete-config! nil
    []))

(def mock-service {:query-fn mock-query-fn})

(deftest test-list-configs
  (testing "查询参数列表"
    (let [result (config/list-configs mock-service {})]
      (is (seq result))
      (is (= 2 (count result))))))

(deftest test-find-config-by-id
  (testing "根据ID查询参数"
    (let [result (config/find-config-by-id mock-service 1)]
      (is (some? result))
      (is (= "主框架页-默认皮肤" (:config_name result))))))

(deftest test-find-config-by-key
  (testing "根据Key查询参数"
    (let [result (config/find-config-by-key mock-service "sys.index.skinName")]
      (is (some? result))
      (is (= "skin-blue" (:config_value result))))))

(deftest test-create-config
  (testing "创建参数"
    (let [result (config/create-config! mock-service {:config_name "测试参数" :config_key "test.key" :config_value "test" :config_type "Y"})]
      (is (some? result)))))

(deftest test-update-config
  (testing "更新参数"
    (let [result (config/update-config! mock-service {:config_id 1 :config_value "new-value"})]
      (is (nil? result)))))

(deftest test-delete-config
  (testing "删除参数"
    (let [result (config/delete-config! mock-service 1)]
      (is (nil? result)))))

(deftest test-create-config-duplicate-key
  (testing "键名重复时给出中文提示,而不是让数据库的唯一索引报错冒给用户"
    (let [ex (try (config/create-config! mock-service {:config_key "sys.index.skinName"})
                  (catch Exception e e))]
      (is (= "参数键名已存在" (ex-message ex)))
      (is (= :system.exception/business (:type (ex-data ex)))))))

(deftest test-update-config-key-unchanged
  (testing "修改参数时键名没换,不算重复"
    (let [result (config/update-config! mock-service {:config_id 1 :config_key "sys.index.skinName" :config_value "x"})]
      (is (nil? result)))))

(deftest test-update-config-key-conflict
  (testing "把键名改成别人已占用的键 -> 拒绝"
    (let [ex (try (config/update-config! mock-service {:config_id 2 :config_key "sys.index.skinName"})
                  (catch Exception e e))]
      (is (= "参数键名已存在" (ex-message ex))))))

(deftest test-find-config-by-id-not-found
  (testing "查询不存在的参数"
    (with-redefs [mock-query-fn (fn [_ _] nil)]
      (let [result (config/find-config-by-id {:query-fn mock-query-fn} 999)]
        (is (nil? result))))))
