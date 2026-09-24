(ns com.ruoyi.frontend.components.page-toolbar
  "页面工具栏容器。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.i18n :as i18n]
   [com.ruoyi.frontend.perm :as perm]))

(defn page-toolbar [{:keys [left right style]}]
  [:div {:style (merge {:display "flex"
                        :justifyContent "space-between"
                        :alignItems "center"
                        :gap 12
                        :padding "12px 22px 14px 22px"
                        :background "var(--app-bg)"}
                       style)}
   left
   right])

(defn kind-style
  "彩色按钮的配色(css/app.css 的 --app-btn-<kind>-* 变量,浅色 / 暗色主题各一套)。"
  [kind]
  (let [k (name kind)]
    {:color (str "var(--app-btn-" k "-color)")
     :border (str "1px solid var(--app-btn-" k "-border)")
     :background (str "var(--app-btn-" k "-bg)")}))

(def button-colors
  (assoc (into {} (map (juxt identity kind-style)) [:add :edit :delete :import :export])
         :default {:height 36 :borderRadius 4}))

(defn- toolbar-button*
  [kind icon on-click disabled? loading? content]
  [antd/button {:icon icon
                :disabled disabled?
                :loading loading?
                :on-click on-click
                :style (merge {:height 36
                               :minWidth 86
                               :padding "0 15px"
                               :borderRadius 4
                               :fontSize 14}
                              (get button-colors kind)
                              (when disabled?
                                {:opacity 0.55}))}
   content])

(defn toolbar-button
  "工具栏按钮。:perm 为所需权限(字符串或集合),没有权限时不渲染(见 frontend.perm)。"
  [{:keys [kind icon on-click disabled? loading? children label perm]}]
  (when (perm/allowed? perm)
    (toolbar-button* kind icon on-click disabled? loading? (or label children))))

(defn toolbar-left [& children]
  (into [:div {:style {:display "flex" :gap 10}}] children))

(defn search-button [{:keys [icon on-click label]}]
  [antd/button {:type "primary"
                :icon icon
                :on-click on-click
                :style {:height 36
                        :minWidth 86
                        :borderRadius 4
                        :fontSize 14
                        :background "#409eff"
                        :border "1px solid #409eff"}}
   (or label (i18n/tr "搜索"))])

(defn reset-button [{:keys [icon on-click label]}]
  [antd/button {:icon icon
                :on-click on-click
                :style {:height 36
                        :minWidth 86
                        :borderRadius 4
                        :fontSize 14
                        :color "var(--app-text-regular)"
                        :border "1px solid var(--app-border)"}}
   (or label (i18n/tr "重置"))])

(defn round-tool-button [{:keys [icon on-click title]}]
  [antd/tooltip {:title title}
   [antd/button {:shape "circle"
                 :icon icon
                 :on-click on-click
                 :style {:width 40
                         :height 40
                         :display "inline-flex"
                         :alignItems "center"
                         :justifyContent "center"
                         :color "var(--app-text-regular)"
                         :border "1px solid var(--app-border)"
                         :boxShadow "0 2px 8px rgba(0,0,0,0.06)"}}]])

(defn toolbar-right [& children]
  (into [:div {:style {:display "flex" :gap 12
                       :alignItems "center"}}] children))
