(ns com.ruoyi.infra.security-test
  "安全基础设施测试。"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.infra.security :as security]))

(deftest test-password-hashing
  (testing "密码哈希"
    (let [raw-password "test123"
          hashed (security/hash-password raw-password)]
      (is (string? hashed))
      (is (not= raw-password hashed))
      (is (security/verify-password raw-password hashed)))))

(deftest test-password-verification-failure
  (testing "密码验证失败"
    (let [hashed (security/hash-password "test123")]
      (is (not (security/verify-password "wrong" hashed))))))

(deftest test-extract-token
  (testing "从请求中提取 Token"
    (let [request {:headers {"authorization" "Bearer test-token-123"}}
          token (security/extract-token request)]
      (is (= "test-token-123" token)))))

(deftest test-token-expiry
  (testing "exp 按 JWT 标准使用 Unix 秒"
    (let [{:keys [iat exp jti]} (security/parse-token (security/generate-token 1 "u" [] :ttl-minutes 2))]
      (is (= 120 (- exp iat)))
      (is (< exp 1e11) "是秒而不是毫秒")
      (is (string? jti))))
  (testing "过期令牌解析失败"
    (is (nil? (security/parse-token (security/generate-token 1 "u" [] :ttl-minutes -1))))))
