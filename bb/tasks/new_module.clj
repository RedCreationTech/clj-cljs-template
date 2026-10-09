(ns tasks.new-module
  "bb new-module:生成一个可直接运行的 CRUD 业务模块,并在各登记点自动插入注册代码。

   生成内容:三库迁移(建表 + 「业务管理」菜单并授权 admin)、HugSQL 查询、领域服务
   (Integrant 组件)、控制器、路由(Malli 校验 + Swagger)、后端集成测试、前端 api /
   re-frame 事件 / 页面、Playwright 用例。

   登记点是源码里的 `;; [new-module] <tag>` 注释行:新代码插在标记行之前,标记行保留,
   所以可以反复生成多个模块。所有修改先在内存里算好,缺标记或有冲突时一个文件都不写。"
  (:require
   [babashka.cli :as cli]
   [babashka.fs :as fs]
   [clojure.string :as str]
   [tasks.scaffold.backend :as backend]
   [tasks.scaffold.frontend :as frontend]
   [tasks.scaffold.model :as m]
   [tasks.scaffold.sql :as sql]
   [tasks.util :as u])
  (:import
   (java.util.regex Pattern)))

(def ^:private usage
  (str "用法:bb new-module <模块名> [--label 中文名] [--fields \"字段规格,...\"] [--dry-run]\n"
       "  模块名   小写 kebab-case,如 notice-board(表名 biz_notice_board,接口 /api/biz/notice-board)\n"
       "  字段规格 name:type[:required][:标签];type 可选 " (str/join "/" (map name (keys m/types))) "\n"
       "  例:bb new-module customer --label 客户 \\\n"
       "       --fields \"name:string:required:名称,phone:string:电话,level:int:等级,vip:bool:VIP,remark:text:备注\""))

(def ^:private icon "ContainerOutlined")

(defn- dirs [{:keys [path-root]}]
  {:clj (str "src/clj/" path-root)
   :cljs (str "src/cljs/" path-root "/frontend")
   :test (str "test/clj/" path-root)})

(defn- new-files
  "要新建的文件:{相对路径 内容}。"
  [{:keys [snake module] :as ctx}]
  (let [{:keys [clj cljs test]} (dirs ctx)]
    (merge (sql/migrations ctx)
           {(str "resources/sql/" snake ".sql") (sql/queries ctx)
            (str clj "/domain/" snake ".clj") (backend/domain ctx)
            (str clj "/web/controllers/" snake ".clj") (backend/controller ctx)
            (str clj "/web/routes/" snake ".clj") (backend/routes ctx)
            (str test "/web/controllers/" snake "_test.clj") (backend/test-ns ctx)
            (str cljs "/api/" snake ".cljs") (frontend/api ctx)
            (str cljs "/events/" snake ".cljs") (frontend/events ctx)
            (str cljs "/pages/" snake ".cljs") (frontend/page ctx)
            (str "tests/e2e/" module ".spec.js") (frontend/e2e ctx)})))

(defn registrations
  "[文件 标记 要插入的行];行内缩进相对于标记行。
   公开是为了 `bb lint:scaffold` 能在不改工作区的前提下核对每个标记行还在源码里。"
  [{:keys [module snake label service-key service-arg menu-path] :as ctx}]
  (let [{:keys [clj cljs]} (dirs ctx)
        kw (str ":" module)]
    [["resources/system.edn" "components"
      [service-key "{:query-fn #ig/ref :db.sql/query-fn" " :db       #ig/ref :db.sql/connection}" ""]]
     ["resources/system.edn" "route-services" [(str ":" service-arg " #ig/ref " service-key)]]
     ["resources/system.edn" "sql-files" [(str "\"sql/" snake ".sql\"")]]
     [(str clj "/web/routes/api.clj") "routes" [(str "(" module "-routes/routes opts)")]]
     ;; 不再往 env/dev/clj/user.clj 插 (require … :reload):待重载集合由
     ;; com.ruoyi.dev 从源码时间戳派生,生成的 domain ns 已经被 core.clj require 过,
     ;; 手抄一份 reload 清单正是「开发期约定」要避免的(见 bb lint:dev)。
     [(str cljs "/router.cljs") "routes" [(str "\"" menu-path "\" " kw)]]
     [(str cljs "/router.cljs") "page-names" [(str kw " \"" label "\"")]]
     [(str cljs "/pages/layout/menu_data.cljs") "menu-keys" [(str kw " \"" menu-path "\"")]]
     [(str cljs "/pages/layout/menu_data.cljs") "breadcrumbs" [(str kw " [\"首页\" \"业务管理\" \"" label "\"]")]]
     [(str cljs "/pages/layout/menu_data.cljs") "icons" [(str kw " \"" icon "\"")]]
     [(str cljs "/pages/layout/page_view.cljs") "pages" [(str kw " [" module "-page/" module "-page]")]]
     [(str cljs "/events/common.cljs") "tab-meta" [(str kw " {:label \"" label "\" :icon \"" icon "\"}")]]]))

