(ns com.ruoyi.infra.redact-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [com.ruoyi.infra.redact :as redact]
            [integrant.core :as ig]))

(deftest configuration-redacts-credentials-and-preserves-structure
  (let [value {:auth {:jwt-secret "jwt-value" :COOKIE_SECRET "cookie-value"}
               :provider {"apiKey" "provider-value" :model "model-name"}
               :database {:password "db-value" :max-pool-size 10}
               :ref (ig/ref :db.sql/query-fn)
               :nested [{:client_secret "client-value"}]
               :enabled? true :port 3000 :nil nil 42 "numeric-key"}
        result (redact/config value)]
    (is (= {:jwt-secret "<redacted>" :COOKIE_SECRET "<redacted>"} (:auth result)))
    (is (= {"apiKey" "<redacted>" :model "model-name"} (:provider result)))
    (is (= {:password "<redacted>" :max-pool-size 10} (:database result)))
    (is (= {:__ig_ref true :key ":db.sql/query-fn"} (:ref result)))
    (is (= [{:client_secret "<redacted>"}] (:nested result)))
    (is (= [true 3000 nil "numeric-key"] (mapv result [:enabled? :port :nil 42])))))

(deftest connection-strings-and-system-properties-hide-secret-values
  (doseq [[input expected]
          [["jdbc:mysql://localhost/test?user=test&password=db-value&charset=utf8"
            "jdbc:mysql://localhost/test?user=test&password=<redacted>&charset=utf8"]
           ["https://user:p%40ssword@example.test/api?api_key=provider-value&mode=json"
            "https://user:<redacted>@example.test/api?api_key=<redacted>&mode=json"]
           ["jdbc:sqlserver://localhost;database=test;Password=db-value;encrypt=true"
            "jdbc:sqlserver://localhost;database=test;Password=<redacted>;encrypt=true"]
           ["-Djwt.secret=jwt-value" "-Djwt.secret=<redacted>"]
           ["-Ddatasource.url=jdbc:mysql://localhost/test?password=db-value&user=test"
            "-Ddatasource.url=jdbc:mysql://localhost/test?password=<redacted>&user=test"]]]
    (is (= expected (redact/url input))))
  (is (nil? (redact/url nil))))

(deftest snapshots-redact-bodies-and-application-specific-fields
  (let [input {:api_key "provider-value" :body {:name "private-value"}
               :nested {"CustomerNote" "private-note" :page 2}}
        result (redact/snapshot input {:sensitive-keys #{:customer-note}})]
    (is (= "<redacted>" (get result ":api_key")))
    (is (= "<redacted>" (get result ":body")))
    (is (= "<redacted>" (get-in result [":nested" "CustomerNote"])))
    (is (= "2" (get-in result [":nested" ":page"]))))
  (testing "异常与不透明对象的 toString 不会泄露细节"
    (is (= "<clojure.lang.ExceptionInfo>"
           (redact/snapshot (ex-info "password=private-value" {:secret "secret-value"}))))
    (is (= "<java.lang.StringBuilder>"
           (redact/snapshot (StringBuilder. "private-value"))))))

(deftest snapshots-have-bounded-depth-collections-and-string-length
  (is (= ["0" "1" "2"] (redact/snapshot (range) {:max-entries 3})))
  (is (= {":nested" "<depth-limit>"}
         (redact/snapshot {:nested {:value "private-value"}} {:max-depth 1})))
  (is (= "\"abcd..." (get (redact/snapshot {:label "abcdef"} {:max-string 5}) ":label")))
  (let [result (redact/snapshot {:url "jdbc:mysql://localhost/test?password=private-value&user=test"})]
    (is (str/includes? (get result ":url") "password=<redacted>"))
    (is (not (str/includes? (str result) "private-value")))))

(deftest unnamed-strings-retain-only-bounded-length-metadata
  (is (= "<string length=16>" (redact/snapshot "private-password")))
  (is (= ["<string length=16>" ["<string length=13>"]]
         (redact/snapshot ["private-password" ["private-token"]])))
  (is (= {":label" "\"ordinary\"" ":items" ["<string length=13>"]}
         (redact/snapshot {:label "ordinary" :items ["private-token"]})))
  (is (= {":password" "<redacted>"}
         (redact/snapshot {:password "private-password"} {:sensitive-keys #{}})))
  (is (< (count (redact/snapshot (apply str (repeat 10000 "s")))) 40)))
