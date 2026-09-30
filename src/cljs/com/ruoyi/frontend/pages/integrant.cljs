(ns com.ruoyi.frontend.pages.integrant
  "Integrant config -> system 依赖可视化页面。"
  (:require
   ["@ant-design/icons" :refer [ReloadOutlined]]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.perm :as perm]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

;; ─── 数据 → Tree ─────────────────────────────────────────────────────

(defn- ->tree-nodes
  ([label value] (->tree-nodes "" label value))
  ([path label value]
   (let [ref? (and (map? value) (:__ig_ref value))
         node-path (str path "/" label)]
     (cond
       ref?
       [{:title (r/as-element
                 [:span {:style {:color "#1677ff" :fontWeight 500}}
                  (str "→ " (:key value))])
         :key (str node-path "-ref-" (:key value))}]

       (and (map? value) (seq value))
       [{:title (str label)
         :key node-path
         :children (mapcat (fn [[k v]]
                             (->tree-nodes node-path (str k) v))
                           (sort-by key value))}]

       (and (sequential? value) (seq value))
       [{:title (str label " [" (count value) "]")
         :key node-path
         :children (mapcat (fn [[i v]]
                             (->tree-nodes node-path (str "[" i "]") v))
                           (map-indexed vector value))}]

       :else
       [{:title (r/as-element
                 [:span
                  [:span {:style {:color "var(--app-text-secondary)"}} (str label " = ")]
                  (pr-str value)])
         :key (str node-path "-leaf")}]))))

(defn- collect-tree-keys [nodes]
  (mapcat (fn [n]
            (cons (:key n)
                  (collect-tree-keys (:children n))))
          nodes))

(defn- trace-key [k]
  (if (keyword? k)
    (subs (str k) 1)
    (str k)))

;; ─── 依赖图 ─────────────────────────────────────────────────────────

(defn- compute-depths [order deps]
  (let [depth-fn (fn rec [k]
                   (let [ds (get deps (keyword k))]
                     (if (seq ds)
                       (inc (apply max (map rec ds)))
                       0)))]
    (into {} (map (fn [k] [k (depth-fn k)]) order))))

(defn- dep-graph-edges [order dependencies node-pos]
  (for [k order
        d (get dependencies (keyword k))]
    (when-let [p1 (get node-pos d)]
      (when-let [p2 (get node-pos k)]
        ^{:key (str d "->" k)}
        [:line {:x1 (:x p1) :y1 (:y p1)
                :x2 (- (:x p2) 75) :y2 (:y p2)
                :stroke "var(--app-text-secondary)" :strokeWidth 1
                :markerEnd "url(#ig-arrow)"}]))))

(defn- dep-graph-fn-toggle [k active? on-toggle]
  [:g {:transform "translate(42,-8)"
       :style {:cursor "pointer"}
       :onClick (fn [e]
                  (.stopPropagation e)
                  (on-toggle k (not active?)))}
   [:rect {:x 0 :y 0 :width 28 :height 16 :rx 8 :ry 8
           :fill (if active? "#52c41a" "#d9d9d9")}]
   [:circle {:cx (if active? 20 8) :cy 8 :r 6 :fill "#fff"}]])

(defn- dep-graph-node [k pos selected trace-by-key system on-select on-toggle]
  (let [function? (= "function" (get-in system [(keyword k) :kind]))
        active? (boolean (get-in trace-by-key [k :active]))]
    [:g {:transform (str "translate(" (:x pos) "," (:y pos) ")")
         :style {:cursor "pointer"}
         :onClick #(on-select k)}
     [:rect {:x -75 :y -16 :width 150 :height 32
             :rx 4 :ry 4
             :fill (if (= k selected) "#1677ff" "var(--app-bg)")
             :stroke (if (= k selected) "#1677ff" "var(--app-border)")}]
     [:text {:x (if function? -18 0) :y 4 :textAnchor "middle"
             :fontSize 11
             :fill (if (= k selected) "#fff" "var(--app-text-primary)")}
      k]
     (when function?
       [dep-graph-fn-toggle k active? on-toggle])]))

(defn- dep-graph [data selected trace-by-key on-select on-toggle]
  (let [{:keys [order dependencies system]} data
        depths (compute-depths order dependencies)
        by-depth (group-by #(get depths %) order)
        layer-height (+ 50 (* 34 (apply max 1 (map count (vals by-depth)))))
        node-pos (into {}
                       (for [[d keys] by-depth
                             [i k] (map-indexed vector keys)]
                         [k {:x (+ 80 (* d 200))
                             :y (+ 40 (* i 34) (/ (- layer-height (* (count keys) 34)) 2))}]))
        width (+ 160 (* (apply max 0 (vals depths)) 200))
        height layer-height]
    [:svg {:width width :height height
           :className "integrant-dep-graph"
           :style {:border "1px solid var(--app-border-light)" :background "var(--app-fill-light)" :borderRadius 4}}
     [:defs
      [:marker {:id "ig-arrow" :markerWidth 8 :markerHeight 8
                :refX 7 :refY 4 :orient "auto" :markerUnits "strokeWidth"}
       [:path {:d "M0,0 L0,8 L8,4 z" :fill "var(--app-text-secondary)"}]]]
     (dep-graph-edges order dependencies node-pos)
     (for [[k pos] node-pos]
       ^{:key k}
       [dep-graph-node k pos selected trace-by-key system on-select on-toggle])]))

(defn- trace-log-line [log]
  (let [error? (= "error" (:type log))]
    [:div {:style {:fontFamily "monospace"
                   :fontSize 12
                   :borderBottom "1px solid #f0f0f0"
                   :padding "6px 0"}}
     [:div
      [:span {:style {:color (if error? "#ff4d4f" "#52c41a")
                      :fontWeight 600}}
       (if error? "[error]" "[return]")]
      [:span {:style {:color "var(--app-text-secondary)" :marginLeft 8}}
       (str (:duration log) "ms")]]
     [:div {:style {:color "var(--app-text-regular)"}}
      [:span {:style {:color "#1890ff"}} "in "]
      (pr-str (:args log))]
     [:div {:style {:color "var(--app-text-regular)"}}
      [:span {:style {:color (if error? "#ff4d4f" "#52c41a")}}
       (if error? "err " "out ")]
      (pr-str (if error? (:error log) (:result log)))]]))

;; ─── 副作用辅助 ─────────────────────────────────────────────────────

(defn- auto-select-first! [selected data set-selected!]
  (when (and (nil? selected) (seq (:order data)))
    (set-selected! (first (:order data)))))

(defn- expand-all-tree! [data set-expanded!]
  (when data
    (set-expanded! (set (collect-tree-keys (->tree-nodes "config" (:config data)))))))

(defn- fetch-function-traces! [data]
  (doseq [[k sys] (:system data)]
    (when (= "function" (:kind sys))
      (rf/dispatch [:integrant/fetch-trace-logs (trace-key k)]))))

;; ─── 渲染子组件 ─────────────────────────────────────────────────────

(defn- integrant-header []
  [:div {:style {:display "flex" :justifyContent "space-between" :alignItems "center" :marginBottom 16}}
   [:h3 {:style {:margin 0}} "Integrant 依赖视图"]
   [antd/button {:icon (r/as-element [:> ReloadOutlined])
                 :onClick #(rf/dispatch [:integrant/fetch])}
    "刷新"]])

(defn- config-card [tree-data expanded set-expanded!]
  [antd/card {:title "静态配置 (Config)"
              :size "small"
              :styles {:body {:padding 12 :maxHeight 480 :overflow "auto"}}}
   [antd/tree {:treeData (clj->js tree-data)
               :expandedKeys (clj->js expanded)
               :onExpand #(set-expanded! (set %2))
               :onSelect #(set-expanded! (let [k (first %1)]
                                           (if (contains? expanded k)
                                             (disj expanded k)
                                             (conj expanded k))))
               :showLine true}]])

(defn- order-card [data selected set-selected!]
  [antd/card {:title "启动顺序 / 依赖 (Order)"
              :size "small"
              :styles {:body {:padding 12 :maxHeight 480 :overflow "auto"}}}
   [:div {:style {:display "flex" :flexDirection "column" :gap 4}}
    (for [[idx k] (map-indexed vector (:order data))]
      ^{:key k}
      [:div {:style {:padding "8px 12px"
                     :borderRadius 4
                     :cursor "pointer"
                     :background (if (= k selected) "#e6f7ff" "var(--app-fill-light)")
                     :border (if (= k selected) "1px solid #1677ff" "1px solid #f0f0f0")}
             :onClick #(set-selected! k)}
       [:span {:style {:color "var(--app-text-secondary)" :marginRight 8 :fontSize 12}} (inc idx)]
       [:span {:style {:fontWeight (if (= k selected) 600 400)}} k]])]])

(defn- dep-tags [color deps set-selected!]
  [antd/space {:size 4 :wrap true}
   (for [d deps]
     ^{:key d}
     [antd/tag {:color color :style {:cursor "pointer"} :onClick #(set-selected! d)} d])])

(defn- trace-log-block [trace-data]
  [:div {:style {:marginTop 12}}
   [:div {:style {:fontWeight 500 :marginBottom 8}} "输入/输出日志"]
   [:div {:style {:maxHeight 240 :overflow "auto" :background "var(--app-fill-light)" :padding "0 12px"}}
    (for [log (reverse (:logs trace-data))]
      ^{:key (:id log)}
      [trace-log-line log])]])

(defn- runtime-card [selected deps dents sys is-fn? trace-data set-selected!]
  [antd/card {:title (str "运行时详情: " (or selected "-"))
              :size "small"
              :styles {:body {:padding 12}}}
   [antd/descriptions {:bordered true :size "small" :column 1}
    [antd/descriptions-item {:label "类型"}
     (or (:kind sys) "-")]
    [antd/descriptions-item {:label "依赖"}
     (if (seq deps)
       [dep-tags "blue" deps set-selected!]
       "-")]
    [antd/descriptions-item {:label "被依赖"}
     (if (seq dents)
       [dep-tags "green" dents set-selected!]
       "-")]
    (when is-fn?
      [antd/descriptions-item {:label "输入/输出采集"}
       [perm/when-allowed "monitor:integrant:trace"
        [antd/switch {:checked (boolean (:active trace-data))
                      :checkedChildren "开启"
                      :unCheckedChildren "关闭"
                      :onChange #(rf/dispatch [:integrant/toggle-trace selected %])}]]])
    [antd/descriptions-item {:label "运行时摘要"}
     (if sys
       [:pre {:style {:margin 0 :fontSize 12 :whiteSpace "pre-wrap"}}
        (js/JSON.stringify (clj->js sys) nil 2)]
       "-")]]
   (when (and is-fn? (seq (:logs trace-data)))
     [trace-log-block trace-data])])

(defn- dep-graph-card [data selected trace-by-key set-selected!]
  [:div {:style {:marginTop 16}}
   [antd/card {:title "依赖图 (Dependency Graph)"
               :size "small"
               :styles {:body {:padding 12 :overflow "auto"}}}
    [dep-graph data selected trace-by-key
     set-selected!
     #(rf/dispatch [:integrant/toggle-trace %1 %2])]]])

;; ─── 主页面 ─────────────────────────────────────────────────────────

(defn integrant-page []
  (hooks/use-effect
   (fn []
     (rf/dispatch [:integrant/fetch])
     js/undefined)
   [])
  (let [data @(rf/subscribe [:integrant/data])
        [selected set-selected!] (hooks/use-state nil)
        [expanded set-expanded!] (hooks/use-state #{})
        trace-by-key @(rf/subscribe [:integrant/traces])
        trace-data (get trace-by-key selected)]
    (hooks/use-effect
     (fn []
       (auto-select-first! selected data set-selected!)
       js/undefined)
     [data])
    (hooks/use-effect
     (fn []
       (expand-all-tree! data set-expanded!)
       js/undefined)
     [data])
    (hooks/use-effect
     (fn []
       (when data
         (fetch-function-traces! data))
       js/undefined)
     [data])
    (hooks/use-effect
     (fn []
       (if (and selected (:active trace-data))
         (let [id (js/setInterval #(rf/dispatch [:integrant/fetch-trace-logs selected]) 1000)]
           #(js/clearInterval id))
         js/undefined))
     [selected (:active trace-data)])
    (if (nil? data)
      [:div {:style {:textAlign "center" :padding 48 :color "var(--app-text-secondary)"}} "加载中..."]
      (let [sel-k (keyword selected)
            deps (get-in data [:dependencies sel-k] [])
            dents (get-in data [:dependents sel-k] [])
            sys (get-in data [:system sel-k])
            is-fn? (= "function" (:kind sys))]
        [:div
         [integrant-header]
         [:div {:style {:display "grid"
                        :gridTemplateColumns "repeat(auto-fit, minmax(320px, 1fr))"
                        :gap 16}}
          [config-card (->tree-nodes "config" (:config data)) expanded set-expanded!]
          [order-card data selected set-selected!]
          [runtime-card selected deps dents sys is-fn? trace-data set-selected!]]
         [dep-graph-card data selected trace-by-key set-selected!]]))))
