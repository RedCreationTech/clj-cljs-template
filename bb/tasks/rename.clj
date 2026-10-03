(ns tasks.rename
  "bb rename <新命名空间> <新项目名>:把模板的命名空间与项目名换成你的。
   旧值从 kit.edn 读取,所以可以重复执行(例如先试一个名字再改)。"
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [tasks.util :as u]))

(def ^:private text-exts #{"clj" "cljs" "cljc" "cljd" "dart" "edn" "md" "org" "html" "svg" "sh" "js" "mjs" "json" "sql" "xml" "yml" "yaml" "py"})
(def ^:private text-names #{"Dockerfile" "Makefile" ".gitignore"})
;; 移动端只有 src/test 下的 .cljd、lib/main.dart、pubspec.yaml 与 deps.edn 需要改写:
;; 平台工程目录与 Dart 构建产物都由 bb mobile:create 按 kit.edn 重新生成,改名时不碰。
(def ^:private skip-dirs #{"node_modules" ".git" "target" ".shadow-cljs" ".cpcache" ".clj-kondo/.cache" ".lsp/.cache"
                           "build" ".dart_tool" ".clojuredart" "cljd-out" ".plugin_symlinks" ".idea"})
(def ^:private skip-paths #{"resources/public/js" "docs/training" "RUOYI_VUE_COMPARISON.md"
                            "mobile/macos" "mobile/ios" "mobile/android" "mobile/linux" "mobile/windows" "mobile/web"})
(def ^:private src-roots ["src/clj" "src/cljs" "test/clj" "test/cljs" "env/dev/clj" "env/prod/clj" "env/test/clj"])
(def ^:private mobile-roots ["mobile/src" "mobile/test"])

(defn- munge-seg [s] (str/replace s "-" "_"))
(defn- ns->path [ns-name] (->> (str/split ns-name #"\.") (map munge-seg) (str/join "/")))
(defn- key-prefix [s] (str (munge-seg s) "_"))

(defn- skip? [rel]
  (or (some #(or (= rel %) (str/starts-with? rel (str % "/"))) (concat skip-dirs skip-paths))
      (some #(str/includes? rel (str "/" % "/")) skip-dirs)))

(defn- text-files []
  (->> (fs/glob "." "**" {:hidden true})
       (filter fs/regular-file?)
       (map #(str (fs/relativize (fs/absolutize ".") (fs/absolutize %))))
       (map #(str/replace % "\\" "/"))
       (remove skip?)
       (filter #(or (text-exts (fs/extension %)) (text-names (fs/file-name %))))))

(defn- replacer [{:keys [old-ns new-ns old-path new-path old-name new-name]}]
  (let [old-prefixes (distinct [(key-prefix (last (str/split old-ns #"\."))) (key-prefix old-name)])
        rules (concat [[(re-pattern (java.util.regex.Pattern/quote old-ns)) new-ns]
                       [(re-pattern (java.util.regex.Pattern/quote old-path)) new-path]]
                      (for [p old-prefixes] [(re-pattern (str "\\b" (java.util.regex.Pattern/quote p))) (key-prefix new-name)])
                      [[(re-pattern (str "\\b" (java.util.regex.Pattern/quote old-name) "\\b")) new-name]])]
    (fn [text]
      (reduce (fn [t [re s]] (str/replace t re (str/re-quote-replacement s))) text rules))))

(defn- move-tree! [base old-path new-path]
  (let [src (fs/path base old-path)
        dst (fs/path base new-path)]
    (when (fs/exists? src)
      (if (fs/exists? dst)
        (u/warn "目标目录已存在,跳过:" dst)
        (do (fs/create-dirs (fs/parent dst))
            (fs/move src dst)
            ;; 清理搬空了的旧父目录(例如 src/clj/com)
            (loop [p (fs/parent src)]
              (when (and p (not= (str p) base) (fs/exists? p) (empty? (fs/list-dir p)))
                (fs/delete p)
                (recur (fs/parent p))))
            (println "  移动" (str src) "→" (str dst)))))))

(defn- validate! [new-ns new-name]
  (when-not (and new-ns new-name)
    (u/fail! "用法: bb rename <命名空间> <项目名>   例: bb rename com.acme.myapp myapp"))
  (when-not (re-matches #"[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+" new-ns)
    (u/fail! "命名空间需形如 com.acme.myapp(小写字母/数字/连字符,至少两段)"))
  (when-not (re-matches #"[a-z][a-z0-9-]*" new-name)
    (u/fail! "项目名需形如 myapp / my-app(小写字母/数字/连字符)")))

(defn rename! [[new-ns new-name]]
  (validate! new-ns new-name)
  (let [{old-ns :ns-name old-path :sanitized old-name :name} (u/project)
        ctx {:old-ns old-ns :new-ns new-ns :old-path old-path :new-path (ns->path new-ns)
             :old-name old-name :new-name new-name}
        rewrite (replacer ctx)
        changed (count (for [f (text-files)
                             :let [text (slurp f) new (rewrite text)]
                             :when (not= text new)]
                         (spit f new)))]
    (u/info old-ns " → " new-ns "," old-name " → " new-name ":改写 " changed " 个文件")
    ;; build.clj 的 main class 必须是 munge 后的类名(命名空间里的 - 对应类名里的 _)
    (let [build (slurp "build.clj")]
      (spit "build.clj" (str/replace build #"\(def main-cls \"[^\"]+\"\)"
                                     (str "(def main-cls \"" (munge-seg new-ns) ".core\")"))))
    (when (not= old-path (:new-path ctx))
      (doseq [root src-roots] (move-tree! root old-path (:new-path ctx))))
    ;; 移动端的命名空间就是项目名本身(ruoyi → myapp):上面的改写规则会把 (ns ruoyi.main)
    ;; 变成 (ns myapp.main),目录必须跟着搬,否则 clj -M:cljd compile 找不到源文件。
    (when (not= old-name new-name)
      (doseq [root mobile-roots] (move-tree! root old-name new-name)))
    (println (str "\n完成。接着执行:\n"
                  "  bb clean && bb lint && bb test && bb test:cljs\n"
                  "  用了移动端再跑一遍:bb mobile:clean && bb mobile:compile && bb mobile:test"
                  "(旧项目名的 Dart 产物不清掉会被重复跑)\n"
                  "  再改 src/cljs/" (:new-path ctx) "/frontend/config.cljs 里的 app-name / repo-url"))))
