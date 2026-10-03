(ns tasks.mobile
  "ClojureDart 移动端任务。

  mobile/ 是一个独立工程:自己的 deps.edn、自己的 pubspec.yaml、自己的 Dart/Flutter 工具链,
  与后端的 src/clj、shadow-cljs 和 uberjar 都不相干(根 deps.edn 的 :paths 里没有 mobile)。
  好处是 `bb test`、`bb uberjar` 不会被 Flutter SDK 绑住;代价是移动端要单独跑这几个任务。

  首次使用:bb mobile:doctor 看清缺什么,再 bb mobile:create 生成平台工程目录。"
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as p]
   [cheshire.core :as json]
   [clojure.string :as str]
   [tasks.dev :as dev]
   [tasks.narrate :as narrate]
   [tasks.tour :as tour]
   [tasks.util :as u]))

;; ─── 依赖检查 ──────────────────────────────────────────────────────

(def dir "mobile")

(def platform-dirs ["macos" "android" "ios" "linux" "windows" "web"])

(def network-entitlement
  "macOS 的 App Sandbox 默认拦出站连接,必须显式给这一条权限。"
  "com.apple.security.network.client")

(defn- flutter-exe []
  (or (u/exe "flutter")
      (u/fail! "找不到 flutter 命令。装法:https://docs.flutter.dev/get-started/install"
               "(本机 brew install flutter 会从源码编译,很慢,建议直接下官方 tarball 解压进 PATH)")))

(defn- run-cljd
  "在 mobile/ 目录里跑 ClojureDart 构建工具;aliases 是 clojure CLI 的 -M 别名字符串。
   编译就是移动端的类型检查器:clj -M:cljd compile 过了,Dart 侧才算静态检查过。"
  [aliases & args]
  (apply u/exec! (into (conj (u/clojure-cmd) (str "-M" aliases)) args) {:dir dir}))

(defn- cljd [& args]
  (apply run-cljd ":cljd" args))

(defn- capture
  "跑一条命令拿首行输出(不 inherit,所以能捕获);失败时返回提示而不是炸掉检查。"
  [cmd]
  (try
    (let [{:keys [exit out]} @(p/process cmd {:out :string :err :out})]
      (if (zero? exit)
        (first (str/split-lines (str/trim out)))
        "(命令失败,请手动执行)"))
    (catch Exception _ "(命令执行异常)")))

(defn- report
  "打印一项工具链检查结果。"
  [name version-fn]
  (if (u/exe name)
    (println (format "  ✔ %-10s %s" name (some-> (version-fn) str)))
    (println (format "  ✖ %-10s 未安装" name))))

(defn- entitlement-state
  "平台目录里的 macOS entitlements 是否已放行出站网络:{:files n :missing [...]}"
  []
  (let [files (remove #(fs/exists? (str dir "/" %))
                      ["macos/Runner/DebugProfile.entitlements"
                       "macos/Runner/Release.entitlements"])
        lacking (for [f (fs/glob dir "macos/Runner/*.entitlements")
                      :when (not (str/includes? (slurp (str f)) network-entitlement))]
                  (str f))]
    {:missing files :no-network lacking}))

