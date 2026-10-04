(ns tasks.subtitles
  "按真实章节与对齐偏移导出可编辑SRT，不从控制台日志猜测时间。"
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn timestamp [milliseconds]
  (let [ms (max 0 (long milliseconds))]
    (format "%02d:%02d:%02d,%03d" (quot ms 3600000)
            (mod (quot ms 60000) 60) (mod (quot ms 1000) 60) (mod ms 1000))))

(defn chapter-captions [{:keys [start end lead]} lines]
  (let [lines (filterv #(and (not (:code %)) (not (str/blank? (:text %)))) lines)]
    (keep-indexed
     (fn [index line]
       (let [begin (+ start lead (:at line))
             finish (min end (+ begin (:for line))
                         (+ start lead (:at (get lines (inc index)) (+ (:at line) (:for line)))))]
         (when (< begin finish)
           {:start begin :end finish :text (:text line)}))) lines)))

(defn srt [captions]
  (str/join "\n" (map-indexed
                  (fn [index {:keys [start end text]}]
                    (str (inc index) "\n" (timestamp start) " --> " (timestamp end) "\n"
                         (str/replace text #"\r\n?" "\n") "\n")) captions)))

(defn export! [directory video-name]
  (let [path (str directory "/caption-timing.json")]
    (when-not (fs/exists? path)
      (throw (ex-info "缺少字幕对齐记录，请先完成旁白合成任务" {:path path})))
    (let [timing (json/parse-string (slurp path) true)
          _ (doseq [source (cons (str directory "/chapters.ffmeta")
                                 (map #(format "%s/storyboard/%02d.json" directory (:n %)) (:chapters timing)))]
              (when (> (.toMillis (fs/last-modified-time source)) (.toMillis (fs/last-modified-time path)))
                (throw (ex-info "章节或台词已修改，请重新合成旁白以更新字幕对齐" {}))))
          captions (mapcat (fn [chapter]
                             (let [board (json/parse-string
                                          (slurp (format "%s/storyboard/%02d.json" directory (:n chapter))) true)]
                               (chapter-captions chapter (:lines board)))) (:chapters timing))
          output (str directory "/" (str/replace video-name #"\.[^.]+$" "") ".zh-CN.srt")]
      (when-not (and (= 1 (:version timing)) (= video-name (:video timing)) (seq captions))
        (throw (ex-info "字幕对齐记录与视频不匹配或没有有效台词" {})))
      (spit output (srt captions))
      (println "✔ 已导出" (count captions) "条中文字幕：" output))))

(defn export-args! [args]
  (case (count args)
    0 (export! "target/tour" "tour.mp4")
    2 (apply export! args)
    (throw (ex-info "用法：bb video:subtitles [输出目录 视频文件名]" {}))))

(defn check! []
  (let [chapter {:n 12 :start 60000 :end 62000 :lead 500}
        captions (vec (chapter-captions chapter [{:text "第一句" :at 0 :for 900}
                                                 {:text "$ bb ci" :at 100 :for 800 :code true}
                                                 {:text "第二句" :at 800 :for 1200}
                                                 {:text "越界句" :at 2000 :for 1000}]))]
    (assert (= [{:start 60500 :end 61300 :text "第一句"}
                {:start 61300 :end 62000 :text "第二句"}] captions))
    (assert (= "01:01:01,001" (timestamp 3661001)))
    (assert (= "1\n00:01:00,500 --> 00:01:01,300\n第一句\n\n2\n00:01:01,300 --> 00:01:02,000\n第二句\n"
               (srt captions)))
    (println "✔ 字幕跨章节、代码过滤、越界和时间格式回归通过")))
