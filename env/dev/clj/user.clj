(ns user
  "开发期 REPL 入口:所有助手都取自 com.ruoyi.dev,这一层只放手敲用的短名字。

   bb backend / bb dev 用 `clj -M:dev -e (require 'user) -m com.ruoyi.core` 启动,所以连上
   nREPL(默认 7000,macOS 上常用 NREPL_PORT=7200)就能直接敲这些短名字。

     (rd)   重载源码并只更新 HTTP 链;看 :status,需要重启时会提示 rr
     (rr)   重启系统:nREPL 留在原地,改 .sql / system.edn / 路由都生效
     (rs)   看运行中的状态:profile、组件、连接池实时数字
     (q :find-user-by-name {:user_name \"admin\"})
     (req :get \"/system/user\" :params {:page 1 :size 5})   ; 返回解析后的 body,看 :code / :data"
  (:require
   [clojure.pprint]
   [clojure.spec.alpha :as s]
   [com.ruoyi.core :as core]
   [com.ruoyi.dev :as dev]
   [expound.alpha :as expound]
   [lambdaisland.classpath :as licp]
   [migratus.core]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))
(add-tap (bound-fn* clojure.pprint/pprint))

(defn init!
  "启动时设定热重载的扫描范围,并把持有运行期状态的命名空间标记为不可卸载
   (详见 com.ruoyi.dev/reload-exclusions)。bb backend / bb dev 加载本 ns 时执行一次。"
  []
  (dev/init-refresh!))

(init!)

(defn go
  "从头启动系统(dev profile)。仅在应用还没跑起来时用,例如停掉后端后手工再拉起。"
  []
  (core/start-app {:opts {:profile :dev}}))

(defn halt
  "停掉整个系统,包括 nREPL 组件(连着 REPL 时会把自己断开,日常用 rr)。"
  []
  (core/stop-app))

(defn- component
  [k]
  (when-let [sys (dev/system)]
    (k sys)))

(defn reset-db
  "清空并重建开发库。"
  []
  (migratus.core/reset (component :db.sql/migrations)))

(defn rollback
  "回滚最后一次迁移。"
  []
  (migratus.core/rollback (component :db.sql/migrations)))

(defn migrate
  "执行待处理的迁移。"
  []
  (migratus.core/migrate (component :db.sql/migrations)))

(defn swap-db!
  "热切换底层连接池(SQLite ↔ MySQL),不重启 JVM。"
  [jdbc-url & opts]
  ((resolve 'com.ruoyi.infra.db/swap-db!) (dev/system) jdbc-url (apply hash-map opts)))

(defn update-deps
  "改了 deps.edn 之后刷新 classpath。"
  []
  (licp/update-classpath! {:aliases [:dev :test]}))

;; 短名字:REPL 里手敲用。重载清单由 com.ruoyi.dev 从代码本身派生(defonce + 排除表),
;; 不要再在这里抄一份 ns 列表 —— 抄出来的那份迟早和源码对不上。
(def rd dev/reload)
(def rr dev/restart!)
(def ra dev/reload)
(def rs dev/state)
(def q dev/q)
(def req dev/request)
(def sys dev/system)
(def cfg dev/prepared-config)

(comment
  (rd)                                  ; 改完 .clj 逻辑
  (rr)                                  ; 改完 .sql / system.edn / 路由
  (rs)                                  ; 看状态
  (q :find-user-by-name {:user_name "admin"})
  (req :get "/system/post" :body {:post-name "店长" :post-code "dianzhang"}))
