(ns com.ruoyi.frontend.pages.layout.header
  "主布局 Header 的组成部分:顶部/侧边导航区、右侧操作区、显示设置面板。"
  (:require
   ["@ant-design/icons" :refer [ExpandOutlined FontSizeOutlined GithubOutlined
                                GlobalOutlined MenuFoldOutlined MenuUnfoldOutlined QuestionCircleOutlined]]
   ["antd" :refer [Avatar Button Dropdown Menu Popover Segmented]]
   [com.ruoyi.frontend.config :as config]
   [com.ruoyi.frontend.i18n :as i18n]
   [com.ruoyi.frontend.pages.layout.header-tools :as tools]
   [re-frame.core :as rf]
   [reagent.core :as r]))

;; 顶部导航模式下的 Header 左侧:品牌 + 横向菜单。
(defn top-nav-header
  [{:keys [show-logo? menu-instance-key selected-menu-key menu-items on-click]}]
  [:<>
   (when show-logo?
     [:div {:style {:display "flex" :alignItems "center" :gap 8
                    :height 56 :paddingRight 16 :fontSize 16 :fontWeight 700
                    :color "var(--app-text-primary)" :whiteSpace "nowrap"}}
      [:div {:style {:width 24 :height 24 :borderRadius "50%"
                     :display "flex" :alignItems "center" :justifyContent "center"
                     :color "#23b99a" :fontSize 20 :fontWeight 300}}
       "⌁"]
      [:span config/app-name]])
   [:> Menu {:key (str "top-" menu-instance-key)
             :mode "horizontal"
             :selectedKeys (clj->js [selected-menu-key])
             :items menu-items
             :onClick on-click
             :style {:flex 1 :minWidth 0 :height 56 :lineHeight "56px"
                     :borderBottom "none" :fontSize 14}}]])

