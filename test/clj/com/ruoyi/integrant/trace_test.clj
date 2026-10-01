(ns com.ruoyi.integrant.trace-test
  "动态代理组件与调用追踪的回归测试。

   `register-dynamic!` 返回的代理函数会被组件长期持有(系统的 `:db.sql/query-fn` 就是它)。
   如果每次注册都换新 atom,系统 halt/init 之后老那一代代理就永远指向已经关闭的连接池:
   在线会话写不进去、请求全部 401,而接口只是安静地失败,所以单独测清楚。
   追踪相关的用例用自己的键并临时接管 `com.ruoyi.integrant.state/system`,不碰真系统的 query-fn。"
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.integrant.state :as state]
   [com.ruoyi.integrant.trace :as trace]
   [com.ruoyi.test-utils :refer [system-fixture]]))

(use-fixtures :once (system-fixture))

(def ^:private probe-key :trace-test/query-fn)

;; 追踪的登记键是字符串,而且必须能 `(keyword ...)` 回原键:monitor 控制器就是拿
;; path-params 里的 `:key` 直接调 start!/stop! 的,带冒号的写法只会追到一个不存在的组件。
(def ^:private key-str "trace-test/query-fn")

(defn- run-traced
  "把 impl 注册成动态代理,让一份临时的系统 map 持有它,再调用 (thunk proxy in-system)。

   in-system 每次都从系统里取当前那个函数(追踪开启后是包装版)并按单参数调用。
   绑定必须覆盖 thunk 的执行,所以把 thunk 调用放进 with-redefs 的表单里,而不是写成宏。"
  [impl thunk]
  (let [proxy (trace/register-dynamic! probe-key impl)]
    (with-redefs [state/system (atom {probe-key proxy})]
      (try
        (thunk proxy (fn [arg] ((probe-key @state/system) arg)))
        (finally (trace/stop! key-str))))))

(deftest register-dynamic-reuses-atom-test
  (testing "同一个键复用同一个 atom:任何一代代理都指向最新实现"
    (let [calls (atom [])
          old-impl (fn [& a] (swap! calls conj (into [:old] a)) "旧实现")
          new-impl (fn [& a] (swap! calls conj (into [:new] a)) "新实现")
          proxy-1 (trace/register-dynamic! probe-key old-impl)
          proxy-2 (trace/register-dynamic! probe-key new-impl)]
      (is (= "新实现" (proxy-2 :x)) "刚注册的代理用的是新实现")
      (is (= "新实现" (proxy-1 :y))
          "上一代代理也必须跟着换:组件里存的还是它,否则它抱着已经关闭的池继续跑")
      (is (= [[:new :x] [:new :y]] @calls)
          "两条调用都落到新实现上,记录里的 :new 证明走的是同一个 atom"))))

(deftest set-dynamic-test
  (testing "set-dynamic! 换实现,current-dynamic 读当前那一个"
    (let [proxy (trace/register-dynamic! probe-key (fn [] "第一版"))]
      (is (= "第一版" (proxy)))
      (trace/set-dynamic! probe-key (fn [] "第二版"))
      (is (= "第二版" (proxy)))
      (is (= "第二版" ((trace/current-dynamic probe-key))))
      (is (nil? (trace/set-dynamic! :trace-test/unknown (fn [] "不该生效")))
          "没登记过的键不报错,也不影响别的组件"))))

(deftest tracking-wrapper-test
  (testing "开启追踪后记录调用,停用后把原实现装回去"
    (run-traced (fn [x] (* 2 x))
                (fn [proxy in-system]
                  (is (= 8 (proxy 4)) "没开追踪时直接透传")
                  (is (true? (trace/start! key-str)) "系统里能取到实现才追得到")
                  (is (trace/active? key-str))
                  (is (= 8 (in-system 4)) "包了追踪也不改变结果")
                  (is (= 1 (count (trace/logs key-str))) "调用记录进了 registry")
                  (trace/stop! key-str)
                  (is (not (trace/active? key-str)))
                  (is (empty? (trace/logs key-str)))
                  (is (= 8 (in-system 4)) "停用后装回原实现,调用照常")
                  (is (= 8 (proxy 4)))))))

(deftest sensitive-args-not-logged-test
  (testing "追踪日志里的 authorization / password 要脱敏,别的字段照常记录"
    (run-traced (fn [m] m)
                (fn [_ in-system]
                  (trace/start! key-str)
                  (in-system {:authorization "Bearer super-secret" :password "admin123" :page 1})
                  (let [entry (first (trace/logs key-str))
                        dumped (str entry)]
          ;; 快照里的键是 `(str 键)`(所以带冒号):监控页把这条记录原样编码成 JSON 显示出来。
                    (is (= "<redacted>" (get-in entry [:args 0 ":authorization"]))
                        "参数快照不能把令牌原样写进日志,监控页会把它显示出来")
                    (is (= "<redacted>" (get-in entry [:args 0 ":password"])))
                    (is (= "1" (get-in entry [:args 0 ":page"])) "非敏感字段照常留痕")
                    (is (not (str/includes? dumped "super-secret")) "整条记录里都不该出现原始令牌")
                    (is (not (str/includes? dumped "admin123"))))))))
