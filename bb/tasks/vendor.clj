(ns tasks.vendor
  "第三方 JS 依赖的构建期补丁。npm 重装后需要重新打,所以由前端构建任务自动调用。"
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [tasks.util :as u]))

;; Closure Compiler v20250407 之后把 ES class 静态方法里的 `super.m(a)` 编译成 `Parent.m(a)`,
;; 丢掉了 `this`。Quill 2 / Parchment 3 的 Blot 全靠 static super 取子类的 tagName,
;; 于是静态方法拿到的永远是父类的标签:轻则 link/image 变成 <span>,重则
;; 抛 "[Parchment] Blot definition missing tagName",公告编辑弹窗整个渲染不出来(dev 与 release 都中招)。
;; 这里把 super 改写成显式的 `Parent.m.call(this, ...)`,语义与 ES 规范一致,编译器不会再动它。
;; 上游修复后删掉本补丁即可。
(def ^:private quill-static-super
  [{:path "node_modules/parchment/dist/parchment.js"
    :from "return super.create(value);"
    :to "return ParentBlot$1.create.call(this, value);"
    :expect 2}
   {:path "node_modules/quill/formats/bold.js"
    :from "return super.create();"
    :to "return Inline.create.call(this);"
    :expect 1}
   {:path "node_modules/quill/formats/code.js"
    :from "const domNode = super.create(value);"
    :to "const domNode = Container.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/formats/formula.js"
    :from "const node = super.create(value);"
    :to "const node = Embed.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/formats/image.js"
    :from "const node = super.create(value);"
    :to "const node = EmbedBlot.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/formats/link.js"
    :from "const node = super.create(value);"
    :to "const node = Inline.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/formats/list.js"
    :from "const node = super.create();"
    :to "const node = Block.create.call(this);"
    :expect 1}
   {:path "node_modules/quill/formats/script.js"
    :from "return super.create(value);"
    :to "return Inline.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/formats/table.js"
    :from "const node = super.create();"
    :to "const node = Block.create.call(this);"
    :expect 1}
   {:path "node_modules/quill/formats/video.js"
    :from "const node = super.create(value);"
    :to "const node = BlockEmbed.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/modules/syntax.js"
    :from "const domNode = super.create(value);"
    :to "const domNode = CodeBlock.create.call(this, value);"
    :expect 1}
   {:path "node_modules/quill/modules/syntax.js"
    :from "return super.formats(node, scroll);"
    :to "return Inline.formats.call(this, node, scroll);"
    :expect 1}])

(defn- occurrences
  "sub 在 s 中出现的次数(字面匹配,不算正则)。"
  [s sub]
  (loop [i 0 n 0]
    (let [at (.indexOf ^String s sub i)]
      (if (neg? at) n (recur (inc at) (inc n))))))

(defn- apply-patch!
  "打一处补丁:已打过就跳过,数量对不上说明依赖换了版本,必须重新核对补丁表。"
  [{:keys [path from to expect]}]
  (let [src (slurp path)
        patched (occurrences src to)]
    (cond
      (>= patched expect) (when (> patched expect)
                            (u/warn path " 补丁数量异常:" patched "/" expect))
      (= (occurrences src from) expect) (spit path (str/replace src from to))
      :else (u/fail! path " 里没找到要替换的 static super 片段(期望 " expect " 处,实际 "
                     (occurrences src from) " 处),"
                     "\n  依赖可能升级了,请重新核对 bb/tasks/vendor.clj 的补丁表,"
                     "\n  或确认 Closure Compiler 是否已修复该问题后删除本补丁。"))))

(defn patch-quill!
  "给 Quill / Parchment 打 static super 补丁;前端编译前调用。"
  []
  (when (fs/exists? "node_modules")
    (doseq [spec quill-static-super] (apply-patch! spec))))

(defn ensure-npm-deps!
  "node_modules 不存在时先 npm install,然后补齐依赖补丁。"
  []
  (when-not (fs/exists? "node_modules")
    (u/info "首次运行:npm install")
    (u/exec! [(or (u/exe "npm") (u/fail! "找不到 npm")) "install"]))
  (patch-quill!))
