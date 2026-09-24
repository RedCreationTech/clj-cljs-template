(ns tasks.lint
  "静态检查:bb lint = clj-kondo(warning 即失败)+ 迁移文件检查 + 规模约束。"
  (:require
   [babashka.fs :as fs]
   [babashka.pods :as pods]
   [clojure.string :as str]
   [edamame.core :as e]
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
(def ^:private src-roots ["src" "env" "test" "bb" "scripts"])

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
  "命名空间 ≤ 500 行,defn/defn-/defmacro/defmethod ≤ 50 行(按原始行数,含 docstring 与空行)。"
  []
  (let [files (->> src-roots
                   (mapcat #(fs/glob % "**.{clj,cljs,cljc}"))
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
