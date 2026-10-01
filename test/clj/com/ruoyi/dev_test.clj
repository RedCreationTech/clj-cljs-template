(ns com.ruoyi.dev-test
  "开发期 REPL 助手的回归测试。

   这一层坏了不会让任何接口报错,只会让开发机上的 `bb dev` 变得难用(重启把自己断开、
   重载后连接池被换掉、状态读不到),所以单独测清楚。
   测试里不调 `reload` / `restart!` / `init-refresh!`:那会改动 tools.namespace 的全局
   tracker,还可能把整个 src 重新加载一遍,影响同一 JVM 里的其他测试。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev :as dev]
   [com.ruoyi.test-utils :refer [system-fixture]]
   [integrant.core :as ig]))

(use-fixtures :once (system-fixture))

;; 下面三个 var 只给 mutable-holders-test 当样本:一个真状态、一份普通数据、一个派生值。
(def ^:private state-holder (atom {:seed 1}))
(def ^:private plain-value {:seed 1})
(def ^:private lazy-value (delay (:seed plain-value)))

(def ^:private probe-halted (atom false))

(defmethod ig/halt-key! ::probe
  [_ _]
  (reset! probe-halted true))

(deftest prepared-config-test
  (testing "重启用的配置读的是给定 profile,并且去掉 nREPL"
    (doseq [profile [:dev :test]]
      (let [cfg (dev/prepared-config profile)]
        (is (not (contains? cfg dev/nrepl-key)) (str profile " 不该带 nREPL 组件"))
        (is (= profile (:system/env cfg)))
        (is (some? (:handler/ring cfg)))
        (is (every? (fn [[_ v]] (not (instance? integrant.core.Ref v))) cfg)
            "引用应该已经展开")))))

(deftest halt-keeps-nrepl-test
  (testing "halt! 释放全部组件,只把 nREPL 与 profile 留在原地"
    (reset! probe-halted false)
    ;; ig/halt! 只认 ig/init 产出的 map:元数据 :integrant.core/origin 存的是**配置**,
    ;; halt 时按它决定停哪些键。手工造系统要照这个形状造假(dissoc 不会丢掉这份元数据,
    ;; 所以把 nREPL 从待 halt 的 map 里摘掉是有效的)。
    (let [origin {dev/nrepl-key    {}
                  :system/env      {}
                  :db.sql/query-fn {}
                  ::probe         {}}
          sys (with-meta {dev/nrepl-key    :repl
                          :system/env      :test
                          :db.sql/query-fn :fake
                          ::probe         :fake}
                {:integrant.core/origin origin})]
      (with-redefs [core/system (atom sys)]
        (is (= 0 (dev/halt!)) "只剩不算组件的键")
        (is @probe-halted "自定义组件该被 halt 到")
        (is (= #{dev/nrepl-key :system/env} (set (keys (dev/system)))) "REPL 不能被自己关掉")
        (is (= :test (@#'dev/active-profile)) "halt 之后也该记得用的是哪个 profile")))))

(deftest component-count-test
  (testing "component-count 不把 nREPL 与 profile 这类元数据键算成组件"
    (with-redefs [core/system (atom {dev/nrepl-key :repl :system/env :test ::probe :fake})]
      (is (= 1 (@#'dev/component-count (dev/system)))))))

(deftest require-system-test
  (testing "系统或组件不在时给出可操作的提示,而不是 NPE"
    (with-redefs [core/system (atom nil)]
      (doseq [[name thunk] {'q       #(dev/q :find-user-by-name {})
                            'state   #(dev/state)
                            'request #(dev/request :get "/system/post")}]
        (let [e (try (thunk)
                     (catch clojure.lang.ExceptionInfo ex ex))]
          (is (instance? clojure.lang.ExceptionInfo e) (str name " 应该抛错"))
          (is (re-find #"系统还没起来" (ex-message e)))
          (is (= name (:while (ex-data e))))))))
  (testing "halt 与 init 之间组件是空的,同样要说人话"
    (with-redefs [core/system (atom {dev/nrepl-key :repl :system/env :test})]
      (let [e (try (dev/q :find-user-by-name {})
                   (catch clojure.lang.ExceptionInfo ex ex))]
        (is (instance? clojure.lang.ExceptionInfo e))
        (is (re-find #"不在系统里" (ex-message e)))
        (is (= :db.sql/query-fn (:key (ex-data e))))))))

(deftest mutable-holders-test
  (testing "重载要不要顺带重建组件,看的是可变容器的身份换没换"
    (let [syms '[com.ruoyi.dev-test]
          holders (@#'dev/mutable-holders syms)]
      (is (= 1 @lazy-value) "delay 也有值,只是换掉它不影响运行中的系统")
      (is (contains? holders 'com.ruoyi.dev-test/state-holder) "atom 是运行期状态")
      (is (not (contains? holders 'com.ruoyi.dev-test/plain-value)) "普通数据不算")
      (is (not (contains? holders 'com.ruoyi.dev-test/lazy-value)) "delay 是一次性派生值,不算")
      (is (empty? (@#'dev/replaced-holders holders syms)) "没重载过就该一致")
      (let [old @state-holder]
        (alter-var-root #'state-holder (fn [_] (atom old)))
        (try
          (is (= '(com.ruoyi.dev-test/state-holder) (@#'dev/replaced-holders holders syms))
              "换了对象的 atom 会让系统继续引用旧的那份,必须重建组件")
          (finally
            (alter-var-root #'state-holder (fn [_] old))))))))

(deftest reload-exclusions-test
  (testing "排除表里的命名空间都真实存在(手抄清单的错字在这里暴露)"
    (is (seq dev/reload-exclusions))
    (doseq [sym dev/reload-exclusions]
      (require sym)
      (is (some? (find-ns sym)) (str "排除表里有不存在的命名空间: " sym)))))

(deftest repl-helpers-on-running-system-test
  (testing "REPL 助手读的都是运行中系统的那份状态"
    (let [admin (dev/q :find-user-by-name {:user_name "admin"})]
      (is (= "admin" (:user_name admin)))
      (is (contains? admin :user_id))
      (is (contains? admin :password)
          "q 打的是原始命名查询,不经领域层脱敏:REPL 里能看到哈希,别把结果直接回给前端"))
    (let [st (dev/state)]
      (is (= :test (:profile st)))
      (is (< 5 (:components st)))
      (is (some? (:db_name (:pool st))) "连接池要能读到实时数字,不是 unknown"))
    (let [resp (dev/request :get "/system/post" :params {:page 1 :size 2})]
      (is (= 200 (:code resp)) (pr-str resp))
      (is (= 2 (count (get-in resp [:data :rows]))))
      (is (number? (get-in resp [:data :total]))))
    (testing "halt 之后组件全空,init 之后又能打接口"
      (is (= 0 (dev/halt!)))
      (is (nil? (:db.sql/query-fn (dev/system))))
      (is (thrown? clojure.lang.ExceptionInfo (dev/q :find-user-by-name {})) "组件没了就该报错")
      (is (< 5 (dev/init!)))
      (is (= 200 (:code (dev/request :get "/system/post" :params {:page 1 :size 1})))))))
