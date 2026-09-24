(ns com.ruoyi.frontend.pages.user.search
  "用户页搜索表单与工具栏（presentational，接收查询字段/列配置与回调）。"
  (:require
   ["@ant-design/icons" :refer [AppstoreOutlined DeleteOutlined DownloadOutlined EditOutlined
                                PlusOutlined ReloadOutlined SearchOutlined UploadOutlined]]
   ["antd" :refer [DatePicker]]
   [com.ruoyi.frontend.antd :as antd]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

(def range-picker (r/adapt-react-class (.-RangePicker DatePicker)))

(defn- search-field [label width input]
  [:div {:style {:display "flex" :alignItems "center" :gap 8 :width width}}
   [:span {:style {:whiteSpace "nowrap" :fontSize 14 :fontWeight 600 :color "var(--app-text-regular)"
                   :width (if (= label "状态") 42 58) :textAlign "right"}} label]
   input])

(defn- search-fields [{:keys [query-params on-field on-search on-reset]}]
  [:div {:style {:display "flex"
                 :flexWrap "wrap"
                 :columnGap 24
                 :rowGap 8
                 :alignItems "center"}}
   [search-field "用户名称" 300
    [antd/input {:placeholder "请输入用户名称"
                 :style {:width 232 :height 34 :borderRadius 4}
                 :value (:user_name query-params)
                 :on-change #(on-field :user_name (.. % -target -value))}]]
   [search-field "手机号码" 300
    [antd/input {:placeholder "请输入手机号码"
                 :style {:width 232 :height 34 :borderRadius 4}
                 :value (:phonenumber query-params)
                 :on-change #(on-field :phonenumber (.. % -target -value))}]]
   [search-field "状态" 260
    [antd/select {:placeholder "用户状态"
                  :style {:width 210 :height 34}
                  :value (:status query-params)
                  :allowClear true
                  :on-change #(on-field :status %)}
     [antd/select-option {:value "0"} "正常"]
     [antd/select-option {:value "1"} "停用"]]]
   [:div {:style {:flexBasis "100%" :height 0}}]
   [search-field "创建时间" 300
    [range-picker {:placeholder #js ["开始日期" "结束日期"]
                   :style {:width 232 :height 34 :borderRadius 4}}]]
   [:div {:style {:display "flex" :gap 10 :alignItems "center" :width 168}}
    [antd/button {:type "primary"
                  :style {:height 34 :borderRadius 4 :background "#409eff"}
                  :icon (r/as-element [:> SearchOutlined])
                  :on-click on-search}
     "搜索"]
    [antd/button {:icon (r/as-element [:> ReloadOutlined])
                  :style {:height 34 :borderRadius 4}
                  :on-click on-reset}
     "重置"]]])

(defn search-form
  "搜索表单。props: :query-params :show-search? :on-field :on-search :on-reset"
  [{:keys [show-search?] :as props}]
  (let [form-ref (hooks/use-ref nil)
        [height set-height!] (hooks/use-state (if show-search? "auto" "0px"))]
    ;; Animate height on toggle
    (hooks/use-effect
     (fn []
       (if show-search?
         (when-let [el (.-current form-ref)]
           (set-height! "0px")
           (js/setTimeout
            (fn [] (set-height! (str (.-scrollHeight el) "px")))
            10)
           (js/setTimeout
            (fn [] (set-height! "auto"))
            320))
         (when-let [el (.-current form-ref)]
           (set-height! (str (.-scrollHeight el) "px"))
           (js/setTimeout
            (fn [] (set-height! "0px"))
            10))))
     [show-search?])
    [:div {:ref form-ref
           :style {:overflow "hidden"
                   :height height
                   :opacity (if show-search? 1 0)
                   :transition "height 0.3s ease, opacity 0.3s ease"}}
     [:div {:style {:background "var(--app-bg)" :padding "8px 22px 4px 22px"}}
      [search-fields props]]]))

(defn- column-toggle-dropdown [columns on-toggle-column]
  [antd/tooltip {:title "显隐列"}
   [antd/dropdown {:menu {:items (clj->js
                                  (map (fn [[key {:keys [label visible?]}]]
                                         {:key (name key)
                                          :label (r/as-element
                                                  [:div {:style {:display "flex" :justifyContent "space-between"
                                                                 :alignItems "center" :width 120}}
                                                   [:span label]
                                                   [antd/switch {:size "small" :checked visible?}]])})
                                       columns))
                          :onClick (fn [e]
                                     (on-toggle-column (keyword (.-key e))))}
                   :trigger #js ["click"]}
    [antd/button {:shape "circle"
                  :icon (r/as-element [:> AppstoreOutlined])
                  :style {:width 38 :height 38 :borderColor "var(--app-border)" :color "var(--app-text-regular)"}}]]])

(defn- toolbar-left [{:keys [selected-empty? on-add on-edit-selected on-batch-delete on-import on-export]}]
  [:div {:style {:display "flex" :gap 8}}
   [antd/button {:type "primary" :ghost true
                 :style {:height 34 :borderRadius 4 :color "#409eff" :borderColor "#a0cfff" :background "#ecf5ff"}
                 :icon (r/as-element [:> PlusOutlined])
                 :on-click on-add}
    "新增"]
   [antd/button {:ghost true
                 :style {:height 34 :borderRadius 4 :color "#67c23a" :borderColor "#b3e19d" :background "#f0f9eb"}
                 :icon (r/as-element [:> EditOutlined])
                 :disabled selected-empty?
                 :on-click on-edit-selected}
    "修改"]
   [antd/button {:danger true :ghost true
                 :style {:height 34 :borderRadius 4 :color "#f56c6c" :borderColor "#fab6b6" :background "#fef0f0"}
                 :icon (r/as-element [:> DeleteOutlined])
                 :disabled selected-empty?
                 :on-click on-batch-delete}
    "删除"]
   [antd/button {:ghost true
                 :style {:height 34 :borderRadius 4 :color "var(--app-text-secondary)" :borderColor "#d3d4d6" :background "#f4f4f5"}
                 :icon (r/as-element [:> UploadOutlined])
                 :on-click on-import}
    "导入"]
   [antd/button {:ghost true
                 :style {:height 34 :borderRadius 4 :color "#e6a23c" :borderColor "#f3d19e" :background "#fdf6ec"}
                 :icon (r/as-element [:> DownloadOutlined])
                 :on-click on-export}
    "导出"]])

(defn toolbar
  "工具栏。props: :show-search? :columns :selected-empty? :on-add :on-edit-selected
   :on-batch-delete :on-import :on-export :on-toggle-search :on-refresh :on-toggle-column"
  [{:keys [show-search? columns] :as props}]
  [:div {:style {:display "flex" :justifyContent "space-between" :alignItems "center"
                 :padding "8px 22px 8px 22px" :background "var(--app-bg)"}}
   [toolbar-left props]
   [:div {:style {:display "flex" :gap 12}}
    [antd/tooltip {:title "显示搜索"}
     [antd/button {:shape "circle"
                   :icon (r/as-element [:> SearchOutlined])
                   :style {:width 38 :height 38 :borderColor "var(--app-border)" :color "var(--app-text-regular)"
                           :background (if show-search? "var(--app-bg)" "var(--app-fill)")}
                   :on-click (:on-toggle-search props)}]]
    [antd/tooltip {:title "刷新"}
     [antd/button {:shape "circle"
                   :icon (r/as-element [:> ReloadOutlined])
                   :style {:width 38 :height 38 :borderColor "var(--app-border)" :color "var(--app-text-regular)"}
                   :on-click (:on-refresh props)}]]
    [column-toggle-dropdown columns (:on-toggle-column props)]]])
