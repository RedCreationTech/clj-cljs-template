(ns com.ruoyi.frontend.dev-snapshot-test
  (:require
   [cljs.test :refer [deftest is]]
   [clojure.string :as str]
   [com.ruoyi.frontend.dev-snapshot :as snapshot]
   [re-frame.db :as db]))

(deftest whitelist-test
  (let [secret "NEVER_EXPORT"
        input {:page secret :auth {:token secret :user {:email secret :phone secret}}
               :users {:loading? true :saving? secret :items [{:remark secret}]
                       :query-params {:search secret} :form {:body secret}}
               :private-module {:loading? true}}
        before (pr-str input)
        result (snapshot/summarize input)]
    (is (= :unknown (get-in result [:state :page])))
    (is (true? (get-in result [:state :token-present?])))
    (is (= {:loading? true :saving? :unknown :modal-visible? :unknown :item-count 1}
           (get-in result [:state :modules :users])))
    (is (not (contains? (get-in result [:state :modules]) :private-module)))
    (is (not (str/includes? (pr-str result) secret)))
    (is (< (count (pr-str result)) 4000))
    (is (= before (pr-str input)))))

(deftest unknown-and-bounds-test
  (is (= {:status :unavailable :state nil} (snapshot/summarize {})))
  (is (= {:status :unavailable :state nil} (snapshot/summarize nil)))
  (is (= :dashboard (get-in (snapshot/summarize {:page :dashboard}) [:state :page])))
  (is (false? (get-in (snapshot/summarize {:page :login}) [:state :token-present?])))
  (is (= :unknown (get-in (snapshot/summarize {:users {:items (repeat :secret)}})
                          [:state :modules :users :item-count])))
  (is (= 1000000 (get-in (snapshot/summarize {:users {:items (vec (repeat 1000001 nil))}})
                         [:state :modules :users :item-count]))))

(deftest snapshot-read-only-test
  (let [source (atom {:page :login})
        writes (atom 0)]
    (add-watch source ::writes (fn [& _] (swap! writes inc)))
    (with-redefs [db/app-db source]
      (let [a (snapshot/snapshot) b (snapshot/snapshot)]
        (is (= :frontend (:source a)))
        (is (= 1 (:schema-version a)))
        (is (= (:runtime-id a) (:runtime-id b)))
        (is (= 36 (count (:runtime-id a))))
        (is (string? (:captured-at a)))
        (is (= 0 @writes))
        (is (= {:page :login} @source))))))

(deftest non-dev-refuses-before-reading-test
  (let [source (reify IDeref
                 (-deref [_] (throw (js/Error. "Must not read app-db"))))]
    (with-redefs [goog.DEBUG false
                  db/app-db source]
      (let [result (snapshot/snapshot)]
        (is (= :forbidden (:status result)))
        (is (nil? (:state result)))))))
