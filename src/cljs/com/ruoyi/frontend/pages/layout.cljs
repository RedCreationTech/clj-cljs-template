(ns com.ruoyi.frontend.pages.layout
  "主布局页面，包含多Tab支持。Tab 栏 / Header / 菜单构建 / 页面分发 / 路由数据均拆到子命名空间。"
  (:require
   ["antd" :refer [Layout Menu]]
   [com.ruoyi.frontend.components.error-boundary :as error-boundary]
   [com.ruoyi.frontend.components.layout-settings :as layout-settings]
   [com.ruoyi.frontend.config :as config]
   [com.ruoyi.frontend.i18n :as i18n]
   [com.ruoyi.frontend.pages.layout.header :as layout-header]
   [com.ruoyi.frontend.pages.layout.menu-build :as menu-build]
   [com.ruoyi.frontend.pages.layout.menu-data :as menu-data]
   [com.ruoyi.frontend.pages.layout.page-view :as page-view]
   [com.ruoyi.frontend.pages.layout.tabs :as layout-tabs]
   [com.ruoyi.frontend.router :as router]
   [re-frame.core :as rf]
   [reagent.hooks :as hooks]))

;; ─── Tab 动画样式 ──────────────────────────────────────────────────────

(defn- tab-animation-styles []
  [:style
   "
@keyframes tabSlideIn {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes tabFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes tabPulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.08); }
  100% { transform: scale(1.05); }
}

.tab-item {
  animation: tabSlideIn 0.3s ease-out;
}

.tab-item-active {
  animation: tabPulse 0.3s ease-out;
}

.tab-content-enter {
  animation: tabFadeIn 0.3s ease-out;
}
"])

;; ─── 视图派生 ──────────────────────────────────────────────────────────

;; 菜单点击 -> 导航到对应页面并开启/激活 Tab。
(defn- make-menu-click-handler
  [labels icons]
  (fn [e]
    (let [k (.-key e)
          matched (router/match-route (str "/" k))
          page (or (:handler matched) (keyword k))]
      (when (:handler matched)
        (rf/dispatch [:navigate page])
        (rf/dispatch [:tabs/add page (get labels page "页面") (get icons page)])))))

;; 由订阅值派生主布局所需数据(纯计算,无 hooks)。
(defn- compute-layout-view
  [page user collapsed layout-settings sider-width collapsed-width]
  (let [filtered-menus (menu-build/filter-visible-menus (vec (or (:menus user) [])))
        menu-items (menu-build/menu->antd-items filtered-menus)
        selected-menu-key (or (menu-data/page->menu-key page) (name page))
        labels (merge menu-data/route-labels (menu-build/page-labels filtered-menus))
        icons (merge menu-data/route-icons (menu-build/page-icons filtered-menus))
        top-nav? (= (get layout-settings :nav-mode "side") "top")]
    {:page page
     :menu-items menu-items
     ;; 默认折叠:仅展开当前页所在分支的祖先分组,其余收起
     :open-menu-keys (menu-build/menu-ancestor-keys filtered-menus selected-menu-key)
     :menu-instance-key (str "permission-menu-" (hash filtered-menus))
     :selected-menu-key selected-menu-key
     :breadcrumbs (get menu-data/page-breadcrumbs page ["首页"])
     :top-nav? top-nav?
     :content-left (if top-nav? 0 (if collapsed collapsed-width sider-width))
     :on-click (make-menu-click-handler labels icons)
     :show-logo? (get layout-settings :show-logo? true)
     :fixed-header? (get layout-settings :fixed-header? true)
     :open-tags? (get layout-settings :open-tags? true)
     :show-footer? (get layout-settings :show-footer? true)
     :dynamic-title? (get layout-settings :dynamic-title? true)}))

;; ─── 布局区块 ──────────────────────────────────────────────────────────

;; 左侧折叠菜单栏。
(defn- app-sider
  [{:keys [collapsed set-collapsed! show-logo? sider-width collapsed-width
           menu-instance-key selected-menu-key open-menu-keys menu-items on-click]}]
  ;; 非受控 defaultOpenKeys:默认仅展开当前页分支、其余折叠;开合交给 antd 自己管理,避免回弹。
  [:> Layout.Sider {:collapsible true
                    :collapsed collapsed
                    :onCollapse set-collapsed!
                    :theme "dark"
                    :width sider-width
                    :collapsedWidth collapsed-width
                    :trigger nil
                    :style {:background "#172033"
                            :boxShadow "2px 0 8px rgba(0,0,0,0.18)"}}
   (when show-logo?
     [:div {:style {:height 56 :display "flex" :alignItems "center"
                    :justifyContent "center" :gap 8 :fontSize 16 :fontWeight 700
                    :color "#fff" :background "#172033"}}
      [:div {:style {:width 24 :height 24 :borderRadius "50%"
                     :display "flex" :alignItems "center" :justifyContent "center"
                     :color "#79e0c2" :fontSize 20 :fontWeight 300}}
       "⌁"]
      (when-not collapsed [:span config/app-name])])
   [:> Menu {:key (str "side-" menu-instance-key)
             :theme "dark"
             :mode "inline"
             :inlineCollapsed collapsed
             :style {:background "#172033" :fontSize 14 :borderInlineEnd "none"}
             :selectedKeys (clj->js [selected-menu-key])
             :defaultOpenKeys (clj->js open-menu-keys)
             :items menu-items
             :onClick on-click}]])

