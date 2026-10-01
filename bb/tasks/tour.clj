(ns tasks.tour
  "功能导览录像:跑 tests/e2e/tour 的全部分镜(编号 01~NN,一个测试一段),
   按分镜顺序拼成 target/tour/tour.mp4,
   并生成内嵌章节、时间轴 chapters.txt 与分镜脚本 storyboard.md。

   台词由 tests/e2e/tour/tour-helper.js 直接画在页面上,字幕天然录在视频里,不需要后期压制。
   左侧常驻章节目录由 tests/e2e/tour/toc-band.js 渲染成图片,合成阶段贴在画面左边。
   环境变量:TOUR_SPEED(默认 1,调小只用于改脚本)、TOUR_WIDTH / TOUR_HEIGHT(默认 1440x900)、
   TOUR_TOC_WIDTH(目录条宽度,默认 300)。"
  (:require
   [babashka.fs :as fs]
   [cheshire.core :as json]
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [tasks.util :as u]
   [tasks.vendor :as vendor]))

(def out-dir "target/tour")

(def app-width (u/env-int "TOUR_WIDTH" 1440))
(def app-height (u/env-int "TOUR_HEIGHT" 900))
(def toc-width (u/env-int "TOUR_TOC_WIDTH" 300))

(defn- tool!
  [name]
  (or (u/exe name)
      (u/fail! "找不到 " name ",请先安装 ffmpeg(自带 ffprobe)")))

(defn- run
  "跑完一条命令,返回修剪过的标准输出;非 0 退出即失败。"
  [cmd]
  (let [{:keys [exit out]} (p/sh cmd {:out :string})]
    (when-not (zero? exit)
      (u/fail! "命令失败:" (str/join " " cmd) (str/trim (or out ""))))
    (str/trim out)))

(defn- ffescape
  "concat demuxer 路径里的单引号转义。"
  [s]
  (str/replace s "'" "'\\''"))

(defn- storyboard
  "分镜卡与台词:tour-helper.js 每段结束时写 target/tour/storyboard/NN.json。"
  [no]
  (let [path (format "%s/storyboard/%02d.json" out-dir no)]
    (when (fs/exists? path)
      (json/parse-string (slurp path) true))))