(defn doctor!
  "打印移动端工具链的就绪情况;不安装任何东西,只告诉你缺什么。"
  []
  (u/info "移动端工具链检查")
  (if (u/exe "flutter")
    (do (report "flutter" #(capture [(flutter-exe) "--version"]))
        (println "  ✔ dart       (随 flutter 分发)"))
    (println "  ✖ flutter    未安装 —— bb mobile:* 全部无法运行"))
  (report "clojure" #(capture (conj (u/clojure-cmd) "--version")))
  (report "bb" #(capture ["bb" "--version"]))
  (when (u/exe "xcodebuild")
    (println "  ✔ xcode      (macOS/iOS 构建可用)"))
  (let [{:keys [missing no-network]} (entitlement-state)]
    (cond
      (seq missing)
      (u/info "还没有平台工程目录(缺 " (str/join ", " missing) "),跑 bb mobile:create")
      (seq no-network)
      (do (doseq [f no-network] (u/warn f " 没有 " network-entitlement " —— macOS 上所有请求都会 Operation not permitted"))
          (u/info "跑 bb mobile:create 会自动补上"))
      :else (println "  ✔ 平台目录   已生成,macOS 已放行出站网络"))))

;; ─── 工程引导 ──────────────────────────────────────────────────────

(defn- flutter-project-name []
  (str (:name (u/project)) "_mobile"))

(defn- org
  "flutter create 的 --org:反域名包名,直接用项目的命名空间。"
  []
  (str/lower-case (:ns-name (u/project))))

(defn- allow-outbound-network!
  "给 macOS 的 entitlements 补 com.apple.security.network.client(幂等)。

   flutter create 的模板开了 app sandbox 却没有这条权限,结果 Debug/Release 两种构建
   里每个出站请求都是 `SocketException … Operation not permitted, errno = 1`,
   而且只在真机/桌面包里出现,`flutter run -d chrome` 看不出来。"
  []
  (doseq [f (fs/glob dir "macos/Runner/*.entitlements")
          :let [content (slurp (str f))]]
    (if (str/includes? content network-entitlement)
      (println "  ✔" f "已有出站网络权限")
      (do (spit (str f)
                (str/replace-first content #"[ \t]*</dict>"
                                   (str "        <key>" network-entitlement "</key>\n"
                                        "        <true/>\n"
                                        "    </dict>")))
          (println "  ✔" f "已补" network-entitlement)))))

(defn- ensure-platforms!
  "平台目录(macos/ android/ …)缺了就用 flutter create 补齐。
   它只补不存在的文件,pubspec.yaml、lib/main.dart 与 .gitignore 会被保留。"
  [platforms]
  (let [missing (remove #(fs/exists? (str dir "/" %)) platforms)]
    (if (seq missing)
      (do (u/info "生成平台工程目录:" (str/join ", " missing))
          (u/exec! [(flutter-exe) "create" "."
                    "--project-name" (flutter-project-name)
                    "--org" (org)
                    "--platforms" (str/join "," missing)]
                   {:dir dir}))
      (println "✔ 平台目录已就绪:" (str/join ", " platforms))))
  (when (fs/exists? (str dir "/macos"))
    (u/info "检查 macOS 出站网络权限")
    (allow-outbound-network!)))

(defn- drop-scaffold-test!
  "删掉 flutter create 附带的 test/widget_test.dart:那是计数器示例,它 import 的 MyApp
   在我们这里不存在(lib/main.dart 只 export ruoyi.main/main),留着它 bb mobile:test 必挂。"
  []
  (let [f (str dir "/test/widget_test.dart")]
    (when (fs/exists? f)
      (fs/delete f)
      (println "  ✔ 已删除示例测试" f))))

(defn create!
  "bb mobile:create [macos android ios …] —— 生成平台工程目录(默认 macos),幂等。"
  [args]
  (let [platforms (if (seq args) (vec args) ["macos"])
        allowed (set platform-dirs)]
    (doseq [p platforms]
      (when-not (contains? allowed p)
        (u/fail! "未知平台:" p " 可选" (str/join " " allowed))))
    (ensure-platforms! platforms)
    (drop-scaffold-test!)
    (u/info "安装 Dart 依赖…")
    (u/exec! [(flutter-exe) "pub" "get"] {:dir dir})
    (cljd "init")
    (u/info "完成。下一步 bb mobile:compile")))

;; ─── 编译 / 运行 / 测试 ────────────────────────────────────────────

(defn compile!
  "bb mobile:compile —— 只编译,不启动。CI 用它当移动端的静态检查。"
  []
  (u/info "编译 mobile/ (clj -M:cljd compile)")
  (cljd "compile"))

(defn api-url
  "默认的接口地址:本机后端。端口与 bb dev 保持一致(PORT 环境变量可覆盖)。"
  []
  (str "http://127.0.0.1:" (u/env-int "PORT" 3000) "/api"))

(defn dart-defines
  "注入给 Dart 的编译期常量。整条必须是**一个**参数(`--dart-define=K=V`),
   拆成 `--dart-define` `K=V` 两个 token 时 flutter 会把 K=V 当成位置参数。
   要连开发机或测试环境时整体覆盖:
     MOBILE_DART_DEFINES=\"--dart-define=RUOYI_API_BASE_URL=http://192.168.1.10:3000/api\" bb mobile:run"
  [url]
  (let [existing (some-> (System/getenv "MOBILE_DART_DEFINES") str/trim)]
    (if (seq existing)
      (str/split existing #"\s+")
      [(str "--dart-define=RUOYI_API_BASE_URL=" url)])))

(defn run-app!
  "bb mobile:run —— 编译并在前台运行(clj -M:cljd flutter 会 watch .cljd 并热重载)。
   先跑 bb mobile:create 生成平台目录;Ctrl-C 退出。"
  []
  (let [url (api-url)]
    (when-not (u/http-ok? (str url "/health"))
      (u/warn "后端没有在 " url " 应答 /api/health —— 先 bb dev,或按 MOBILE_DART_DEFINES 的说明换地址"))
    (ensure-platforms! ["macos"])
    (u/info "运行移动端(接口地址 " url ",改 .cljd 会自动重编译 + 热重载,Ctrl-C 退出)")
    (apply cljd "flutter" (dart-defines url))))

(defn test!
  "bb mobile:test [命名空间…] —— 编译并跑 mobile/test 下的 .cljd 单元测试(cljd.test,语法同 clojure.test)。

   必须带 :test 别名:mobile/deps.edn 的 :paths 只有 src,test/ 是 :test 的 extra-paths,
   少了它 ClojureDart 找不到测试命名空间,只会编译 src 然后跑一个空的 flutter test。
   跑单个测试文件要给**生成出来的 Dart 路径**,并且用 -- 与 bb 的参数分隔:
     bb mobile:test -- test/cljd-out/ruoyi/json-test_test.dart
   直接传 ruoyi/json-test 或源文件路径都不行 —— cljd.build 会把位置参数当命名空间去 src 里找
   (报 Could not locate …/cljd.cljd),而命名空间参数又只解析 src,不含 test。"
  [args]
  (u/info "跑移动端单元测试(mobile/test)")
  (apply run-cljd ":cljd:test" "test" args))

(defn- clean-cljd-out!
  "只删生成出来的 Dart 源码。bb mobile:test 会在 lib/cljd-out 里留下测试命名空间的 import,
   紧接着 flutter build web 就会报 Error when reading 'lib/test/cljd-out/…_test.dart'。"
  []
  (doseq [p ["lib/cljd-out" "test/cljd-out"]
          :let [f (str dir "/" p)]
          :when (fs/exists? f)]
    (fs/delete-tree f)
    (println "  ✔ 已清理" f)))

(defn- web-build!
  "把 src 下的 .cljd 编成网页版产物。两个参数是为了在没有外网的机器上也能跑起来:
   --no-web-resources-cdn + UseLocalCanvasKit 让 CanvasKit 走本地那份,不打 www.gstatic.com;
   接口地址默认同源相对路径 /api,写成 http://127.0.0.1:… 会和页面所在origin 不同而被 CORS 拦掉。"
  [base]
  (u/exec! (into [(flutter-exe) "build" "web" "--base-href" "/mobile/" "--no-web-resources-cdn"
                  "--dart-define=UseLocalCanvasKit=true"
                  (str "--dart-define=RUOYI_API_BASE_URL=" base)]
                 (some-> (System/getenv "MOBILE_DART_DEFINES") str/trim (str/split #"\s+")))
           {:dir dir}))

(defn- web-deploy!
  "产物搬到 resources/public/mobile,后端原样伺服(和网页端同一个端口,不用另起服务)。"
  []
  (let [out "resources/public/mobile"]
    (when (fs/exists? out) (fs/delete-tree out))
    (fs/copy-tree (str dir "/build/web") out)
    (let [stage (fs/file "tests/e2e/mobile/stage.html")]
      (if (fs/exists? stage)
        (do (fs/copy stage (str out "/stage.html")) (println "  ✔ 录制舞台" (str out "/stage.html")))
        (u/warn "没有 tests/e2e/mobile/stage.html,只部署了 App 本体")))
    (println "✔ 打开" (str "http://localhost:" (u/env-int "PORT" 3000) "/mobile/stage.html"))))

(defn web!
  "bb mobile:web —— 编译移动端网页版并部署到 resources/public/mobile(录屏与演示用)。"
  []
  (u/info "编译移动端网页版")
  (ensure-platforms! ["web"])
  (clean-cljd-out!)
  (compile!)
  (web-build! (or (some-> (System/getenv "MOBILE_WEB_API_BASE") str/trim) "/api"))
  (web-deploy!))

(defn clean!
  "bb mobile:clean —— 删掉移动端的构建产物(平台工程目录保留)。"
  []
  (doseq [p ["build" ".dart_tool" ".clojuredart" "lib/cljd-out" "test/cljd-out" ".plugin_symlinks"]
          :let [full (str dir "/" p)]]
    (when (fs/exists? full)
      (fs/delete-tree full)
      (println "已删除" full)))
  (println "✔ 移动端产物已清理"))

;; ─── 导览录屏 ──────────────────────────────────────────────────────

(def demo-port 3210)

(def demo-out-dir "target/mobile-tour")

(def demo-db "target/mobile-demo.db")

(def seed-posts 4)

(def extra-posts 20)

(defn- demo-base [] (str "http://localhost:" demo-port))

(defn- api
  "打录屏后端的接口:四类失败里只要不是 200/业务码 200,直接给出中文原因退出。"
  [method path & {:keys [token body]}]
  (let [resp (http/request {:method method
                            :url (str (demo-base) "/api" path)
                            :headers (cond-> {"content-type" "application/json"}
                                       token (assoc "authorization" (str "Bearer " token)))
                            :body (some-> body json/generate-string)
                            :as :string
                            :throw false
                            :timeout 15000})
        parsed (when (= 200 (:status resp)) (json/parse-string (:body resp) true))]
    (cond
      (not= 200 (:status resp)) (u/fail! "录屏后端 " (name method) " " path " 返回 HTTP " (:status resp))
      (not= 200 (:code parsed)) (u/fail! "录屏后端 " (name method) " " path " 失败:" (:msg parsed))
      :else parsed)))

(defn- start-demo-backend!
  "录屏专用的后端:独立端口 + 独立 SQLite 文件(每次删掉重新迁移),不碰开发库。
   网页版产物由它一起伺服(resources/public/mobile),所以页面和接口同源,不会被 CORS 拦。"
  []
  (doseq [f [demo-db (str demo-db "-journal")]] (fs/delete-if-exists f))
  (u/require-free-ports! [["录屏 HTTP" demo-port] ["录屏 nREPL" (inc demo-port)]])
  (u/info "启动录屏后端 " (demo-base) " 库 " demo-db)
  (let [proc (u/start! "[demo]    " (dev/backend-cmd)
                       {:extra-env {"PORT" (str demo-port)
                                    "NREPL_PORT" (str (inc demo-port))
                                    "JDBC_URL" (str "jdbc:sqlite:" demo-db)}}
                       nil)]
    (u/wait-http! (str (demo-base) "/api/health") 600 #(p/alive? proc))
    proc))

(defn- seed-demo-data!
  "把岗位补到 24 条:种子的 4 条一页就装完了,翻页、两页页码这些镜头没东西可拍。
   post_sort 跟着编号递增,所以第 2 页第一条固定是 21(分镜用例断言的就是它)。"
  []
  (let [token (get-in (api :post "/auth/login"
                           :body {:username "admin" :password "admin123"})
                      [:data :token])]
    (doseq [i (range 1 (inc extra-posts))
            :let [n (+ seed-posts i)]]
      (api :post "/system/post" :token token
           :body {:post_name (format "演示岗位 %02d" i)
                  :post_code (format "demo_%02d" i)
                  :post_sort n
                  :status "0"
                  :remark "移动端导览分镜用"}))
    (u/info "已写入 " extra-posts " 条演示岗位,列表共 " (+ seed-posts extra-posts) " 条 = 两页")))

(defn- record!
  "起后端 → 灌数据 → 录四段分镜 → 关后端(异常也关,不留孤儿进程)。"
  [args]
  (let [proc (start-demo-backend!)]
    (try
      (seed-demo-data!)
      (tour/video! args)
      (finally
        (p/destroy-tree proc)
        (u/info "录屏后端已停止")))))

(defn- with-demo
  "把合成/旁白两个任务切到移动端的目录与画面参数:舞台页自带目录,所以不贴目录条;
   舞台整体是深色底,分镜卡的亮度阈值要跟着往下调,否则测不出卡片出现的时刻。"
  [f]
  (binding [tour/out-dir demo-out-dir
            tour/video-name "mobile.mp4"
            tour/playwright-config "playwright.mobile.config.js"
            tour/toc-band-script nil
            tour/film-title "移动端导览"
            tour/base-url (demo-base)
            tour/record-env {"MOBILE_BASE_URL" (demo-base)}
            narrate/out-dir demo-out-dir
            narrate/video-name "mobile.mp4"
            narrate/card-luma 40]
    (f)))

(defn video!
  "bb video:mobile [选项] —— 重编网页版、录四段分镜、合成 target/mobile-tour/mobile.mp4 并配旁白。
   --compose-only 不录制也不重编,用已有分镜视频重新合成 + 配音;
   --no-narrate 只出无声成片;其余参数(--provider say --voice 白桦 --force …)转给旁白任务。"
  [args]
  (let [skip #{"--compose-only" "--no-narrate"}]
    (with-demo
      (fn []
        (if (some #{"--compose-only"} args)
          (tour/video! ["--compose-only"])
          (do (web!)
              (record! args)))
        (when-not (some #{"--no-narrate"} args)
          (narrate/narrate! (remove skip args)))))))
