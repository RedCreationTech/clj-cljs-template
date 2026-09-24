(ns com.ruoyi.frontend.pages.layout.tabs
  "主布局的多 Tab 栏组件(标签项/右键菜单/滚动容器/操作按钮)。"
  (:require
   ["@ant-design/icons" :refer [ArrowRightOutlined CloseCircleOutlined CloseOutlined DownOutlined
                                HomeOutlined LeftOutlined ReloadOutlined RightOutlined]]
   ["antd" :refer [Dropdown]]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.components.icon-picker :as icon-picker]
   [com.ruoyi.frontend.i18n :as i18n]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

(defn- tab-context-menu
  "标签页右键菜单项。"
  [key has-others? has-right?]
  (clj->js
   [{:key "refresh"
     :label (i18n/tr "刷新页面")
     :icon (r/as-element [:> ReloadOutlined])}
    {:key "close-current"
     :label (i18n/tr "关闭当前")
     :icon (r/as-element [:> CloseOutlined])
     :disabled (= key :dashboard)}
    {:key "close-others"
     :label (i18n/tr "关闭其他")
     :icon (r/as-element [:> CloseCircleOutlined])
     :disabled (not has-others?)}
    {:key "close-right"
     :label (i18n/tr "关闭右侧")
     :icon (r/as-element [:> ArrowRightOutlined])
     :disabled (not has-right?)}
    {:key "close-all"
     :label (i18n/tr "全部关闭")
     :icon (r/as-element [:> CloseCircleOutlined])}]))

;; Tab 项内联样式(随 active / card 风格变化)。
(defn- tab-item-style
  [active? card-style?]
  {:display "inline-flex"
   :flex "0 0 auto"
   :alignItems "center"
   :height (if card-style? 34 38)
   :padding (if card-style? "0 16px" "0 18px")
   :marginRight (if card-style? 6 2)
   :background (if active? "var(--ant-color-primary-bg, #e8f4ff)" "var(--app-bg)")
   :color (if active? "#409eff" "var(--app-text-regular)")
   :borderRadius (cond
                   card-style? 4
                   active? "14px 14px 0 0"
                   :else "0")
   :cursor "pointer"
   :fontSize 14
   :transition "background 0.2s, color 0.2s"
   :border "1px solid var(--app-border-light)"
   :borderBottom (if active? "1px solid var(--ant-color-primary-bg, #e8f4ff)" "1px solid var(--app-border-light)")
   :boxShadow (if (and card-style? active?) "0 1px 4px rgba(64,158,255,0.18)" "none")
   :whiteSpace "nowrap"
   :position "relative"
   :overflow "hidden"})

;; Tab 右键菜单 / 工具栏下拉菜单的统一点击处理。
(defn- tab-menu-onclick
  [key]
  (fn [e]
    (case (.-key e)
      "close-current" (rf/dispatch [:tabs/close key])
      "close-others" (rf/dispatch [:tabs/remove-others key])
      "close-right" (rf/dispatch [:tabs/remove-right key])
      "close-all" (rf/dispatch [:tabs/remove-all])
      "refresh" (.reload js/location)
      nil)))

(defn- tab-item
  "单个Tab项组件"
  [{:keys [key label icon closable active?]}]
  (let [tabs @(rf/subscribe [:tabs/items])
        layout-settings @(rf/subscribe [:layout/settings])
        show-icon? (get layout-settings :show-tab-icon? true)
        card-style? (= "card" (get layout-settings :tab-style "google"))
        idx (.indexOf (clj->js (mapv :key tabs)) key)
        has-others? (> (count tabs) 1)
        has-right? (< idx (dec (count tabs)))]
    [:> Dropdown {:menu {:items (tab-context-menu key has-others? has-right?)
                         :onClick (tab-menu-onclick key)}
                  :trigger (clj->js ["contextMenu"])}
     [:div {:class (str "tab-item" (when active? " tab-item-active"))
            :style (tab-item-style active? card-style?)
            :on-click #(do (rf/dispatch [:tabs/activate key]) (rf/dispatch [:navigate (keyword key)]))}
      (when show-icon?
        (if icon
          (when-let [icon-el (icon-picker/icon-element icon {:style {:marginRight 6 :fontSize 12}})]
            icon-el)
          (when (= key :dashboard)
            [:> HomeOutlined {:style {:marginRight 6 :fontSize 12}}])))
      [:span (i18n/tr label)]
      (when (and closable (not= key :dashboard))
        [:> CloseOutlined {:style {:marginLeft 8 :fontSize 10
                                   :opacity (if active? 0.8 0.4)
                                   :transition "opacity 0.2s"}
                           :on-click (fn [e]
                                       (.stopPropagation e)
                                       (rf/dispatch [:tabs/close key]))}])]]))

(defn- scroll-tabs
  "左右滚动标签页"
  [container-ref direction]
  (when-let [el (.-current container-ref)]
    (let [scroll-amount 200]
      (.scrollBy el #js {:left (* direction scroll-amount) :behavior "smooth"}))))

;; 标签栏滚动状态与副作用,返回 [container-ref show-scroll? can-left? can-right? check-scroll]。
(defn- use-tab-scroll
  [tabs-count active]
  (let [container-ref (hooks/use-ref nil)
        [show-scroll? set-show-scroll!] (hooks/use-state false)
        [can-left? set-can-left!] (hooks/use-state false)
        [can-right? set-can-right!] (hooks/use-state false)
        check-scroll (fn []
                       (when-let [el (.-current container-ref)]
                         (let [sw (.-scrollWidth el)
                               cw (.-clientWidth el)
                               left (.-scrollLeft el)]
                           (set-show-scroll! (> sw cw))
                           (set-can-left! (> left 0))
                           (set-can-right! (> (- sw cw left) 1)))))]
    (hooks/use-effect
     (fn []
       (when-let [el (.-current container-ref)]
         (check-scroll)
         (if (exists? js/ResizeObserver)
           (let [ro (js/ResizeObserver. (fn [_] (check-scroll)))]
             (.observe ro el)
             (fn [] (.disconnect ro)))
           (do (.addEventListener js/window "resize" check-scroll)
               (fn [] (.removeEventListener js/window "resize" check-scroll))))))
     [tabs-count])
    (hooks/use-effect
     (fn []
       (when-let [el (.-current container-ref)]
         (when-let [active-el (.querySelector el ".tab-item-active")]
           (let [el-left (.-offsetLeft active-el)
                 el-width (.-offsetWidth active-el)
                 scroll (.-scrollLeft el)
                 cw (.-clientWidth el)]
             (cond
               (< el-left scroll)
               (set! (.-scrollLeft el) el-left)

               (> (+ el-left el-width) (+ scroll cw))
               (set! (.-scrollLeft el) (- (+ el-left el-width) cw))))))
       js/undefined)
     [active])
    [container-ref show-scroll? can-left? can-right? check-scroll]))

;; 标签栏左/右侧滚动按钮(仅在溢出时显示)。
(defn- tab-scroll-side-btn
  [side can? container-ref]
  (let [left? (= side :left)]
    [:div {:class (str "tab-scroll-btn " (if left? "tab-scroll-left" "tab-scroll-right"))
           :style {:flex "0 0 auto"
                   :cursor (if can? "pointer" "not-allowed")
                   :width 32
                   :height 40
                   :display "flex"
                   :alignItems "center"
                   :justifyContent "center"
                   (if left? :borderRight :borderLeft) "1px solid #ebeef5"
                   :color (if can? "var(--ant-color-text-secondary, #666)" "var(--ant-color-border, #ccc)")
                   :fontSize 16
                   :userSelect "none"}
           :on-click #(when can? (scroll-tabs container-ref (if left? -1 1)))}
     (if left?
       [:> LeftOutlined {:style {:fontSize 12}}]
       [:> RightOutlined {:style {:fontSize 12}}])]))

;; 承载 tab-item 的可横向滚动容器。
(defn- tab-scroll-container
  [container-ref tabs active check-scroll]
  [:div {:ref container-ref
         :style {:flex 1
                 :display "flex"
                 :alignItems "flex-end"
                 :height 40
                 :paddingLeft 0
                 :overflowX "auto"
                 :overflowY "hidden"
                 :whiteSpace "nowrap"
                 :scrollbarWidth "none"
                 ::WebkitOverflowScrolling "touch"
                 :msOverflowStyle "none"}
         :on-scroll check-scroll}
   (for [tab tabs]
     ^{:key (:key tab)}
     [tab-item (assoc tab :active? (= (:key tab) active))])])

;; 标签栏右侧操作按钮组:左右滚动、刷新、关闭菜单。
(defn- tab-action-buttons
  [container-ref tabs active]
  (let [active-idx (.indexOf (clj->js (mapv :key tabs)) active)]
    [:div {:style {:display "flex" :alignItems "center" :marginLeft 0 :height 40
                   :borderLeft "1px solid var(--app-border-light)"}}
     [antd/tooltip {:title (i18n/tr "向左滚动")}
      [:> LeftOutlined {:style {:cursor "pointer" :color "var(--app-text-secondary)"
                                :fontSize 13 :padding "13px 12px"
                                :borderRight "1px solid var(--app-border-light)"}
                        :on-click #(scroll-tabs container-ref -1)}]]
     [antd/tooltip {:title (i18n/tr "向右滚动")}
      [:> RightOutlined {:style {:cursor "pointer" :color "var(--app-text-secondary)"
                                 :fontSize 13 :padding "13px 12px"
                                 :borderRight "1px solid var(--app-border-light)"}
                         :on-click #(scroll-tabs container-ref 1)}]]
     [antd/tooltip {:title (i18n/tr "刷新当前页")}
      [:> ReloadOutlined {:style {:cursor "pointer" :color "var(--app-text-secondary)"
                                  :fontSize 14 :padding "13px 12px"
                                  :borderRight "1px solid var(--app-border-light)"}
                          :on-click #(.reload js/location)}]]
     [antd/dropdown {:menu {:items (tab-context-menu active
                                                     (> (count tabs) 1)
                                                     (< active-idx (dec (count tabs))))
                            :onClick (tab-menu-onclick active)}}
      [:> DownOutlined {:style {:cursor "pointer" :color "var(--app-text-secondary)"
                                :fontSize 12 :padding "14px 12px"}}]]]))

(defn tab-bar
  "Tab栏组件 — RuoYi 风格，支持左右滚动"
  []
  (let [tabs @(rf/subscribe [:tabs/items])
        active @(rf/subscribe [:tabs/active])
        [container-ref show-scroll? can-left? can-right? check-scroll]
        (use-tab-scroll (count tabs) active)]
    [:div {:class "app-tab-bar"
           :style {:borderBottom "1px solid var(--app-border)"
                   :padding "0 0 0 0"
                   :display "flex"
                   :alignItems "center"
                   :height 40
                   :background "var(--app-bg)"
                   :boxShadow "0 1px 2px rgba(0,0,0,0.04)"}}
     (when show-scroll? [tab-scroll-side-btn :left can-left? container-ref])
     [tab-scroll-container container-ref tabs active check-scroll]
     (when show-scroll? [tab-scroll-side-btn :right can-right? container-ref])
     [tab-action-buttons container-ref tabs active]]))
