(ns com.ruoyi.infra.datasource-test
  "代理连接池的回归测试。

   测的都是运行期身份:监控页解包解不到 Hikari 就显示 unknown,老代理抱着已经关闭的池
   就让在线会话写不进去、请求全部 401。这些出问题接口不一定报错,所以单独锁住。"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.ruoyi.config :as config]
   [com.ruoyi.infra.datasource :as ds]
   [com.ruoyi.integrant.trace]
   [conman.core :as conman]
   [integrant.core :as ig])
  (:import
   [com.zaxxer.hikari HikariDataSource]))

(defn- fake-pool [tag]
  ;; 代理只在 getConnection 时才碰底层,解包 / 登记 / 换池这几条路径不需要真 DataSource
  {:tag tag})

(deftest unwrap-test
  (testing "unwrap 一路解到真正的池,不停在中间那层代理上"
    (let [real (fake-pool :real)
          p1 (ds/delegating-datasource real)
          p2 (ds/delegating-datasource p1)]
      (is (ds/swappable? p1))
      (is (ds/swappable? p2))
      (is (identical? real (ds/unwrap p1)))
      (is (identical? real (ds/unwrap p2)) "历史遗留的嵌套代理也必须解到底")
      (is (identical? real (ds/unwrap real)) "不是代理时原样返回"))))

(deftest deregister-test
  (testing "停用后摘掉登记:注册表按 identityHashCode 索引,残留会让普通池被误认成代理"
    (let [real (fake-pool :real)
          proxy (ds/delegating-datasource real)]
      (is (ds/swappable? proxy))
      (is (nil? (ds/deregister! proxy)))
      (is (not (ds/swappable? proxy)) "摘掉之后不该再被当成代理")
      (is (identical? proxy (ds/unwrap proxy)) "没有登记就不再往下解,不会读到已经关闭的池")
      (is (nil? (ds/swap-delegate! proxy (fake-pool :new))) "摘掉之后换不动底层,也不会误改别人的池"))))

(deftest swap-delegate-test
  (testing "热切换只换 delegate:代理对象身份不变,已持有它的服务无需重新初始化"
    (let [sqlite (fake-pool :sqlite)
          proxy (ds/delegating-datasource sqlite)
          mysql (fake-pool :mysql)]
      (is (identical? sqlite (ds/swap-delegate! proxy mysql)) "返回旧的底层池,交给调用方关闭")
      (is (identical? mysql (ds/get-delegate proxy)))
      (is (nil? (ds/swap-delegate! (fake-pool :not-a-proxy) mysql))
          "没登记过的对象换不动,也不会误改别人的池"))))

(deftest connection-init-is-idempotent-test
  (testing ":db.sql/connection 只在拿到普通池时包一层代理"
    (let [plain (fake-pool :hikari)
          proxy (ds/delegating-datasource plain)
          init (get-method ig/init-key :db.sql/connection)]
      (is (ds/swappable? (with-redefs [conman/connect! (fn [_] plain)]
                           (init :db.sql/connection {:jdbc-url "jdbc:sqlite:x"})))
          "普通池要包上代理,否则 swap-db! 无从热切换")
      (is (identical? proxy
                      (with-redefs [conman/connect! (fn [_] proxy)]
                        (init :db.sql/connection {:jdbc-url "jdbc:sqlite:x"})))
          "已经包过一层就原样返回:嵌套代理让监控页解不到 Hikari(显示 unknown),
           swap-db! 也只换最外层、里层继续用已经关闭的池"))))

(deftest proxy-forwards-datasource-api-test
  (testing "代理是完整的 DataSource:除了 getConnection,其余方法转发给底层池"
    (let [calls  (atom [])
          writer (java.io.PrintWriter. (java.io.StringWriter.))
          real   (reify
                   javax.sql.DataSource
                   (getLoginTimeout [_] 5)
                   (setLoginTimeout [_ seconds] (swap! calls conj [:setLoginTimeout seconds]))
                   (getLogWriter [_] writer)
                   (setLogWriter [_ w] (swap! calls conj [:setLogWriter w]))
                   (isWrapperFor [_ iface] (swap! calls conj [:isWrapperFor iface]) true))
          ^javax.sql.DataSource proxy (ds/delegating-datasource real)]
      ;; 只实现 getConnection 的代理是一个残缺的 DataSource:任何拿它当池用的库都会撞上
      ;; AbstractMethodError(conman 设置登录超时就是这样),而报错位置离真正的原因很远。
      (is (= 5 (.getLoginTimeout proxy)) "读方法转到底层池")
      (is (identical? writer (.getLogWriter proxy)))
      (.setLoginTimeout proxy 9)
      (.setLogWriter proxy writer)
      (is (= [[:setLoginTimeout 9] [:setLogWriter writer]] @calls) "写方法也要落到底层池")
      (is (true? (.isWrapperFor proxy java.sql.Connection)) "Wrapper 协议同样转发"))))

(deftest close-pool-test
  (testing "close-pool! 关的是解包后的真池,重复调用不报错"
    (let [proxy (ds/delegating-datasource (fake-pool :fake))]
      (is (nil? (ds/close-pool! proxy)) "底层不是 HikariDataSource 时安静跳过")
      (is (ds/swappable? proxy) "没关掉假池也不该顺手摘掉登记"))))

(def ^:private pool-file "target/datasource-pool-test.db")

(deftest close-pool-reaches-hikari-test
  (testing "真池:close-pool! 透过代理关掉它,再调一次不会重复关闭"
    (io/make-parents pool-file)          ;; target/ 在干净克隆里可能还不存在
    (let [spec (:db.sql/connection
                (config/with-dialect-pool {:db.sql/connection {:jdbc-url (str "jdbc:sqlite:" pool-file)}} {}))
          conn ((get-method ig/init-key :db.sql/connection) :db.sql/connection spec)
          ^HikariDataSource pool (ds/unwrap conn)]
      (try
        (is (instance? HikariDataSource pool)
            "unwrap 要穿过代理拿到 HikariCP,监控页读的就是它")
        (is (not (.isClosed pool)))
        (ds/close-pool! conn)
        (is (.isClosed pool) "halt 时代理背后的池真的被关掉,不会留下抱着死池的老引用")
        (is (nil? (ds/close-pool! conn)) "已关闭的池再关一次不报错")
        (finally
          (.close pool)
          (io/delete-file pool-file true))))))
