(ns tasks.util
  "bb 任务共用的小工具:项目元信息、跨平台进程启动、端口/HTTP 就绪检查。"
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as p]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn project
  "读取 kit.edn:{:ns-name \"com.ruoyi\" :sanitized \"com/ruoyi\" :name \"rouyi\"}。
   改名脚本会同步修改它,所有任务都从这里取命名空间与路径,不写死。"
  []
  (edn/read-string (slurp "kit.edn")))

(defn info [& xs] (println (apply str "▶ " xs)))

(defn warn [& xs] (binding [*out* *err*] (println (apply str "⚠ " xs))))

(defn fail!
  "打印错误并以非 0 退出(bb 任务的失败约定)。"
  [& xs]
  (binding [*out* *err*] (println (apply str "✖ " xs)))
  (System/exit 1))

(defn exe
  "按名字找可执行文件(Windows 下会补 .cmd/.exe);找不到返回 nil。"
  [name]
  (some-> (fs/which name) str))

(defn clojure-cmd
  "优先用本机安装的 Clojure CLI;没有时退回 bb 内置的 deps.clj(`bb clojure`),保证 Windows 也能跑。"
  []
  (if-let [c (exe "clojure")]
    [c]
    [(or (exe "bb") "bb") "clojure"]))

(def strict-cljs
  "shadow-cljs --config-merge 参数:把编译 warning 当作错误。"
  "{:compiler-options {:warnings-as-errors true}}")

(defn npx-cmd []
  (or (exe "npx") (fail! "找不到 npx,请先安装 Node.js 18+")))

(defn env-int [k default]
  (or (some-> (System/getenv k) parse-long) default))

(defn port-free?
  "尝试在本机绑定端口;能绑定说明端口空闲。"
  [port]
  (try (with-open [_ (java.net.ServerSocket. (int port))] true)
       (catch Exception _ false)))

(defn require-free-ports!
  "端口被占用时给出可操作的提示并退出。"
  [ports]
  (let [busy (remove (comp port-free? second) ports)]
    (when (seq busy)
      (fail! "以下端口已被占用:"
             (str/join ", " (map (fn [[label port]] (str label " " port)) busy))
             "\n  查占用进程:macOS/Linux `lsof -i :<端口>`,Windows `netstat -ano | findstr :<端口>`"
             "\n  或换端口:PORT=3200 NREPL_PORT=7200 bb dev"))))

(defn http-ok?
  [url]
  (try (= 200 (:status (http/get url {:throw false :timeout 2000})))
       (catch Exception _ false)))

(defn wait-http!
  "轮询 url 直到返回 200;alive? 返回 false(进程已退出)或超时则失败。"
  [url timeout-s alive?]
  (loop [n 0]
    (cond
      (http-ok? url) true
      (not (alive?)) (fail! "进程在就绪前退出,见上方日志")
      (>= n timeout-s) (fail! "等待 " url " 超时(" timeout-s "s)")
      :else (do (Thread/sleep 1000) (recur (inc n))))))

(defn pump!
  "把子进程输出逐行加前缀打印;on-line 可对每行做检测(如识别编译完成)。"
  [prefix stream on-line]
  (future
    (with-open [r (io/reader stream)]
      (doseq [line (line-seq r)]
        (println (str prefix line))
        (when on-line (on-line line))))))

(defn start!
  "后台启动进程(stderr 合并到 stdout,输出带前缀),返回 babashka.process 对象。"
  ([prefix cmd] (start! prefix cmd {} nil))
  ([prefix cmd opts on-line]
   (let [proc (apply p/process (merge {:out :stream :err :out :shutdown p/destroy-tree} opts) cmd)]
     (pump! prefix (:out proc) on-line)
     proc)))

(defn exec!
  "前台运行并继承输入输出;失败时以子进程的退出码退出。"
  ([cmd] (exec! cmd {}))
  ([cmd opts]
   (let [{:keys [exit]} @(apply p/process (merge {:inherit true} opts) cmd)]
     (when-not (zero? exit)
       (System/exit exit)))))