;; 右下角悬浮徽标。
(defn- floating-badge []
  [:div {:class "app-layout-float"
         :style {:position "fixed" :right 14 :bottom 54
                 :width 42 :height 42 :borderRadius "50%"
                 :background "#e989aa" :color "#fff"
                 :display "flex" :alignItems "center" :justifyContent "center"
                 :fontSize 18 :fontWeight 700
                 :boxShadow "0 4px 12px rgba(233,137,170,0.35)"
                 :zIndex 20}}
   "LA"])

;; 固定底部版权栏。
(defn- layout-footer
  [content-left]
  [:div {:class "app-layout-footer"
         :style {:position "fixed" :left content-left :right 0 :bottom 0
                 :height 36 :display "flex" :alignItems "center" :justifyContent "flex-end"
                 :padding "0 20px" :borderTop "1px solid var(--app-border-light)"
                 :color "var(--app-text-secondary)" :fontSize 14 :background "var(--app-bg)" :zIndex 10}}
   "Copyright © 2018-2026 RuoYi. All Rights Reserved."])

;; 主内容区(带 Error Boundary,避免单个页面崩溃导致整个布局白屏)。
(defn- layout-content
  [{:keys [page content-left open-tags? show-footer?]}]
  [:> Layout.Content {:style {:margin 0
                              :padding 0
                              :background "var(--app-bg)"
                              :minHeight (if open-tags?
                                           "calc(100vh - 96px)"
                                           "calc(100vh - 56px)")
                              :paddingBottom (if show-footer? 36 0)
                              :position "relative"}
                      :key (name page)
                      :class "tab-content-enter"}
   [error-boundary/boundary [page-view/render-page page]]
   [floating-badge]
   (when show-footer? [layout-footer content-left])])

;; 顶部 Header:左(导航/面包屑) + 右(操作区) + 布局设置抽屉。
(defn- app-header
  [{:keys [top-nav? fixed-header? show-logo? collapsed set-collapsed! breadcrumbs
           menu-instance-key selected-menu-key menu-items on-click
           user settings-open? set-settings-open!]}]
  [:> Layout.Header {:style {:padding "0 16px"
                             :display "flex" :justifyContent "space-between"
                             :alignItems "center" :height 56
                             :background "var(--app-bg)"
                             :borderBottom "1px solid var(--app-border)"
                             :boxShadow "0 1px 4px rgba(0,21,41,0.08)"
                             :position (when fixed-header? "sticky")
                             :top 0
                             :zIndex 30}}
   [:div {:style {:display "flex" :alignItems "center" :gap 12 :flex 1 :minWidth 0}}
    (if top-nav?
      [layout-header/top-nav-header {:show-logo? show-logo?
                                     :menu-instance-key menu-instance-key
                                     :selected-menu-key selected-menu-key
                                     :menu-items menu-items
                                     :on-click on-click}]
      [layout-header/side-nav-header {:collapsed collapsed
                                      :set-collapsed! set-collapsed!
                                      :breadcrumbs breadcrumbs}])]
   [layout-header/header-actions {:user (:user user) :menus (:menus user) :set-settings-open! set-settings-open!}]
   [layout-settings/layout-settings-drawer {:open? settings-open?
                                            :on-close #(set-settings-open! false)}]])

;; ─── 主布局 ────────────────────────────────────────────────────────────

(defn main-layout []
  (let [[collapsed set-collapsed!] (hooks/use-state false)
        [settings-open? set-settings-open!] (hooks/use-state false)
        user @(rf/subscribe [:auth/user])
        page @(rf/subscribe [:page])
        layout-settings @(rf/subscribe [:layout/settings])
        sider-width 196
        collapsed-width 56
        view (compute-layout-view page user collapsed layout-settings sider-width collapsed-width)]
    (hooks/use-effect
     (fn []
       (set! (.-title js/document)
             (if (:dynamic-title? view)
               (str (i18n/tr (last (:breadcrumbs view))) " - " config/app-name)
               config/app-name))
       js/undefined)
     [page (:dynamic-title? view)])
    [:> Layout {:style {:minHeight "100vh"
                        :background "var(--app-bg)"
                        :fontFamily "\"Helvetica Neue\", Helvetica, \"PingFang SC\", \"Hiragino Sans GB\", \"Microsoft YaHei\", Arial, sans-serif"
                        :fontSize 14}}
     [tab-animation-styles]
     (when-not (:top-nav? view)
       [app-sider (assoc view :collapsed collapsed
                         :set-collapsed! set-collapsed!
                         :sider-width sider-width
                         :collapsed-width collapsed-width)])
     [:> Layout {:style {:background "var(--app-bg)"}}
      [app-header (assoc view :user user
                         :collapsed collapsed
                         :set-collapsed! set-collapsed!
                         :settings-open? settings-open?
                         :set-settings-open! set-settings-open!)]
      (when (:open-tags? view)
        [layout-tabs/tab-bar])
      [layout-content (select-keys view [:page :content-left :open-tags? :show-footer?])]]]))
