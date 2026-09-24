(ns com.ruoyi.infra.secrets-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.secrets :as secrets]))

(def good {"JWT_SECRET" (apply str (repeat 64 "a"))
           "COOKIE_SECRET" "0123456789abcdef"})

(deftest problems-test
  (testing "合规配置没有问题"
    (is (empty? (secrets/problems good))))
  (testing "缺失"
    (is (= ["未设置 JWT_SECRET" "未设置 COOKIE_SECRET"] (secrets/problems {}))))
  (testing "仍是默认值"
    (is (some #(str/includes? % "默认值")
              (secrets/problems (assoc good "JWT_SECRET" secrets/default-jwt-secret))))
    (is (some #(str/includes? % "默认值")
              (secrets/problems (assoc good "COOKIE_SECRET" secrets/default-cookie-secret)))))
  (testing "长度"
    (is (some #(str/includes? % "太短") (secrets/problems (assoc good "JWT_SECRET" "short"))))
    (is (some #(str/includes? % "16 字节") (secrets/problems (assoc good "COOKIE_SECRET" "too-short"))))
    ;; 中文按 UTF-8 字节计:6 个汉字 = 18 字节
    (is (some #(str/includes? % "16 字节") (secrets/problems (assoc good "COOKIE_SECRET" "六个汉字六个"))))))

(deftest verify-test
  (testing "只在 prod 下拦截"
    (is (nil? (secrets/verify! :dev {})))
    (is (nil? (secrets/verify! :test {})))
    (is (nil? (secrets/verify! :prod good))))
  (testing "prod 下不合规时抛出带问题列表的异常"
    (let [e (try (secrets/verify! :prod {}) nil (catch clojure.lang.ExceptionInfo e e))]
      (is (some? e))
      (is (= ::secrets/insecure-secrets (:type (ex-data e))))
      (is (= 2 (count (:problems (ex-data e)))))
      (is (str/includes? (ex-message e) "openssl rand")))))
