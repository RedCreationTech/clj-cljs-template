(ns com.ruoyi.frontend.pages.login-log
  "登录日志页面。"
  (:require
   ["@ant-design/icons" :refer [DeleteOutlined DownloadOutlined LockOutlined ReloadOutlined
                                SearchOutlined]]
   ["antd" :refer [DatePicker]]
   [clojure.string :as str]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.components.page-search :as page-search]
   [com.ruoyi.frontend.components.page-toolbar :as page-toolbar]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

(def range-picker (r/adapt-react-class (.-RangePicker DatePicker)))

(defn- status-tag [status]
  [antd/tag {:color (if (= (str status) "0") "blue" "red")}
   (if (= (str status) "0") "成功" "失败")])

(defn- login-log-columns []
  #js [#js {:title "访问编号" :dataIndex "info_id" :key "info_id" :width 100}
       #js {:title "用户名称" :dataIndex "user_name" :key "user_name" :width 120
            :sorter true}
       #js {:title "登录地址" :dataIndex "ipaddr" :key "ipaddr" :width 160}
       #js {:title "登录地点" :dataIndex "login_location" :key "login_location" :width 160}
       #js {:title "浏览器" :dataIndex "browser" :key "browser" :width 140}
       #js {:title "操作系统" :dataIndex "os" :key "os" :width 150}
       #js {:title "登录状态" :dataIndex "status" :key "status" :width 110
            :render (fn [v _] (r/as-element [status-tag v]))}
       #js {:title "操作信息" :dataIndex "msg" :key "msg" :width 160}
       #js {:title "登录日期" :dataIndex "login_time" :key "login_time" :width 180
            :sorter true}])

(defn- query-params [ipaddr username status date-range]
  (cond-> {:ipaddr ipaddr :user_name username :status status}
    (first date-range) (assoc :begin_time (first date-range))
    (second date-range) (assoc :end_time (second date-range))))

(defn- search-text-input [label value set-value!]
  [page-search/search-item
   label
   [antd/input {:placeholder (str "请输入" label)
                :style page-search/input-style
                :value value
                :onChange #(set-value! (-> % .-target .-value))}]])

(defn- login-log-search-form [{:keys [ipaddr set-ipaddr! username set-username! status set-status!
                                      date-range set-date-range! run-search]}]
  [page-search/page-search {:visible? true}
   [page-search/search-row
    [search-text-input "登录地址" ipaddr set-ipaddr!]
    [search-text-input "用户名称" username set-username!]
    [page-search/search-item
     "状态"
     [antd/select {:placeholder "登录状态"
                   :style page-search/select-style
                   :allowClear true
                   :value status
                   :onChange set-status!}
      [antd/select-option {:value "0"} "成功"]
      [antd/select-option {:value "1"} "失败"]]]
    [:div {:style {:flexBasis "100%" :height 0}}]
    [page-search/search-item
     "登录时间"
     [range-picker {:placeholder #js ["开始日期" "结束日期"]
                    :style {:width 260 :height 34}
                    :onChange (fn [_ date-strings]
                                (set-date-range! (js->clj date-strings)))}]]
    [page-search/search-actions
     [page-toolbar/search-button {:icon (r/as-element [:> SearchOutlined])
                                  :on-click run-search}]
     [page-toolbar/reset-button {:icon (r/as-element [:> ReloadOutlined])
                                 :on-click #(do (set-ipaddr! "")
                                                (set-username! "")
                                                (set-status! nil)
                                                (set-date-range! [])
                                                (rf/dispatch [:login-logs/fetch {}]))}]]]])

(defn- login-log-toolbar [{:keys [items selected-ids set-selected-ids! run-search]}]
  (let [selected-id-string #(str/join "," selected-ids)]
    [page-toolbar/page-toolbar
     {:left [page-toolbar/toolbar-left
             [antd/popconfirm {:title "确认删除选中的登录日志？"
                               :onConfirm #(when (seq selected-ids)
                                             (rf/dispatch [:login-logs/delete (selected-id-string)])
                                             (set-selected-ids! []))}
              [page-toolbar/toolbar-button {:kind :delete
                                            :icon (r/as-element [:> DeleteOutlined])
                                            :disabled? (empty? selected-ids)
                                            :label "删除"}]]
             [antd/popconfirm {:title "确认清空所有登录日志？"
                               :onConfirm #(rf/dispatch [:login-logs/clear])}
              [page-toolbar/toolbar-button {:kind :delete
                                            :icon (r/as-element [:> DeleteOutlined])
                                            :label "清空"}]]
             [page-toolbar/toolbar-button {:kind :add
                                           :icon (r/as-element [:> LockOutlined])
                                           :disabled? (empty? selected-ids)
                                           :on-click #(when-let [row (some (fn [item]
                                                                             (when (= (:info_id item) (first selected-ids)) item))
                                                                           items)]
                                                        (rf/dispatch [:login-logs/unlock (:user_name row)]))
                                           :label "解锁"}]
             [page-toolbar/toolbar-button {:kind :export
                                           :icon (r/as-element [:> DownloadOutlined])
                                           :on-click #(rf/dispatch [:login-logs/export])
                                           :label "导出"}]]
      :right [page-toolbar/toolbar-right
              [page-toolbar/round-tool-button {:title "搜索"
                                               :icon (r/as-element [:> SearchOutlined])
                                               :on-click run-search}]
              [page-toolbar/round-tool-button {:title "刷新"
                                               :icon (r/as-element [:> ReloadOutlined])
                                               :on-click #(rf/dispatch [:login-logs/fetch {}])}]]}]))

(defn- login-log-table [{:keys [loading? items total selected-ids set-selected-ids!]}]
  [antd/table {:scroll #js {:x "max-content"}
               :rowKey "info_id"
               :rowSelection #js {:selectedRowKeys (clj->js selected-ids)
                                  :onChange (fn [keys _]
                                              (set-selected-ids! (js->clj keys)))}
               :loading loading?
               :columns (login-log-columns)
               :dataSource (clj->js items)
               :pagination {:pageSize 10
                            :total total
                            :showSizeChanger true
                            :showTotal (fn [t] (str "共 " t " 条"))}}])

(defn login-log-page []
  (hooks/use-effect (fn []
                      (rf/dispatch [:login-logs/fetch {}])
                      js/undefined)
                    [])
  (let [items @(rf/subscribe [:login-logs/items])
        total @(rf/subscribe [:login-logs/total])
        loading? @(rf/subscribe [:login-logs/loading?])
        [ipaddr set-ipaddr!] (hooks/use-state "")
        [username set-username!] (hooks/use-state "")
        [status set-status!] (hooks/use-state nil)
        [date-range set-date-range!] (hooks/use-state [])
        [selected-ids set-selected-ids!] (hooks/use-state [])
        run-search #(rf/dispatch [:login-logs/fetch (query-params ipaddr username status date-range)])]
    [:div
     [login-log-search-form {:ipaddr ipaddr
                             :set-ipaddr! set-ipaddr!
                             :username username
                             :set-username! set-username!
                             :status status
                             :set-status! set-status!
                             :date-range date-range
                             :set-date-range! set-date-range!
                             :run-search run-search}]
     [login-log-toolbar {:items items
                         :selected-ids selected-ids
                         :set-selected-ids! set-selected-ids!
                         :run-search run-search}]
     [login-log-table {:loading? loading?
                       :items items
                       :total total
                       :selected-ids selected-ids
                       :set-selected-ids! set-selected-ids!}]]))
