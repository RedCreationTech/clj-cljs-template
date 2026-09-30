(ns com.ruoyi.frontend.pages.user
  "用户管理页面 - 对齐 RuoYi-Vue 功能。

  本命名空间仅暴露 `user-page`（供路由以 [user/user-page] 引用）。
  `user-page` 是唯一的 stateful 组件：所有 re-frame 订阅与 dispatch 集中于此，
  搜索表单/工具栏/表格列/新增编辑弹窗/部门树等大块已拆到 pages.user.* 子命名空间，
  以显式 props（数据 + 回调）形式接收。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.pages.user.columns :as columns]
   [com.ruoyi.frontend.pages.user.dept-tree :as dept-tree]
   [com.ruoyi.frontend.pages.user.form-modal :as form-modal]
   [com.ruoyi.frontend.pages.user.import-modal :as import-modal]
   [com.ruoyi.frontend.pages.user.search :as search]
   [re-frame.core :as rf]
   [reagent.hooks :as hooks]))

;; ─── props 构建（订阅数据 + dispatch 回调集中在此，传给 presentational 子组件）──

(defn- search-props []
  {:query-params @(rf/subscribe [:users/query-params])
   :show-search? @(rf/subscribe [:users/show-search?])
   :on-field (fn [k v] (rf/dispatch [:users/update-query k v]))
   :on-search #(rf/dispatch [:users/search])
   :on-reset #(rf/dispatch [:users/reset-query])})

(defn- toolbar-props []
  {:show-search? @(rf/subscribe [:users/show-search?])
   :columns @(rf/subscribe [:users/columns])
   :selected-empty? @(rf/subscribe [:users/selected-empty?])
   :on-add #(rf/dispatch [:users/open-add])
   :on-edit-selected #(rf/dispatch [:users/open-edit-selected])
   :on-batch-delete #(rf/dispatch [:users/batch-delete])
   :on-import #(rf/dispatch [:users/open-import])
   :on-export #(rf/dispatch [:users/export])
   :on-toggle-search #(rf/dispatch [:users/toggle-search])
   :on-refresh #(rf/dispatch [:users/fetch-with-params])
   :on-toggle-column #(rf/dispatch [:users/toggle-column %])})

(defn- columns-props []
  {:columns-config @(rf/subscribe [:users/columns])
   :on-view #(rf/dispatch [:users/view-detail %])
   :on-change-status (fn [id s] (rf/dispatch [:users/change-status id s]))
   :on-edit #(rf/dispatch [:users/open-edit %])
   :on-delete #(rf/dispatch [:users/delete %])
   :on-reset-password #(rf/dispatch [:users/reset-password %])
   :on-auth-role #(rf/dispatch [:users/auth-role %])})

(defn- form-modal-props []
  {:visible? @(rf/subscribe [:users/modal-visible?])
   :editing @(rf/subscribe [:users/editing])
   :form-data @(rf/subscribe [:users/form-data])
   :role-options @(rf/subscribe [:users/role-options])
   :post-options @(rf/subscribe [:users/post-options])
   :on-close #(rf/dispatch [:users/close-modal])
   :on-submit #(rf/dispatch [:users/submit %])
   :on-fetch-options #(rf/dispatch [:users/fetch-options])})

(defn- dept-tree-props []
  {:dept-items @(rf/subscribe [:depts/tree])
   :selected-dept-id @(rf/subscribe [:users/selected-dept-id])
   :on-select (fn [id]
                (rf/dispatch [:users/select-dept id])
                (rf/dispatch [:users/fetch {:dept_id id}]))
   :on-reload #(rf/dispatch [:depts/fetch {}])})

;; ─── 自定义弹窗（替代 antd/modal，避免 antd 6 + Reagent 兼容问题）──

(defn- detail-drawer []
  (let [visible? @(rf/subscribe [:users/detail-visible?])
        user @(rf/subscribe [:users/detail-data])]
    [antd/drawer {:title "用户详情"
                  :open visible?
                  :size "large"
                  :onClose #(rf/dispatch [:users/close-detail])}
     (when user
       [:div {:style {:padding "0 16px"}}
        [antd/descriptions {:column 1 :bordered true :size "small"}
         [antd/descriptions-item {:label "用户编号"} (:user_id user)]
         [antd/descriptions-item {:label "用户名称"} (:user_name user)]
         [antd/descriptions-item {:label "用户昵称"} (:nick_name user)]
         [antd/descriptions-item {:label "部门"} (get-in user [:dept :dept_name] "-")]
         [antd/descriptions-item {:label "手机号码"} (:phonenumber user "-")]
         [antd/descriptions-item {:label "邮箱"} (:email user "-")]
         [antd/descriptions-item {:label "性别"} (case (:sex user "0") "0" "男" "1" "女" "-")]
         [antd/descriptions-item {:label "状态"}
          [antd/tag {:color (if (= (:status user "0") "0") "green" "red")}
           (if (= (:status user "0") "0") "正常" "停用")]]
         [antd/descriptions-item {:label "创建时间"} (:create_time user "-")]
         [antd/descriptions-item {:label "备注"} (:remark user "-")]]
        ;; 角色信息
        (when (seq (:roles user))
          [:div {:style {:marginTop 16}}
           [:div {:style {:fontWeight 500 :marginBottom 8}} "角色信息"]
           [:div {:style {:display "flex" :flexWrap "wrap" :gap 4}}
            (for [role (:roles user)]
              ^{:key (:role_id role)}
              [antd/tag {:color "blue"} (:role_name role)])]])
        ;; 岗位信息
        (when (seq (:posts user))
          [:div {:style {:marginTop 16}}
           [:div {:style {:fontWeight 500 :marginBottom 8}} "岗位信息"]
           [:div {:style {:display "flex" :flexWrap "wrap" :gap 4}}
            (for [post (:posts user)]
              ^{:key (:post_id post)}
              [antd/tag {:color "cyan"} (:post_name post)])]])])]))

(defn- reset-password-modal []
  (let [visible? @(rf/subscribe [:users/reset-pwd-visible?])
        username @(rf/subscribe [:users/reset-pwd-username])
        [form] (antd/form-use-form)]
    (hooks/use-effect
     (fn []
       (when visible?
         (.resetFields form))
       js/undefined)
     [visible?])
    (when visible?
      [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0
                     :background "rgba(0,0,0,0.45)" :zIndex 1060
                     :display "flex" :justifyContent "center" :alignItems "center"}}
       [:div {:style {:background "var(--ant-color-bg-container, #fff)" :padding 24 :borderRadius 8 :width 400
                      :boxShadow "0 6px 16px rgba(0,0,0,0.08)"}}
        [:h3 {:style {:margin "0 0 16px 0" :fontSize 16}} (str "重置密码 - " username)]
        [antd/form {:form form
                    :layout "vertical"
                    :preserve false
                    :onFinish (fn [values]
                                (rf/dispatch [:users/submit-reset-password (js->clj values :keywordize-keys true)]))
                    :initialValues #js {}}
         [antd/form-item {:label "新密码" :name "password"
                          :rules [{:required true :message "请输入新密码"}]}
          [antd/password {:placeholder "请输入新密码"}]]
         [:div {:style {:display "flex" :justifyContent "flex-end" :gap 8 :marginTop 16}}
          [antd/button {:on-click #(rf/dispatch [:users/close-reset-password])} "取消"]
          [antd/button {:type "primary" :htmlType "submit"} "确定"]]]]])))

(defn- display-users
  "返回真实接口数据，避免演示数据覆盖创建时间。"
  [items]
  items)

(defn- auth-role-modal []
  (let [visible? @(rf/subscribe [:users/auth-role-visible?])
        user @(rf/subscribe [:users/auth-role-user])
        role-options @(rf/subscribe [:users/role-options])
        selected-role-ids @(rf/subscribe [:users/auth-role-ids])]
    (when visible?
      [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0
                     :background "rgba(0,0,0,0.45)" :zIndex 1060
                     :display "flex" :justifyContent "center" :alignItems "center"}}
       [:div {:style {:background "var(--ant-color-bg-container, #fff)" :padding 24 :borderRadius 4 :width 520
                      :boxShadow "0 2px 12px rgba(0,0,0,0.18)"}}
        [:div {:style {:display "flex" :justifyContent "space-between" :alignItems "center"
                       :marginBottom 18}}
         [:h3 {:style {:margin 0 :fontSize 18 :fontWeight 500 :color "var(--app-text-primary)"}}
          (str "分配角色 - " (or (:user_name user) ""))]
         [antd/button {:type "text"
                       :style {:fontSize 22 :color "var(--app-text-secondary)" :width 32 :height 32}
                       :on-click #(rf/dispatch [:users/close-auth-role])}
          "×"]]
        [:div {:style {:display "flex" :flexDirection "column" :gap 10}}
         [:span {:style {:fontSize 14 :color "var(--app-text-regular)"}} "角色"]
         [antd/select {:mode "multiple"
                       :placeholder "请选择角色"
                       :allowClear true
                       :value selected-role-ids
                       :style {:width "100%" :minHeight 40}
                       :on-change #(rf/dispatch [:users/set-auth-role-selection (js->clj %)])}
          (for [role role-options]
            ^{:key (:role_id role)}
            [antd/select-option {:value (:role_id role)} (:role_name role)])]]
        [:div {:style {:display "flex" :justifyContent "flex-end" :gap 10 :marginTop 24}}
         [antd/button {:on-click #(rf/dispatch [:users/close-auth-role])} "取消"]
         [antd/button {:type "primary"
                       :style {:background "#409eff"}
                       :on-click #(rf/dispatch [:users/submit-auth-role])}
          "确定"]]]])))

(defn- pagination-bar [total page page-size]
  [:div {:style {:display "flex" :justifyContent "flex-end" :alignItems "center"
                 :gap 16 :height 68 :padding "0 24px" :background "var(--app-bg)"
                 :color "var(--app-text-regular)" :fontSize 16}}
   [:span (str "共 " total " 条")]
   [antd/select {:value page-size
                 :style {:width 142}
                 :on-change #(rf/dispatch [:users/change-page 1 %])}
    [antd/select-option {:value 10} "10条/页"]
    [antd/select-option {:value 20} "20条/页"]
    [antd/select-option {:value 30} "30条/页"]]
   [antd/button {:disabled (<= page 1)
                 :style {:width 44 :height 40 :borderRadius 4}
                 :on-click #(rf/dispatch [:users/change-page (max 1 (dec page)) page-size])}
    "‹"]
   [antd/button {:type "primary"
                 :style {:width 44 :height 40 :borderRadius 4 :background "#409eff"}}
    (str page)]
   [antd/button {:disabled (>= (* page page-size) total)
                 :style {:width 44 :height 40 :borderRadius 4}
                 :on-click #(rf/dispatch [:users/change-page (inc page) page-size])}
    "›"]
   [:span "前往"]
   [antd/input {:value page
                :style {:width 68 :height 40 :textAlign "center" :borderRadius 4}
                :on-change (fn [e]
                             (let [v (js/parseInt (.. e -target -value) 10)]
                               (when (pos? v)
                                 (rf/dispatch [:users/change-page v page-size]))))}]
   [:span "页"]])

(defn user-page []
  (hooks/use-effect
   (fn []
     (rf/dispatch [:depts/fetch {}])
     (rf/dispatch [:users/fetch {}])
     js/undefined)
   [])
  (let [items (display-users @(rf/subscribe [:users/items]))
        total (max @(rf/subscribe [:users/total]) (count items))
        loading? @(rf/subscribe [:users/loading?])
        selected-ids @(rf/subscribe [:users/selected-ids])
        page @(rf/subscribe [:users/page])
        page-size @(rf/subscribe [:users/page-size])]
    [:div {:style {:display "flex" :height "100%" :alignItems "stretch" :background "var(--app-bg)"}}
     ;; 左侧部门树
     [dept-tree/dept-tree-sidebar (dept-tree-props)]
     ;; 右侧内容区
     [:div {:style {:flex 1 :minWidth 0 :overflow "auto" :background "var(--app-bg)"}}
      [search/search-form (search-props)]
      [search/toolbar (toolbar-props)]
      [:div {:style {:padding "0 24px"}}
       [antd/table {:scroll #js {:x "max-content"} :rowKey "user_id"
                    :columns (columns/user-columns (columns-props))
                    :dataSource (clj->js items)
                    :loading loading?
                    :rowSelection {:selectedRowKeys (clj->js selected-ids)
                                   :onChange (fn [keys]
                                               (rf/dispatch [:users/set-selected (js->clj keys)]))}
                    :pagination false}]]
      [pagination-bar total page page-size]
      [form-modal/form-modal (form-modal-props)]
      [import-modal/import-modal]
      [reset-password-modal]
      [auth-role-modal]
      [detail-drawer]]]))
