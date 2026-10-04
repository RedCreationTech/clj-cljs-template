(ns tasks.narrate
  "给出成的导览视频加旁白音轨:把 storyboard/NN.json 里的台词逐句合成语音,
   按每句的 at 换算到整片时间轴上统一排句,再混进视频。at 是相对分镜卡(深色标题页)的
   毫秒数,卡片前面的登录/导航空白靠读画面亮度现场量出来,所以音频能对上烧录字幕。
   排句是全片一次完成的,所以一句语音可以自然跨过章节边界,不会被分镜切掉。每句先剪掉
   首尾静音,再按「下一条字幕出现之前说完」排;装不下就提速追(最多 1.3 倍),两句之间
   始终留出空隙,不会连着念。
   视频流直接 copy,所以画面、左侧目录带与内嵌章节都不受影响;任务可重复执行。

   提供方:
   - mimo(默认)—— 小米 MiMo TTS,POST /v1/chat/completions,密钥取自 MIMO_API_KEY
     或 target/tour/mimo.key(target/ 已在 .gitignore 里,不要把密钥提交进仓库);
   - say —— macOS 自带 `say` 的离线兜底,没网络或没额度也能出一版能听的旁白。

   合成结果缓存在 target/tour/narration/,只有改了台词才需要重新合成那几句。
   用法:bb video:narrate [--provider mimo|say] [--voice 名称] [--model 名称]
            [--rate 语速,只影响 say] [--parallel N] [--force] [--only 分镜号]
   MiMo 音色:冰糖(默认)/ 茉莉 / 苏打 / 白桦 / Mia / Chloe / Milo / Dean。
   macOS 音色:say -v '?' 里 zh_CN 那批,默认 Tingting。"
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as p]
   [cheshire.core :as json]
   [clojure.string :as str]
   [tasks.subtitles :as subtitles]
   [tasks.util :as u]))

(def ^:dynamic out-dir "target/tour")
(def ^:dynamic video-name "tour.mp4")

(defn- audio-dir [] (str out-dir "/narration"))
(defn- video-path [] (str out-dir "/" video-name))
(defn- ffmeta-path [] (str out-dir "/chapters.ffmeta"))
(def api-url "https://api.xiaomimimo.com/v1/chat/completions")

(def sample-rate 48000)

(def style-prompt
  "中文技术解说,语速偏快,干脆利落,句子之间不留停顿。")

(defn- tool!
  [name]
  (or (u/exe name) (u/fail! "找不到 " name ",请先安装 ffmpeg(自带 ffprobe)")))

(defn- run
  "跑完一条命令返回修剪过的输出;非 0 退出即失败。"
  [cmd]
  (let [{:keys [exit out err]} (p/sh cmd {:out :string :err :string})]
    (when-not (zero? exit)
      (u/fail! "命令失败:" (str/join " " cmd) (str/trim (str out err))))
    (str/trim (or out ""))))

(def defaults
  {:provider "mimo" :voice "冰糖" :model "mimo-v2.5-tts" :rate 240 :parallel 4})

