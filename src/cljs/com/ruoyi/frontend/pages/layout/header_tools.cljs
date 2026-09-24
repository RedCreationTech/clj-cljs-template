(ns com.ruoyi.frontend.pages.layout.header-tools
  "顶部工具:菜单搜索(在当前用户能访问的页面里搜索并跳转)、通知铃铛(最新通知 + 未读角标)。"
  (:require
   ["@ant-design/icons" :refer [BellOutlined SearchOutlined]]
   ["antd" :refer [Badge Button Empty Popover Select]]
   [com.ruoyi.frontend.i18n :as i18n]
   [com.ruoyi.frontend.pages.layout.menu-build :as menu-build]
   [com.ruoyi.frontend.perm :as perm]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

(def ^:private icon-btn {:type "text" :style {:fontSize 18 :color "var(--app-text-regular)"}})

(defn page-options
  "当前用户菜单里的页面 → 搜索选项 [{:value \"user\" :label \"用户管理\"}](按钮类菜单不参与)。"
  [menus]
  (->> (menu-build/page-labels (menu-build/filter-visible-menus menus))
       (map (fn [[k label]] {:value (name k) :label (i18n/tr label)}))
       (sort-by :label)))

(defn menu-search
  "搜索菜单并跳转。"
  [menus]
  (let [[open? set-open!] (hooks/use-state false)]
    [:> Popover {:open open?
                 :onOpenChange set-open!
                 :trigger "click"
                 :placement "bottomRight"
                 :destroyOnHidden true
                 :content (r/as-element
                           [:> Select {:showSearch true
                                       :autoFocus true
                                       :defaultOpen true
                                       :style {:width 240}
                                       :placeholder (i18n/tr "搜索菜单")
                                       :optionFilterProp "label"
                                       :options (clj->js (page-options menus))
                                       :onChange (fn [v]
                                                   (set-open! false)
                                                   (rf/dispatch [:navigate (keyword v)]))}])}
     [:> Button (assoc icon-btn :title (i18n/tr "搜索菜单") :icon (r/as-element [:> SearchOutlined]))]]))

(defn- notice-item [{:keys [notice_name notice_type create_time]}]
  [:div {:style {:padding "8px 0" :borderBottom "1px solid var(--app-border-light)"}}
   [:div {:style {:color "var(--app-text-primary)" :fontSize 14}} notice_name]
   [:div {:style {:color "var(--app-text-secondary)" :fontSize 12 :marginTop 2}}
    (str (i18n/tr (if (= "2" notice_type) "公告" "通知")) " · " (or create_time ""))]])

(defn- notice-list [items]
  [:div {:style {:width 300}}
   (if (seq items)
     (for [item items]
       ^{:key (:notice_id item)} [notice-item item])
     [:> Empty {:image (.-PRESENTED_IMAGE_SIMPLE ^js Empty) :description (i18n/tr "暂无通知")}])
   (when (perm/allowed? "system:notice:list")
     [:div {:style {:textAlign "right" :marginTop 8}}
      [:a {:on-click #(rf/dispatch [:navigate :notice])} (i18n/tr "查看全部")]])])

(defn notice-bell
  "通知铃铛:进入主布局时拉取一次,打开时刷新并记为已读。"
  []
  (hooks/use-effect (fn [] (rf/dispatch [:notice-bell/fetch]) js/undefined) [])
  (let [items @(rf/subscribe [:notice-bell/items])
        unread @(rf/subscribe [:notice-bell/unread])]
    [:> Popover {:trigger "click"
                 :placement "bottomRight"
                 :title (i18n/tr "通知")
                 :content (r/as-element [notice-list items])
                 :onOpenChange (fn [open?]
                                 (when open?
                                   (rf/dispatch [:notice-bell/fetch])
                                   (rf/dispatch [:notice-bell/mark-seen])))}
     [:> Badge {:count unread :size "small"}
      [:> Button (assoc icon-btn :title (i18n/tr "通知") :icon (r/as-element [:> BellOutlined]))]]]))