;; 侧边导航模式下的 Header 左侧:折叠按钮 + 面包屑。
(defn side-nav-header
  [{:keys [collapsed set-collapsed! breadcrumbs]}]
  [:<>
   [:div {:style {:cursor "pointer" :padding "0 6px" :fontSize 21
                  :display "flex" :alignItems "center"
                  :color "var(--app-text-primary)"
                  :transition "color 0.3s"}
          :on-click #(set-collapsed! (not collapsed))}
    (if collapsed
      [:> MenuUnfoldOutlined]
      [:> MenuFoldOutlined])]
   [:div {:style {:display "flex" :alignItems "center" :gap 10 :fontSize 14}}
    (for [[idx crumb] (map-indexed vector breadcrumbs)]
      ^{:key (str "crumb-" idx)}
      [:<>
       (when (pos? idx)
         [:span {:style {:color "var(--app-text-placeholder)"}} "/"])
       [:span {:style {:color (if (= idx (dec (count breadcrumbs))) "#97a8be" "var(--app-text-primary)")
                       :fontWeight (if (= idx (dec (count breadcrumbs))) 400 500)}}
        (i18n/tr crumb)]])]])

(defn- display-settings-panel
  "字号按钮弹出的显示设置面板。"
  []
  (let [component-size @(rf/subscribe [:theme/component-size])
        font-size @(rf/subscribe [:theme/font-size])]
    [:div {:style {:width 220 :padding 4}}
     [:div {:style {:fontSize 14 :fontWeight 600 :color "var(--app-text-primary)" :margin "0 0 12px"}}
      (i18n/tr "显示设置")]
     [:div {:style {:marginBottom 14}}
      [:div {:style {:fontSize 13 :color "var(--app-text-regular)" :marginBottom 8}} (i18n/tr "布局密度")]
      [:> Segmented {:block true
                     :value component-size
                     :onChange #(rf/dispatch [:theme/set-density %])
                     :options #js [#js {:label (i18n/tr "紧凑") :value "small"}
                                   #js {:label (i18n/tr "默认") :value "middle"}
                                   #js {:label (i18n/tr "宽松") :value "large"}]}]]
     [:div
      [:div {:style {:fontSize 13 :color "var(--app-text-regular)" :marginBottom 8}} (i18n/tr "字体大小")]
      [:> Segmented {:block true
                     :value font-size
                     :onChange #(rf/dispatch [:theme/set-font-size %])
                     :options #js [#js {:label (i18n/tr "小") :value "small"}
                                   #js {:label (i18n/tr "中") :value "middle"}
                                   #js {:label (i18n/tr "大") :value "large"}]}]]]))

(defn- language-switcher
  "界面语言切换。"
  []
  (let [current @(rf/subscribe [:i18n/locale])]
    [:> Dropdown {:menu {:items (clj->js (for [{:keys [key label]} i18n/locales]
                                           {:key (name key) :label label}))
                         :selectable true
                         :selectedKeys (clj->js [(name current)])
                         :onClick #(rf/dispatch [:i18n/set-locale (keyword (.-key %))])}
                  :trigger (clj->js ["click"])}
     [:> Button {:type "text"
                 :title (i18n/tr "语言")
                 :style {:fontSize 18 :color "var(--app-text-regular)"}
                 :icon (r/as-element [:> GlobalOutlined])}]]))

(defn- display-settings-button
  "右上角显示设置按钮。"
  []
  [:> Popover {:content (r/as-element [display-settings-panel])
               :trigger "click"
               :placement "bottomRight"}
   [:> Button {:type "text"
               :style {:fontSize 18 :color "var(--app-text-regular)"}
               :icon (r/as-element [:> FontSizeOutlined])}]])

;; Header 右侧操作区:菜单搜索/GitHub/文档/全屏/显示设置/语言/通知/头像菜单。
(defn header-actions
  [{:keys [user menus set-settings-open!]}]
  (let [icon-btn {:type "text" :style {:fontSize 18 :color "var(--app-text-regular)"}}]
    [:div {:style {:display "flex" :alignItems "center" :gap 6}}
     [tools/menu-search menus]
     [:> Button (assoc icon-btn :title "GitHub" :icon (r/as-element [:> GithubOutlined])
                       :onClick #(js/window.open config/repo-url "_blank"))]
     [:> Button (assoc icon-btn :title (i18n/tr "文档") :icon (r/as-element [:> QuestionCircleOutlined])
                       :onClick #(js/window.open config/docs-url "_blank"))]
     ;; 全屏
     [:> Button (assoc icon-btn :icon (r/as-element [:> ExpandOutlined])
                       :onClick #(let [doc js/document.documentElement]
                                   (if (.-fullscreenElement js/document)
                                     (.exitFullscreen js/document)
                                     (.requestFullscreen doc))))]
     [display-settings-button]
     [language-switcher]
     [tools/notice-bell]
     ;; 头像 + 下拉菜单
     [:> Dropdown {:menu {:items (clj->js [{:key "profile" :label (i18n/tr "个人中心")}
                                           {:key "layout-settings" :label (i18n/tr "布局设置")}
                                           {:type "divider"}
                                           {:key "logout" :label (i18n/tr "退出登录") :danger true}])
                          :onClick (fn [e]
                                     (case (.-key e)
                                       "profile" (rf/dispatch [:navigate :profile])
                                       "layout-settings" (set-settings-open! true)
                                       "logout" (rf/dispatch [:auth/logout])
                                       nil))}
                   :trigger (clj->js ["click"])}
      [:div {:style {:display "flex" :alignItems "center" :gap 8 :cursor "pointer" :padding "0 6px"}}
       [:> Avatar (merge {:size 32
                          :style {:background "linear-gradient(135deg,#f7d7c4,#9bc9ff)"
                                  :color "#fff"
                                  :fontWeight 700}}
                         (when (seq (:avatar user)) {:src (:avatar user)}))
        (str (first (or (:nick_name user) (:user_name user) "管理员")))]
       [:span {:style {:fontSize 14 :fontWeight 600 :color "var(--app-text-primary)"}} (or (:nick_name user) (:user_name user) "管理员")]]]]))