(def providers
  #{"mimo" "say"})

(def default-voice
  "换提供方时不要沿用另一家的音色名:say 遇到「冰糖」只会报一句听不懂的错。"
  {"mimo" "冰糖" "say" "Tingting"})

(defn- with-voice
  [cfg]
  (if (= "冰糖" (:voice cfg))
    (assoc cfg :voice (get default-voice (:provider cfg) (:voice cfg)))
    cfg))

(defn- int-flag
  [opt val]
  (when-not (and val (re-matches #"\d+" val))
    (u/fail! opt " 需要一个整数,收到:" (str val)))
  (parse-long val))

(defn- parse-args
  [args]
  (loop [xs (seq args) acc defaults]
    (if-let [a (first xs)]
      (let [val (second xs)]
        (when (and (not= "--force" a) (nil? val)) (u/fail! a " 缺少参数值"))
        (condp = a
          "--provider" (do (when-not (providers val)
                             (u/fail! "未知 --provider:" val "(支持 mimo | say)"))
                           (recur (drop 2 xs) (assoc acc :provider val)))
          "--voice" (recur (drop 2 xs) (assoc acc :voice val))
          "--model" (recur (drop 2 xs) (assoc acc :model val))
          "--rate" (recur (drop 2 xs) (assoc acc :rate (int-flag a val)))
          "--parallel" (recur (drop 2 xs) (assoc acc :parallel (max 1 (int-flag a val))))
          "--only" (recur (drop 2 xs) (assoc acc :only (int-flag a val)))
          "--force" (recur (next xs) (assoc acc :force true))
          (u/fail! "未知参数:" a "(用法见 bb tasks)")))
      acc)))

(defn- api-key
  "密钥只在本地:环境变量优先,其次 <out-dir>/mimo.key,最后 target/tour/mimo.key
   (移动端录屏复用同一份密钥,不必再抄一份到它的输出目录)。"
  []
  (or (some-> (System/getenv "MIMO_API_KEY") not-empty)
      (some-> (some #(when (fs/exists? %) (not-empty (str/trim (slurp %))))
                    (distinct [(fs/file out-dir "mimo.key") (fs/file "target/tour" "mimo.key")]))
              not-empty)
      (u/fail! "没有 MiMo 密钥:export MIMO_API_KEY=…,或把它写进 " out-dir "/mimo.key"
               "\n  (target/ 已被 gitignore;不要把密钥提交到仓库,建议用环境变量注入)"
               "\n  只想先出一版能听的:bb video:narrate --provider say")))

(defn- chapter-times
  "chapters.ffmeta → {分镜号 {:start 毫秒 :end 毫秒}}(相对成片时间轴)。"
  []
  (when-not (fs/exists? (ffmeta-path))
    (u/fail! "缺少 " (ffmeta-path) ",先跑录制任务"))
  (into {}
        (map (fn [[_ s e n]] [(parse-long n) {:start (parse-long s) :end (parse-long e)}]))
        (re-seq #"TIMEBASE=1/1000\s+START=(\d+)\s+END=(\d+)\s+title=(\d+)｜"
                (slurp (ffmeta-path)))))

(def ^:dynamic card-luma
  "分镜卡是整屏深色底,画面平均亮度会掉到这个阈值以下;台词的 at 就从卡片出现那一刻算起。
   舞台整体偏暗的录屏(移动端)要把这个值绑得更低,见 tasks.mobile/video!。"
  95)

(def ^:dynamic card-scan-seconds
  "分镜前导航/Flutter冷启动的搜索窗口；长启动视频可在任务中绑定更大的值。"
  16)

(defn- luma-series
  "从 from-ms 起 secs 秒内,每秒 4 帧的画面平均亮度。"
  [ffmpeg from-ms secs]
  (mapv #(Double/parseDouble (second %))
        (re-seq #"YAVG=([0-9.]+)"
                (run [ffmpeg "-hide_banner" "-nostats"
                      "-ss" (format "%.3f" (/ (max from-ms 0) 1000.0))
                      "-t" (str secs) "-i" (video-path)
                      "-vf" "fps=4,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-"
                      "-an" "-f" "null" "-"]))))

(defn- dark-run?
  "接下来 6 帧也都是深色底:连当前帧凑满 7 帧(约 1.75 秒),正好是一张分镜卡的停留时间。"
  [ys]
  (let [ys (take 6 ys)]
    (and (= 6 (count ys)) (every? #(< % card-luma) ys))))

(defn- card-start
  "第一个「亮过之后连续 7 帧都暗」的位置(毫秒,相对采样起点);找不到返回 nil。
   必须先看到亮帧:章节边界前一段可能停在深色的假终端画面上,否则空白会被算成 0,
   这一章的旁白会抢在字幕前面 1~4 秒开口。"
  [ys]
  (loop [i 0, ys (seq ys), seen-bright? false]
    (when-let [y (first ys)]
      (if (and seen-bright? (< y card-luma) (dark-run? (next ys)))
        (* i 250)
        (recur (inc i) (next ys) (or seen-bright? (<= card-luma y)))))))

(defn- chapter-leads!
  "分镜号 → 台词偏移毫秒:章节起点到分镜卡之间是登录/导航的空白,at 里不含它。"
  [ffmpeg times nos]
  (into {}
        (map (fn [n]
               (let [{:keys [start]} (times n)
                     lead (some-> (luma-series ffmpeg (- start 500) card-scan-seconds) card-start (- 500))]
                 (when (nil? lead)
                   (u/warn "分镜 " n " 没找到分镜卡,这一章的旁白可能比字幕早开口"))
                 [(long n) (max 0 (or lead 0))])))
        nos))

(defn- spoken-lines
  "一段分镜要念的句子;假终端里回放的命令($ bb ci)、以及代码卡逐行(带 :code 标记)
   只留在画面上,不念出来——逐字念 Clojure 不是解说。"
  [n]
  (let [path (format "%s/storyboard/%02d.json" out-dir n)]
    (when (fs/exists? path)
      (->> (:lines (json/parse-string (slurp path) true))
           (remove :code)
           (remove #(str/starts-with? (str (:text %)) "$ "))
           (filter #(seq (:text %)))
           (sort-by :at)
           (mapv (fn [l] {:text (:text l) :delay (long (:at l))}))))))

(defn- text-hash
  [text]
  (format "%032x"
          (java.math.BigInteger. 1
                                 (.digest (java.security.MessageDigest/getInstance "MD5")
                                          (.getBytes text "UTF-8")))))

(defn- clip-path
  "缓存文件名带上提供方、音色、模型/语速与(风格提示 + 台词)的哈希,换任一项都会重合成。"
  [{:keys [provider voice model rate]} text]
  (let [tag (if (= "say" provider) (str "r" rate) (str/replace model #"[^0-9A-Za-z._-]" "-"))]
    (format "%s/%s-%s-%s-%s.wav" (audio-dir) provider (text-hash (str style-prompt "::" text))
            (str/replace voice #"[^0-9A-Za-z_-]" "") tag)))

(defn- mimo-post
  "一次合成请求:失败(网络/超时/非 200)直接给出可读的退出信息。"
  [{:keys [key voice model]} text]
  (try
    (http/post api-url
               {:headers {"authorization" (str "Bearer " key)
                          "content-type" "application/json"}
                :body (json/generate-string
                       {:model model
                        :messages [{:role "user" :content style-prompt}
                                   {:role "assistant" :content text}]
                        :audio {:format "wav" :voice voice}})
                :as :string
                :throw false
                :timeout 180000})
    (catch Exception _
      (u/fail! "MiMo 请求失败，请检查服务地址、网络与超时；响应和请求密钥不写入日志"))))

(defn- write-bytes!
  [path bytes]
  (with-open [os (java.io.FileOutputStream. path)] (.write os bytes))
  path)

(defn- mimo-synth!
  [cfg text path]
  (let [resp (mimo-post cfg text)
        status (:status resp)
        body (str (:body resp))]
    (if-not (= 200 status)
      (u/fail! "MiMo TTS 返回 HTTP " status
               (if (= 402 status)
                 (str "\n  402 = 账号余额不足,充值后重跑即可(已合成的句子都在缓存里,不会重复计费);"
                      "\n  想先出一版能听的旁白:bb video:narrate --provider say")
                 ""))
      (if-let [b64 (get-in (try (json/parse-string body true) (catch Exception _ nil))
                           [:choices 0 :message :audio :data])]
        (write-bytes! path (.decode (java.util.Base64/getDecoder) b64))
        (u/fail! "MiMo 响应中没有有效音频数据；原始响应不写入日志")))))

(defn- say-synth!
  [{:keys [voice rate]} text path]
  (let [aiff (str path ".aiff")
        say (or (u/exe "say") (u/fail! "--provider say 需要 macOS 自带的 say 命令"))]
    (run [say "-v" voice "-r" (str rate) "-o" aiff text])
    (run [(tool! "ffmpeg") "-v" "error" "-y" "-i" aiff
          "-ar" (str sample-rate) "-ac" "1" path])
    (fs/delete-if-exists aiff)))

(defn- synth!
  [cfg text]
  (let [path (clip-path cfg text)]
    (cond
      (= "say" (:provider cfg)) (say-synth! cfg text path)
      (= "mimo" (:provider cfg)) (mimo-synth! cfg text path)
      :else (u/fail! "未知 --provider:" (:provider cfg) "(支持 mimo | say)"))
    path))

(defn- cached?
  [cfg path]
  (and (not (:force cfg)) (fs/exists? path) (< 1000 (fs/size path))))

(defn- run-parallel!
  "最多 n 路并发跑 f,全部完成才返回结果(按原顺序);任一句失败即抛出。"
  [f items n]
  (when (seq items)
    (let [size (max 1 (int (Math/ceil (/ (count items) (double n)))))]
      (mapcat deref (map (fn [group] (future (mapv f group))) (partition-all size items))))))

(def trim-tag
  "剪静音参数的版本号,换参数会得到新文件名,不会用到旧缓存。"
  "t45")

(defn- trimmed-path
  [path]
  (str (audio-dir) "/trimmed/" trim-tag "-" (fs/file-name path)))

(defn- trim!
  "剪掉一句语音首尾的静音:MiMo 每句头部约 0.15 秒、尾部约 0.35 秒是空白,
   273 句攒起来就是两分多钟,不剪掉旁白会一路落后画面。反向再剪一次等于剪尾部。"
  [src tgt]
  (run [(tool! "ffmpeg") "-v" "error" "-y" "-i" src
        "-af" (str "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.02,"
                   "areverse,"
                   "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,"
                   "areverse")
        "-ar" (str sample-rate) "-ac" "1" "-c:a" "pcm_s16le" tgt]))

(def max-tempo
  "语音比字幕停留长时,最多提速多少倍(再长就舍弃那句)。"
  1.3)

(def gap-ms
  "两句之间至少留多少毫秒换气的空隙。"
  200)

(defn- tempo-for
  "可用时长 = 下一条字幕的时刻 - 本句实际开口时刻 - 空隙。落后时它会变成 0 甚至负数,
   那就直接顶到最快速度去追,而不是回落到 1.0(那样只会越拖越远)。"
  [ms avail]
  (cond
    (<= ms avail) 1.0
    (<= avail 0.0) max-tempo
    :else (double (min max-tempo (/ ms avail)))))

(defn- schedule
  "把每句排到整片时间轴上:默认跟字幕同时开口,放不下就提速,提速后仍然撞上成片末尾则舍弃。
   时间轴以 window 起点为 0(旁白轨就从那里开始)。
   返回 [排好序的句 溢出毫秒 被舍弃的台词]。"
  [{:keys [start end]} clips]
  (let [limit (- end start)]
    (loop [cs (seq (map #(update % :delay - start) clips)) cursor 0.0 acc [] lost []]
      (if-not cs
        ;; cursor 含最后一句之后的空隙,溢出只算语音本身
        [acc (max 0.0 (- cursor gap-ms limit)) (reverse lost)]
        (let [{:keys [delay ms] :as c} (first cs)
              s (max (double delay) cursor)
              nxt (or (some-> (second cs) :delay double) limit)
              tempo (tempo-for ms (- nxt s gap-ms))
              eff (/ ms tempo)]
          (if (>= s (- limit 300.0))
            (recur (next cs) cursor acc (conj lost (select-keys c [:n :text])))
            (recur (next cs) (+ s eff gap-ms)
                   (conj acc (assoc c :start s :tempo tempo)) lost)))))))

(defn- filter-chain
  "单个输入的处理链:统一格式 → 必要时提速 → 推到排定的开口时刻。"
  [i {:keys [start tempo]}]
  (str (format "[%d:a]aformat=sample_fmts=s16:sample_rates=%d:channel_layouts=mono"
               (inc i) sample-rate)
       (when (> tempo 1.01) (format ",atempo=%.3f" tempo))
       (format ",adelay=delays=%d:all=1[p%d]" (long start) i)))

(defn- filter-graph
  "一路静音底 + N 句延迟人声 → amix。normalize=0 保持每句原始音量。"
  [clips]
  (str/join ";"
            (concat
             [(format "[0:a]aformat=sample_fmts=s16:sample_rates=%d:channel_layouts=mono[b]" sample-rate)]
             (map-indexed filter-chain clips)
             [(format "[b]%samix=inputs=%d:normalize=0:duration=first[out]"
                      (str/join (map (fn [i] (format "[p%d]" i)) (range (count clips))))
                      (inc (count clips)))])))

(defn- narration-track!
  "整片的旁白轨:一路静音底 + 每句按排定时刻 amix,时长等于成片时长。"
  [window clips]
  (let [dur (format "%.3f" (/ (- (:end window) (:start window)) 1000.0))
        out (str out-dir "/narration.wav")
        base [(tool! "ffmpeg") "-v" "error" "-y" "-f" "lavfi" "-t" dur
              "-i" (format "anullsrc=r=%d:cl=mono" sample-rate)]]
    (if (empty? clips)
      (run (vec (concat base ["-c:a" "pcm_s16le" out])))
      (run (vec (concat base
                        (mapcat (fn [{:keys [path]}] ["-i" path]) clips)
                        ["-filter_complex" (filter-graph clips)
                         "-map" "[out]" "-ar" (str sample-rate) "-ac" "1"
                         "-c:a" "pcm_s16le" out]))))
    out))

(defn- dur-ms
  [audio]
  (long (* 1000 (Double/parseDouble
                 (run [(tool! "ffprobe") "-v" "error"
                       "-show_entries" "format=duration"
                       "-of" "default=nk=1:nw=1" audio])))))

(defn- film-window
  "章节表 → 整片时间轴(旁白按整片排句,不受分镜边界截断)。"
  [times]
  {:start (apply min (map :start (vals times)))
   :end (apply max (map :end (vals times)))})

(defn- absolute-delays
  "台词的 at 相对分镜卡,加上「章节起点 + 卡片之前的空白」才是在成片里的时刻。"
  [times leads clips]
  (mapv (fn [c]
          (update c :delay + (get-in times [(:n c) :start] 0) (get leads (:n c) 0)))
        clips))

(defn- report!
  "排句结果:只有真的装不下才提示,并列出被舍弃的台词。"
  [sped overflow lost]
  (u/info "排句:提速 " sped " 句,舍弃 " (count lost) " 句")
  (when (or (> (/ overflow 1000.0) 0.4) (seq lost))
    (u/warn "最后一句的语音比画面长 " (format "%.1f" (/ overflow 1000.0)) " 秒(想全念完就缩短台词)"))
  (doseq [{:keys [n text]} (take 10 lost)] (u/warn "  分镜 " n " 没念: " text)))

(defn- mux!
  "旁白混进成片:视频流 copy,章节元数据从 chapters.ffmeta 重新带上。
   音轨一定落成双声道:QuickTime 对单声道 AAC 会「画面照播、一声不出」;
   但不要用裸 -ac 2 做单→双(实测偷偷掉了约 3 dB),用 pan 明确复制同一声道。"
  [narration]
  (let [tmp (str (video-path) ".mux.mp4")]
    (run [(tool! "ffmpeg") "-v" "error" "-y"
          "-i" (video-path) "-i" narration "-i" (ffmeta-path)
          "-map" "0:v" "-map" "1:a" "-c:v" "copy"
          "-af" "pan=stereo|c0=c0|c1=c0" "-ac" "2" "-c:a" "aac" "-b:a" "192k"
          "-map_chapters" "2" "-movflags" "+faststart" tmp])
    (fs/move (fs/file tmp) (fs/file (video-path)) {:replace-existing true})))

(defn- prepare-clips!
  "把每句映射到缓存文件,缺的并发合成,剪掉首尾静音后量出每句的实际时长。"
  [cfg nos]
  (let [jobs (mapcat (fn [n]
                       (let [ls (spoken-lines n)]
                         (when-not (seq ls) (u/warn "分镜 " n " 读不到台词,整段不会配音"))
                         (map #(assoc % :n n :path (clip-path cfg (:text %))) ls)))
                     nos)
        pending (distinct (map :text (remove #(cached? cfg (:path %)) jobs)))]
    (u/info (count jobs) "句台词,需合成 " (count pending) " 句"
            (when (and (seq pending) (= "mimo" (:provider cfg)))
              "(按字数计费)")
            (when (and (:force cfg) (= "mimo" (:provider cfg))) "(--force:缓存的也重灌)")
            "…")
    (run-parallel! #(synth! cfg %) pending (:parallel cfg))
    (fs/create-dirs (fs/file (audio-dir) "trimmed"))
    (u/info "剪首尾静音、量时长…")
    (run-parallel!
     (fn [{:keys [path] :as c}]
       (when (<= (fs/size path) 1000) (u/fail! "合成结果为空:" path))
       (let [tgt (trimmed-path path)]
         ;; --force 会重灌同名缓存,剪好的副本比源文件旧就要重剪
         (when (or (:force cfg) (not (fs/exists? tgt))
                   (pos? (compare (fs/last-modified-time path) (fs/last-modified-time tgt))))
           (trim! path tgt))
         (if (< (fs/size tgt) 500)
           (assoc c :ms (dur-ms path))
           (assoc c :path tgt :ms (dur-ms tgt)))))
     jobs (:parallel cfg))))

(defn narrate!
  "生成旁白并写进成片(画面不重编码,可反复执行)。"
  [args]
  (when-not (fs/exists? (video-path)) (u/fail! "找不到 " (video-path) ",先跑录制任务"))
  (fs/create-dirs (fs/file (audio-dir)))
  (let [ffmpeg (tool! "ffmpeg")
        cfg (-> (parse-args args) with-voice)
        cfg (if (= "mimo" (:provider cfg)) (assoc cfg :key (api-key)) cfg)
        times (chapter-times)
        nos (if-let [only (:only cfg)]
              (do (when-not (contains? times only)
                    (u/fail! "成片里没有分镜 " only "(共 " (count times) " 段)"))
                  [only])
              (sort (keys times)))]
    (when (:only cfg)
      (prepare-clips! cfg nos)
      (u/info "只合成第 " (:only cfg) " 段的语音缓存,未改动视频")
      (System/exit 0))
    (let [leads (chapter-leads! ffmpeg times nos)
          _ (u/info "分镜卡前的空白(秒):"
                    (str/join " " (map (fn [[n l]] (format "%d=%.2f" n (/ l 1000.0))) (sort-by key leads))))
          clips (->> (prepare-clips! cfg nos)
                     (sort-by (juxt :n :delay))
                     (absolute-delays times leads))
          window (film-window times)
          [cs overflow lost] (schedule window clips)
          narration (narration-track! window cs)]
      (report! (count (filter #(> (:tempo %) 1.01) cs)) overflow lost)
      (mux! narration)
      (spit (str out-dir "/caption-timing.json")
            (json/generate-string
             {:version 1 :video video-name
              :chapters (mapv (fn [n] (assoc (times n) :n n :lead (get leads n))) nos)}))
      (subtitles/export! out-dir video-name)
      (u/info "旁白已写入 " (video-path) ":" (count cs) "句,"
              (:provider cfg) " / " (:voice cfg) ",时长 "
              (format "%.1f 秒" (/ (dur-ms narration) 1000.0))))))
