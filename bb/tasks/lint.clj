(ns tasks.lint
  "静态检查:bb lint = clj-kondo(warning 即失败)+ 迁移文件 + 规模约束 + 分页约定 + 脚手架登记点。"
  (:require
   [babashka.fs :as fs]
   [babashka.pods :as pods]
   [clojure.string :as str]
   [edamame.core :as e]
   [tasks.new-module :as nm]
   [tasks.util :as u]))

;; ─── clj-kondo ─────────────────────────────────────────────────────

(def kondo-version
  "与 CI 安装的 clj-kondo 保持一致,避免本地与 CI 结论不同。"
  "2026.08.04")

(def lint-paths ["src" "test" "env" "bb" "scripts"])

(defn kondo!
  "本机装了 clj-kondo 就直接调用;否则通过 babashka pod 自动下载同版本。"
  []
  (if-let [bin (u/exe "clj-kondo")]
    (u/exec! (into [bin "--fail-level" "warning" "--lint"] lint-paths))
    (do (pods/load-pod 'clj-kondo/clj-kondo kondo-version)
        (let [kondo-run (requiring-resolve 'pod.borkdude.clj-kondo/run!)
              kondo-print (requiring-resolve 'pod.borkdude.clj-kondo/print!)
              result (kondo-run {:lint lint-paths})]
          (kondo-print result)
          (when (pos? (+ (get-in result [:summary :error] 0)
                         (get-in result [:summary :warning] 0)))
            (System/exit 1))))))

;; ─── 迁移文件 ──────────────────────────────────────────────────────

(def ^:private dirs {:sqlite "resources/migrations-sqlite" :mysql "resources/migrations"})

(def ^:private forbidden
  "每个目录里不应出现的对方方言(出现通常意味着复制粘贴没改)。"
  {:mysql [[#"(?i)\bAUTOINCREMENT\b" "SQLite 的 AUTOINCREMENT(MySQL 用 AUTO_INCREMENT)"]
           [#"(?i)INSERT\s+OR\s+(IGNORE|REPLACE)" "SQLite 的 INSERT OR IGNORE/REPLACE(MySQL 用 INSERT IGNORE)"]
           [#"(?i)CREATE\s+(UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS" "CREATE INDEX IF NOT EXISTS(MySQL 不支持)"]
           [#"(?i)DROP\s+INDEX\s+IF\s+EXISTS" "DROP INDEX IF EXISTS(MySQL 不支持)"]
           [#"(?i)\bCASCADE\s*;" "TRUNCATE … CASCADE(PostgreSQL 语法)"]]
   :sqlite [[#"(?i)\bAUTO_INCREMENT\b" "MySQL 的 AUTO_INCREMENT"]
            [#"(?i)\bENGINE\s*=" "MySQL 的 ENGINE="]
            [#"(?i)INSERT\s+IGNORE\b" "MySQL 的 INSERT IGNORE(SQLite 用 INSERT OR IGNORE)"]
            [#"(?i)\bON\s+UPDATE\s+CURRENT_TIMESTAMP" "MySQL 的 ON UPDATE CURRENT_TIMESTAMP"]
            [#"(?i)\bCASCADE\s*;" "TRUNCATE … CASCADE(PostgreSQL 语法)"]]})

(defn- strip-comments [sql]
  (->> (str/split-lines sql)
       (remove #(str/starts-with? (str/trim %) "--"))
       (str/join "\n")))

(defn- statement-problems
  "Migratus 以 --;; 分隔语句:每段必须恰好一条语句,不能为空、不能塞多条。"
  [text]
  (->> (str/split text #"(?m)^--;;\s*$")
       (map-indexed
        (fn [i chunk]
          (let [body (str/trim (strip-comments chunk))
                n (count (re-seq #";\s*(?:\n|$)" body))]
            (cond
              (str/blank? body) (str "第 " (inc i) " 段为空(多余的 --;; 或只有注释)")
              (> n 1) (str "第 " (inc i) " 段含 " n " 条语句,语句之间要用 --;; 分隔")))))
       (remove nil?)))

(defn- migration-names [dir]
  (->> (fs/list-dir dir "*.sql") (map (comp str fs/file-name)) sort))

(defn- base-names [dir]
  (->> (migration-names dir) (map #(str/replace % #"\.(up|down)\.sql$" "")) set))

(defn- dialect-problems [db dir]
  (for [f (migration-names dir)
        :let [text (slurp (str dir "/" f))
              code (strip-comments text)]
        p (concat (for [[re why] (forbidden db) :when (re-find re code)] why)
                  (statement-problems text))]
    (str dir "/" f ": " p)))

(defn- pairing-problems []
  (let [s (base-names (:sqlite dirs)) m (base-names (:mysql dirs))]
    (concat
     (for [b (sort (remove m s))] (str "只在 SQLite 目录存在:" b ",MySQL 目录也要加"))
     (for [b (sort (remove s m))] (str "只在 MySQL 目录存在:" b ",SQLite 目录也要加"))
     (for [[_ dir] dirs
           f (migration-names dir)
           :when (str/ends-with? f ".up.sql")
           :let [down (str/replace f #"\.up\.sql$" ".down.sql")]
           :when (not (fs/exists? (str dir "/" down)))]
       (str dir "/" f ": 缺少 " down)))))

(def ^:private query-forbidden
  "resources/sql 下的查询两库共用,不能用只在一个库里成立、或两库结果不同的写法。"
  [[#"(?i)\bCURRENT_TIMESTAMP\b|\bNOW\s*\(\)" "CURRENT_TIMESTAMP / NOW()(SQLite 是 UTC、MySQL 是会话时区;用 :now,见 infra.clock)"]
   [#"(?i)\bdatetime\s*\(" "SQLite 的 datetime()"]
   [#"\|\|" "|| 字符串拼接(MySQL 默认当作 OR;用 CONCAT 或在 Clojure 里拼)"]])

(defn- query-problems []
  (for [f (->> (fs/glob "resources/sql" "*.sql") (map str) sort)
        :let [code (strip-comments (slurp f))]
        [re why] query-forbidden
        :when (re-find re code)]
    (str f ": " why)))

(defn migrations!
  "两套迁移同名成对、各有 down、语句分隔正确、没有混入对方方言;共用查询不含单库写法。"
  []
  (let [problems (concat (pairing-problems)
                         (dialect-problems :sqlite (:sqlite dirs))
                         (dialect-problems :mysql (:mysql dirs))
                         (query-problems))]
    (if (seq problems)
      (do (doseq [p problems] (println "  ✖" p))
          (u/fail! "迁移检查未通过(" (count problems) " 处)"))
      (println "✔ 迁移检查通过:" (count (base-names (:sqlite dirs))) "组迁移,SQLite/MySQL 成对且语法干净;共用查询无单库写法"))))

;; ─── 规模约束 ──────────────────────────────────────────────────────

(def ns-limit 500)
(def fn-limit 50)
(def ^:private fn-heads '#{defn defn- defmacro defmethod})

(def ^:private src-roots
  "规模约束覆盖的目录。mobile/ 也在里面:.cljd 与 .clj 用同一套纪律,
   移动端拆分依赖命名空间(config/json/api/fx/events/subs/model/views),
   不检查就会一路膨胀成一个巨大的 main.cljd。"
  ["src" "env" "test" "bb" "scripts" "mobile/src" "mobile/test"])

(def ^:private src-glob "**.{clj,cljs,cljc,cljd}")

(defn- parse-forms [text]
  (e/parse-string-all text {:all true
                            :auto-resolve (constantly 'user)
                            :readers (fn [_] identity)
                            :features #{:clj :cljs}
                            :read-cond :allow
                            :row-key :line
                            :end-row-key :end-line}))

(defn- file-report [path]
  (let [text (slurp path)
        lines (count (str/split-lines text))
        fns (for [form (parse-forms text)
                  :when (and (seq? form) (fn-heads (first form)))
                  :let [{:keys [line end-line]} (meta form)]]
              {:path path :name (second form) :line line :length (inc (- end-line line))})]
    {:path path :lines lines :fns fns}))

(defn constraints!
  "命名空间 ≤ 500 行,defn/defn-/defmacro/defmethod ≤ 50 行(按原始行数,含 docstring 与空行)。
   覆盖 .clj/.cljs/.cljc 与移动端的 .cljd。"
  []
  (let [files (->> src-roots
                   (mapcat #(fs/glob % src-glob))
                   (map str) sort)
        reports (map file-report files)
        big-files (filter #(> (:lines %) ns-limit) reports)
        long-fns (->> reports (mapcat :fns) (filter #(> (:length %) fn-limit)))]
    (println (format "检查 %d 个文件、%d 个函数;最大文件 %d 行,最长函数 %d 行"
                     (count reports) (count (mapcat :fns reports))
                     (apply max 0 (map :lines reports))
                     (apply max 0 (map :length (mapcat :fns reports)))))
    (doseq [{:keys [path lines]} big-files] (println (format "  ✖ %s: %d 行 > %d" path lines ns-limit)))
    (doseq [{:keys [path name line length]} long-fns]
      (println (format "  ✖ %s:%d %s: %d 行 > %d" path line name length fn-limit)))
    (if (or (seq big-files) (seq long-fns))
      (u/fail! "规模约束未通过:超限的文件/函数请按职责拆分(见 AGENTS.md)")
      (println "✔ 规模约束通过"))))

;; ─── 列表分页约定 ──────────────────────────────────────────────────

(def ^:private hand-rolled-pagination
  "页面里手写 :pagination {…} 或 :pagination #js {…}:漏掉 :current / :onChange 时,页码会跳但数据不换。"
  #":pagination\s*(?:#js\s*)?\{")

(defn pagination-conventions!
  "列表页的 :pagination 必须来自 components/pagination(table-pagination / client-pagination)。"
  []
  (let [files (->> (fs/glob "src/cljs" "**.cljs")
                   (map str) sort (filter #(str/includes? % "/pages/")))
        problems (for [f files
                       :let [hits (keep (fn [[i line]] (when (re-find hand-rolled-pagination line) (inc i)))
                                        (map-indexed vector (str/split-lines (slurp f))))]
                       :when (seq hits)]
                   (str f ":" (str/join "," hits) " 手写了 :pagination 属性"))]
    (if (seq problems)
      (do (doseq [p problems] (println "  ✖" p))
          (u/fail! "分页约定未通过:服务端分页用 pagination/table-pagination,本地翻页用 client-pagination"))
      (println "✔ 分页约定通过:" (count files) "个页面表格的分页属性都来自 components/pagination"))))

;; ─── 开发期助手(env/dev/clj)──────────────────────────────────────

(def dev-helpers-dir "env/dev/clj")

(def second-state-holder
  "integrant.repl 自己有一个 atom 存系统 map,而装配只发生在 core/start-app 写的
   com.ruoyi.integrant.state/system 上。两处状态迟早不同步:一边重启完能查库,另一边读到 nil。
   只认代码里的引用(require 向量、全限定调用),注释/docstring 里提到它是在解释为什么不用。"
  #"\[[\s]*integrant\.repl|\(integrant\.repl/|integrant\.repl\.state/")

(defn- clj-files [dir]
  (->> (fs/glob dir "**.clj") (map str) sort))

(defn- source-problems
  "env/dev/clj、test/clj 里不再出现 integrant.repl(系统状态只有一份)。"
  []
  (for [dir [dev-helpers-dir "test/clj"]
        f (clj-files dir)
        :let [hits (keep (fn [[i line]]
                           (when (re-find second-state-holder line) (inc i)))
                         (map-indexed vector (str/split-lines (slurp f))))]
        :when (seq hits)]
    (str f ":" (str/join "," hits) " 又引入了第二份系统状态(integrant.repl)")))

(def integrant-method-override
  "覆盖 Integrant 生命周期方法(:handler/ring 这种自己模块的键不算覆盖,是唯一定义)。"
  #"\(\s*defmethod\s+(?:ig|integrant\.core)/(?:init-key|halt-key!)\s+")

(def upstream-method-capture
  "抓住上游(库里原本那个)Integrant 方法,再把自己的 defmethod 装上去。
   热重载会重新抓一次,这次抓到的是本 ns 刚装上的那个 —— com.ruoyi.integrant.trace 的事故:
   连接池每重载一次多套一层代理,监控页解不到 Hikari,老代理抱着已关闭的池,请求全部 401。"
  #"\(\s*get-method\s+(?:ig|integrant\.core)/(?:init-key|halt-key!)\s+")

(def runtime-identity-definition
  "`defonce` 交出去的是运行期身份(atom、注册表、上游方法清单):组件 init 时抓住的就是那个对象本身,
   卸载重载会换成新对象,系统继续引用旧的那份。"
  #"\(\s*defonce\s+")

(defn- ns-symbol [path]
  (-> path (str/replace #"\.clj$" "") (str/replace #"^src/clj/" "") (str/replace "/" ".") symbol))

(defn- declared-exclusions
  "读出 com.ruoyi.dev/reload-exclusions 里登记的命名空间(清单本身只有一份,不要再抄)。"
  []
  (let [f (str dev-helpers-dir "/com/ruoyi/dev.clj")]
    (when-not (fs/exists? f) (u/fail! "找不到开发期助手:" f))
    (let [text (slurp f)
          i (str/index-of text "(def reload-exclusions")]
      (when-not i (u/fail! "com.ruoyi.dev 里没有 reload-exclusions"))
      (let [form (subs text i)
            start (str/index-of form "[")
            end (str/index-of form "]")]
        (when-not (and start end (< start end))
          (u/fail! "读不出 reload-exclusions 的向量,格式变了(检查 " f ")"))
        (let [v (read-string (subs form start (inc end)))]
          (when-not (and (vector? v) (every? symbol? v))
            (u/fail! "reload-exclusions 必须是符号向量,元素是命名空间:" v))
          v)))))

(defn- src-facts []
  (for [f (clj-files "src/clj")
        :let [text (slurp f)]]
    {:ns        (ns-symbol f)
     :override? (boolean (re-find integrant-method-override text))
     :capture?  (boolean (re-find upstream-method-capture text))
     :defonce?  (boolean (re-find runtime-identity-definition text))}))

(defn- reload-hygiene-problems
  "reload-exclusions 与源码互相咬合:危险的必须登记,登记的必须仍然危险。"
  []
  (let [declared (set (declared-exclusions))
        facts (src-facts)
        by-ns (into {} (map (juxt :ns identity)) facts)
        dangerous (->> facts (keep (fn [{:keys [override? capture? ns]}]
                                     (when (and override? capture?) ns))) set)]
    (concat
     (for [ns-sym (sort (remove declared dangerous))]
       (str ns-sym " 覆盖 Integrant 生命周期方法又抓取上游实现:热重载会再套一层代理,"
            "运行中的组件抱着旧对象 —— 必须登记进 com.ruoyi.dev/reload-exclusions"))
     (for [ns-sym (sort (remove (set (keys by-ns)) declared))]
       (str "reload-exclusions 登记的 " ns-sym " 在 src/clj 下没有对应文件(改名或删掉了?)"))
     (for [ns-sym (sort (filter (set (keys by-ns)) declared))
           :let [{:keys [override? capture? defonce?]} (get by-ns ns-sym)]
           :when (not (or override? capture? defonce?))]
       (str ns-sym " 登记在 reload-exclusions 里,但既没有 defonce 的运行期身份,也不覆盖 Integrant 方法,"
            "热重载它换不掉任何东西 —— 可以从清单里移除")))))

(defn dev-hygiene!
  "开发期约定:系统状态只有一份,热重载保护表跟得上源码。"
  []
  (let [problems (concat (source-problems) (reload-hygiene-problems))]
    (if (seq problems)
      (do (doseq [p problems] (println "  ✖" p))
          (u/fail! "开发期约定检查未通过(" (count problems) " 处):状态只能有一份,reload-exclusions 要跟得上源码"))
      (println "✔ 开发期约定通过:没有第二份系统状态;"
               (count (declared-exclusions)) "个不参与热重载的命名空间与 src/clj 对得上"))))

;; ─── E2E 用例（tests/e2e）──────────────────────────────────────────

(def e2e-dir "tests/e2e")

(def e2e-config "playwright.config.js")

(def ^:private e2e-forbidden
  "所有用例文件共用的写法问题。逐行匹配,注释也算 —— 注释里的 pageNum 一样会被复制粘贴带走。"
  [[#"\btest\.only\s*\(|\bdescribe\.only\s*\(|\.fixme\s*\("
    "test.only / describe.only / test.fixme:会静默跳过其余用例,门禁就成了摆设"]
   [#"\bpageNum\b|\bpageSize\b|page-num|page-size"
    "分页参数只有 page / size(见 AGENTS.md「后端分页参数使用 page / size」)"]
   [#"\.goto\s*\(\s*['\"`]\s*http"
    "写死了 http:// 绝对地址:用相对路径走 baseURL,换端口时不用改用例"]])

(def ^:private spec-forbidden
  "只约束 *.spec.js:辅助文件(tour-helper.js)为了镜头节奏可以故意等一会儿。"
  [[#"\.waitForTimeout\s*\(" "固定 sleep:慢机器上时好时坏,改成等元素或等断言"]])

(defn- e2e-files [] (->> (fs/glob e2e-dir "**.js") (map str) sort))

(defn- spec-files [] (filter #(str/ends-with? % ".spec.js") (e2e-files)))

(defn- line-problems
  "返回 `文件:行 原因`。"
  [file patterns]
  (for [[i line] (map-indexed vector (str/split-lines (slurp file)))
        [re why] patterns
        :when (re-find re line)]
    (str file ":" (inc i) " " why)))

(defn- assertion-problems [specs]
  (for [f specs
        :when (not (re-find #"\bexpect[.(]" (slurp f)))]
    (str f " 一条断言都没有:只点不验的用例发现不了回归")))

(defn- tour-is-excluded? []
  ;; 导览录像要 17 分钟,只归 playwright.tour.config.js 管;这条 testIgnore 一旦被删,
  ;; bb e2e 会把录像当常规门禁跑,CI 的前端任务直接超时。
  (boolean (re-find #"testIgnore\s*:\s*['\"].*tour" (slurp e2e-config))))

(defn e2e-conventions!
  "E2E 用例的静态约定:不残留 only/fixme、分页只用 page/size、不写死绝对地址、spec 不 sleep 且必须有断言、tour 仍被排除。"
  []
  (let [files (e2e-files)
        specs (spec-files)
        problems (concat (mapcat #(line-problems % e2e-forbidden) files)
                         (mapcat #(line-problems % spec-forbidden) specs)
                         (assertion-problems specs)
                         (when-not (tour-is-excluded?)
                           [(str e2e-config " 不再 testIgnore tour:bb e2e 会把 17 分钟的录像当常规门禁跑")]))]
    (if (seq problems)
      (do (doseq [p problems] (println "  ✖" p))
          (u/fail! "E2E 用例约定未通过(" (count problems) " 处)"))
      (println "✔ E2E 用例约定通过:" (count files) "个用例文件 /" (count specs)
               "条 spec 都有断言;tour 仍由 playwright.tour.config.js 单独跑"))))