(defn- chapter-no
  "从 Playwright 的输出目录名取分镜编号,例如 05-audit-09｜... → 9。"
  [dir]
  (some-> (re-find #"(\d+)｜" (fs/file-name dir)) second parse-long))

(defn- duration-ms
  [video]
  (long (* 1000 (Double/parseDouble
                 (run [(tool! "ffprobe") "-v" "error"
                       "-show_entries" "format=duration"
                       "-of" "default=nk=1:nw=1" video])))))

(defn- recorded-videos
  "Playwright 输出目录里的分镜视频:[[分镜编号 视频路径] ...],按编号升序。"
  []
  (->> (file-seq (io/file out-dir "results"))
       (filter #(= "video.webm" (fs/file-name %)))
       (map (fn [f] [(chapter-no (.getParentFile f)) (str (fs/absolutize f))]))
       (filter first)
       (sort-by first)
       (vec)))

(defn- segments
  "带起点毫秒的分镜片段 [{:no :video :start :duration :storyboard} ...];缺号直接失败。"
  []
  (when-not (fs/exists? (io/file out-dir "results"))
    (u/fail! "没有分镜视频目录:" out-dir "/results"))
  (let [videos (recorded-videos)]
    (when-not (= (map first videos) (range 1 (inc (count videos))))
      (u/fail! "分镜不完整,只录到编号:" (str (map first videos))))
    (loop [vs videos, start 0, acc []]
      (if-let [[no video] (first vs)]
        (let [d (duration-ms video)]
          (recur (rest vs) (+ start d)
                 (conj acc {:no no, :video video, :start start, :duration d,
                            :storyboard (storyboard no)})))
        acc))))

(defn- clock
  "毫秒 → 成片里的 HH:MM:SS。"
  [ms]
  (let [s (long (/ ms 1000))]
    (format "%02d:%02d:%02d" (quot s 3600) (mod (quot s 60) 60) (mod s 60))))

(defn- title-of
  [{:keys [no storyboard]}]
  (if-let [t (:title storyboard)]
    (format "%02d｜%s" no t)
    (format "分镜 %02d" no)))

(defn- write-inputs!
  "concat 清单 + 内嵌章节的 ffmetadata + 纯文本时间轴。"
  [segments]
  (spit (str out-dir "/concat.txt")
        (str (str/join "\n" (map #(format "file '%s'" (ffescape (:video %))) segments)) "\n"))
  (spit (str out-dir "/chapters.ffmeta")
        (str ";FFMETADATA1\n"
             (str/join "\n"
                       (for [{:keys [start duration] :as s} segments]
                         (format "[CHAPTER]\nTIMEBASE=1/1000\nSTART=%d\nEND=%d\ntitle=%s"
                                 start (+ start duration) (title-of s))))
             "\n"))
  (spit (str out-dir "/chapters.txt")
        (str (str/join "\n" (for [{:keys [start] :as s} segments]
                              (str (clock start) "  " (title-of s))))
             "\n")))

(defn- segment-script
  "一个分镜的 markdown 段落:标题、要点与带绝对时间点的台词。"
  [{:keys [start duration storyboard] :as s}]
  (str/join "\n"
            (remove nil?
                    [(format "## %s — %s" (title-of s) (clock start))
                     (when-let [sub (:subtitle storyboard)] (str "> " sub))
                     (str "时长 " (clock duration) ",起点 " (clock start) "。")
                     (when (seq (:points storyboard))
                       (str "要点:\n" (str/join "\n" (map #(str "- " %) (:points storyboard)))))
                     (when (seq (:lines storyboard))
                       (str "台词:\n"
                            (str/join "\n"
                                      (for [l (:lines storyboard)]
                                        (format "- `%s` %s"
                                                (clock (+ start (long (:at l 0)))) (:text l ""))))))])))

(defn- write-storyboard-md!
  [segments]
  (spit (str out-dir "/storyboard.md")
        (str (str/join "\n\n"
                       (concat [(str "# 功能导览 · 分镜脚本\n\n"
                                     "成片 `" out-dir "/tour.mp4`,共 " (count segments)
                                     " 段,总时长 " (clock (+ (:start (last segments))
                                                         (:duration (last segments))))
                                     "。\n\n台词由 `tour-helper.js` 画在页面上,时间点为该句在成片里的出现时刻。")]
                               (map segment-script segments)))
             "\n")))

(defn- concat!
  "按 concat.txt 顺序重编码为不带目录条的原片,并把章节写进容器。"
  []
  (run [(tool! "ffmpeg") "-v" "error" "-y"
        "-f" "concat" "-safe" "0" "-i" (str out-dir "/concat.txt")
        "-i" (str out-dir "/chapters.ffmeta")
        "-map" "0:v" "-map_metadata" "1"
        "-c:v" "libx264" "-crf" "23" "-preset" "medium" "-pix_fmt" "yuv420p"
        "-movflags" "+faststart"
        (str out-dir "/tour-raw.mp4")]))

;; ─── 左侧常驻章节目录 ─────────────────────────────────────────────

(defn- secs
  [ms]
  (format "%.3f" (/ (double ms) 1000)))

(defn- total-secs
  [segments]
  (secs (+ (:start (last segments)) (:duration (last segments)))))

(defn- filter-chain
  "应用画面右移让出目录条,再按分镜时间窗逐段贴对应的目录图,末尾画一条总进度线。"
  [segments]
  (let [w (+ toc-width app-width)
        last-idx (dec (count segments))]
    (str
     (format "[0:v]pad=%d:%d:%d:0:color=black[b];" w app-height toc-width)
     (str/join
      ";"
      (map-indexed
       (fn [i {:keys [start duration]}]
         (format "[%s][%d:v]overlay=0:0:enable='between(t,%s,%s)'[%s]"
                 (if (zero? i) "b" (str "c" (dec i)))
                 (inc i) (secs start) (secs (+ start duration)) (str "c" i)))
       segments))
     (format ";[c%d]drawbox=x=%d:y=0:w='%d*t/%s':h=5:color=0x409eff:t=fill[v]"
             last-idx toc-width app-width (total-secs segments)))))

(defn- toc-band!
  "用 Chromium 把每段目录渲染成 target/tour/toc/NN.png(高亮当前分镜)。"
  []
  (u/exec! [(or (u/exe "node") (u/fail! "找不到 node")) "tests/e2e/tour/toc-band.js"]))

(defn- composite!
  "tour-raw.mp4 + 每段一张目录图 + 章节元数据 → tour.mp4(成片)。"
  [segments]
  (let [meta-idx (inc (count segments))
        images (map #(format "%s/toc/%02d.png" out-dir (:no %)) segments)]
    (run (vec (concat [(tool! "ffmpeg") "-v" "error" "-y"
                       "-i" (str out-dir "/tour-raw.mp4")]
                      (mapcat #(list "-loop" "1" "-t" (total-secs segments) "-i" %) images)
                      ["-i" (str out-dir "/chapters.ffmeta")
                       "-filter_complex" (filter-chain segments)
                       "-map" "[v]" "-map_metadata" (str meta-idx)
                       "-c:v" "libx264" "-crf" "23" "-preset" "medium" "-pix_fmt" "yuv420p"
                       "-movflags" "+faststart"
                       (str out-dir "/tour.mp4")])))))

(defn video!
  "录制 + 合成一条龙:bb video:tour [--compose-only] [额外的 playwright 参数]。
   --compose-only 跳过录制,用已有的分镜视频重新合成(调目录条/字幕样式时用)。"
  [args]
  (let [base (or (System/getenv "BASE_URL") "http://localhost:3000")
        compose-only? (some #{"--compose-only"} args)
        playwright-args (remove #{"--compose-only"} args)]
    (tool! "ffmpeg")
    (if compose-only?
      (u/info "跳过录制,直接用 " out-dir "/results 里已有的分镜视频重新合成")
      (do (when-not (u/http-ok? (str base "/api/health"))
            (u/fail! "后端未运行:先 bb dev(或 bb backend),再跑 bb video:tour"))
          (vendor/ensure-npm-deps!)
          (vendor/ensure-browsers!)
          (u/exec! (into [(u/npx-cmd) "playwright" "test" "--config" "playwright.tour.config.js"]
                         playwright-args))))
    (let [segs (segments)]
      (write-inputs! segs)
      (write-storyboard-md! segs)
      (concat!)
      (toc-band!)
      (composite! segs)
      (u/info "成片 " out-dir "/tour.mp4(" (total-secs segs) " 秒)," (count segs)
              " 段,左侧常驻目录;分镜脚本 storyboard.md,时间轴 chapters.txt"))))