(defn- requires
  "[文件 require 条目]:加载新命名空间(其中的 Integrant / re-frame 注册是副作用)。"
  [{:keys [ns-root module] :as ctx}]
  (let [{:keys [clj cljs]} (dirs ctx)]
    [[(str clj "/core.clj") (str "[" ns-root ".domain." module "]")]
     [(str clj "/web/routes/api.clj") (str "[" ns-root ".web.routes." module " :as " module "-routes]")]
     [(str cljs "/events.cljs") (str "[" ns-root ".frontend.events." module "]")]
     [(str cljs "/pages/layout/page_view.cljs") (str "[" ns-root ".frontend.pages." module " :as " module "-page]")]]))

;; ── 文本插入 ─────────────────────────────────────────────

(defn- lines-of [content] (vec (str/split content #"\n" -1)))

(defn- insert-at-marker
  "在 `;; [new-module] tag` 行之前插入 new-lines(补上标记行的缩进)。"
  [content file tag new-lines]
  (let [lines (lines-of content)
        target (str ";; [new-module] " tag)
        i (or (first (keep-indexed (fn [i l] (when (= target (str/trim l)) i)) lines))
              (u/fail! file " 里找不到标记行 `" target "`;请恢复该行后重试"))
        indent (re-find #"^\s*" (nth lines i))
        block (map #(if (str/blank? %) "" (str indent %)) new-lines)]
    (str/join "\n" (concat (take i lines) block (drop i lines)))))

(defn- lib-name [line]
  (second (re-find #"^\s*\[\"?([^\s\"\]]+)" line)))

(defn- insert-require
  "按字母序把条目插入 ns 的 (:require ...) 块;排在最后时把收尾括号挪到新行。"
  [content file entry]
  (let [lines (lines-of content)
        start (inc (or (first (keep-indexed (fn [i l] (when (str/includes? l "(:require") i)) lines))
                       (u/fail! file " 里找不到 (:require")))
        end (+ start (count (take-while #(re-find #"^\s+\[" %) (subvec lines start))))
        indent (re-find #"^\s*" (nth lines start))
        lib (lib-name entry)
        pos (or (first (filter #(pos? (compare (lib-name (nth lines %)) lib)) (range start end))) end)]
    (if (= pos end)
      (let [[_ body closers] (re-find #"^(.*\])(\)*)\s*$" (nth lines (dec end)))]
        (str/join "\n" (concat (take (dec end) lines) [body (str indent entry closers)] (drop end lines))))
      (str/join "\n" (concat (take pos lines) [(str indent entry)] (drop pos lines))))))

;; ── 冲突检查 ─────────────────────────────────────────────

(defn- unique-ts
  "迁移 id(时间戳)必须全局唯一:同一秒内连续生成多个模块时顺延到下一个空闲的秒。"
  [ts]
  (let [fmt (java.text.SimpleDateFormat. "yyyyMMddHHmmss")
        used (set (for [dir ["resources/migrations" "resources/migrations-sqlite"
                          "resources/migrations-postgresql"]
                        f (fs/list-dir dir)]
                    (re-find #"^\d+" (fs/file-name f))))]
    (loop [t ts]
      (if (used t)
        (recur (.format fmt (java.util.Date. (+ 1000 (.getTime (.parse fmt t))))))
        t))))

(defn- query-names [{:keys [module]}]
  (map #(str % module) ["list-" "count-" "find-" "create-" "update-" "delete-"]))

(defn- conflicts
  "返回冲突说明列表:目标文件已存在、前端已有同名关键字(路由 / app-db / 事件前缀)、HugSQL 查询重名。"
  [{:keys [module] :as ctx} files]
  (let [kw-re (re-pattern (str "(?<![\\w\\-:.]):" (Pattern/quote module) "(?![\\w\\-?!*])"))
        sql-re (re-pattern (str "--\\s*:name\\s+(" (str/join "|" (map #(Pattern/quote %) (query-names ctx)))
                                ")(-by-id)?!?\\s"))
        scan (fn [root pattern re]
               (for [f (fs/glob root pattern) :when (re-find re (slurp (str f)))] (str f)))]
    (concat
     (for [f (keys files) :when (fs/exists? f)] (str "文件已存在:" f))
     (for [f (scan (:cljs (dirs ctx)) "**.cljs" kw-re)] (str "前端已使用关键字 :" module ":" f))
     (for [f (scan "resources" "**.sql" sql-re)] (str "HugSQL 查询名冲突:" f)))))

;; ── 主流程 ───────────────────────────────────────────────

(defn- planned-edits
  "把所有登记与 require 应用到内存中的文件内容,返回 {文件 新内容}。"
  [ctx]
  (let [apply-edit (fn [acc [file f]]
                     (let [content (get acc file (slurp file))]
                       (assoc acc file (f content))))]
    (reduce apply-edit {}
            (concat
             (for [[file tag lines] (registrations ctx)]
               [file #(insert-at-marker % file tag lines)])
             (for [[file entry] (requires ctx)]
               [file #(if (str/includes? % entry) % (insert-require % file entry))])))))

(defn- report! [{:keys [module label ns-root menu-path perm-prefix]} files edits dry-run?]
  (println (str (if dry-run? "【试运行,未写入】" "✔ 已生成") "模块 " module "(" label ")"))
  (println "  新建:")
  (doseq [f (sort (keys files))] (println "    " f))
  (println "  登记:")
  (doseq [f (sort (keys edits))] (println "    " f))
  (when-not dry-run?
    (println (str "\n下一步:\n"
                  "  1. bb test -n " ns-root ".web.controllers." module "-test   # 生成的后端集成测试\n"
                  "  2. 重启 bb dev(system.edn 与迁移有变化,迁移在启动时自动执行),重新登录后\n"
                  "     打开 http://localhost:3000/" menu-path "(菜单:业务管理 / " label ")\n"
                  "  3. bb e2e tests/e2e/" module ".spec.js   # 生成的端到端用例\n"
                  "  4. 权限标识 " perm-prefix ":list/query/add/edit/remove 已登记为按钮菜单并授权给 admin;\n"
                  "     其他角色在「角色管理 → 分配权限」里勾选\n"
                  "  5. 按业务修改生成的代码;撤销生成可用 git checkout + git clean"))))

(defn scaffold-marker-problems
  "登记点自检,给 `bb lint:scaffold` 用(不写任何文件):
   模板里的 `;; [new-module] <tag>` 被删掉、改名,或 require 块被挪走时,
   bb new-module 要到运行期才失败,CI 的 scaffold 冒烟也会挂在同一个地方;
   这里在静态检查阶段就把话说清楚。"
  []
  (let [ctx (m/build {:module "demo" :label "示例" :fields "name:string:名称"
                      :project (u/project) :now (java.util.Date.)})
        marker? (fn [file tag]
                  (and (fs/exists? file)
                       (some #(= (str ";; [new-module] " tag) (str/trim %))
                             (lines-of (slurp file)))))]
    (concat
     (for [[file tag] (registrations ctx)
           :when (not (marker? file tag))]
       (str file " 缺少标记行 `;; [new-module] " tag "`"))
     (for [[file _entry] (requires ctx)
           :when (not (and (fs/exists? file)
                           (str/includes? (slurp file) "(:require")))]
       (str file " 里找不到 (:require 块")))))

(defn generate!
  [args]
  (let [{:keys [args opts]} (cli/parse-args args {:coerce {:dry-run :boolean}})
        {:keys [label fields dry-run help]} opts]
    (when (or help (not= 1 (count args)))
      (println usage)
      (System/exit (if help 0 1)))
    (let [ctx (try (m/build {:module (first args) :label label :fields fields
                             :project (u/project) :now (java.util.Date.)})
                   (catch clojure.lang.ExceptionInfo e (u/fail! (ex-message e) "\n\n" usage)))
          ctx (update ctx :ts unique-ts)
          files (new-files ctx)
          problems (conflicts ctx files)]
      (when (seq problems)
        (u/fail! "无法生成:\n  " (str/join "\n  " problems)))
      (let [edits (planned-edits ctx)]
        (when-not dry-run
          (doseq [[f content] (merge files edits)]
            (fs/create-dirs (fs/parent f))
            (spit f content)))
        (report! ctx files edits dry-run)))))
