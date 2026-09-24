(ns com.ruoyi.web.controllers.captcha
  "验证码控制器 — 生成图片验证码。验证码存在 infra.kv(5 分钟有效、一次性),多实例共享。"
  (:require
   [com.ruoyi.infra.kv :as kv]
   [ring.util.response :as response])
  (:import
   [java.awt Color Font RenderingHints]
   [java.awt.image BufferedImage]
   [java.io ByteArrayOutputStream]
   [java.util Random]
   [javax.imageio ImageIO]))

(def ttl-ms
  "验证码有效期。"
  (* 5 60 1000))

(defn- store-key [uuid] (str "captcha:" uuid))

(defn store-code!
  "保存验证码(infra.kv,多实例共享)。"
  [uuid code]
  (kv/put! (store-key uuid) code ttl-ms))

(defn take-code!
  "取出并作废验证码:每个验证码只能校验一次,无论对错。"
  [uuid]
  (when (seq uuid) (kv/take! (store-key uuid))))

(def code-chars
  "验证码字符集(去掉易混的 I、O、0、1)。"
  "ABCDEFGHJKLMNPQRSTUVWXYZ23456789")

(defn- generate-code
  "生成随机验证码。"
  [length]
  (let [random (Random.)]
    (apply str (repeatedly length #(nth code-chars (.nextInt random (count code-chars)))))))

(defn- generate-color
  "生成随机颜色。"
  [min-val max-val]
  (let [random (Random.)
        r (+ min-val (.nextInt random (- max-val min-val)))
        g (+ min-val (.nextInt random (- max-val min-val)))
        b (+ min-val (.nextInt random (- max-val min-val)))]
    (Color. r g b)))

(defn draw-code!
  "按字体的实际宽度把字符均匀排进图片(左右各留 pad),字号随图片高度,纵向略微抖动。
   不写死坐标:服务器上没有 Arial 时 Java 会换字体,字宽会变。"
  [^java.awt.Graphics2D g code width height]
  (let [random (Random.)
        n (count code)
        pad 8
        slot (/ (- width (* 2 pad)) (double n))]
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
    (.setFont g (Font. Font/SANS_SERIF Font/BOLD (int (* height 0.62))))
    (let [fm (.getFontMetrics g)
          baseline (quot (+ height (- (.getAscent fm) (.getDescent fm))) 2)]
      (dotimes [i n]
        (let [ch (str (nth code i))
              x (+ pad (* i slot) (/ (- slot (.stringWidth fm ch)) 2))]
          (.setColor g (generate-color 50 180))
          (.drawString g ch (int x) (int (+ baseline (- (.nextInt random 7) 3)))))))))

(defn- draw-noise!
  "干扰线与干扰点。"
  [^java.awt.Graphics2D g width height]
  (let [random (Random.)]
    (dotimes [_ 6]
      (.setColor g (generate-color 100 200))
      (.drawLine g (.nextInt random width) (.nextInt random height)
                 (.nextInt random width) (.nextInt random height)))
    (dotimes [_ 30]
      (.setColor g (generate-color 150 230))
      (.drawOval g (.nextInt random width) (.nextInt random height) 2 2))))

(defn- create-captcha-image
  "创建验证码图片。"
  [code width height]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics image)]
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 width height)
    (draw-code! g code width height)
    (draw-noise! g width height)
    (.dispose g)
    image))

(defn captcha-image
  "生成验证码图片并返回。"
  [_ request]
  (let [code (generate-code 4)
        image (create-captcha-image code 150 50)
        baos (ByteArrayOutputStream.)]
    (ImageIO/write image "png" baos)
    (let [uuid (or (get-in request [:query-params "r"])
                   (str (java.util.UUID/randomUUID)))]
      (store-code! uuid code)
      ;; 返回图片和 UUID
      (-> (response/response (.toByteArray baos))
          (response/content-type "image/png")
          (response/header "Captcha-UUID" uuid)))))
