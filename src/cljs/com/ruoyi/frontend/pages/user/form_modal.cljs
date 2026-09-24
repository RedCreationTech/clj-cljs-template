(ns com.ruoyi.frontend.pages.user.form-modal
  "用户新增/编辑弹窗（presentational：数据与回调由 user-page 传入，内部保留 Form hook）。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.components.dept-tree-select :refer [dept-tree-select]]
   [reagent.hooks :as hooks]))

(defn- form-basic-items [form editing]
  [[antd/form-item {:style {:marginBottom 0} :label "用户昵称" :name "nick_name"
                    :rules [{:required true :message "请输入用户昵称"}]}
    [antd/input {:placeholder "请输入用户昵称" :style {:height 42 :borderRadius 4}}]]
   [antd/form-item {:style {:marginBottom 0} :label "归属部门"}
    [dept-tree-select {:placeholder "请选择归属部门" :allow-clear? true :style {:height 42}
                       :value (.getFieldValue form "dept_id")
                       :on-change (fn [v] (.setFieldsValue form #js {"dept_id" v}))}]]
   [antd/form-item {:style {:marginBottom 0} :label "手机号码" :name "phonenumber"}
    [antd/input {:placeholder "请输入手机号码" :style {:height 42 :borderRadius 4}}]]
   [antd/form-item {:style {:marginBottom 0} :label "邮箱" :name "email"}
    [antd/input {:placeholder "请输入邮箱" :style {:height 42 :borderRadius 4}}]]
   (when-not editing
     [antd/form-item {:style {:marginBottom 0} :label "用户名称" :name "user_name"
                      :rules [{:required true :message "请输入用户名称"}]}
      [antd/input {:placeholder "请输入用户名称" :style {:height 42 :borderRadius 4}}]])
   (when-not editing
     [antd/form-item {:style {:marginBottom 0} :label "用户密码" :name "password"
                      :rules [{:required true :message "请输入用户密码"}]}
      [antd/password {:placeholder "请输入用户密码" :style {:height 42 :borderRadius 4}}]])])

(defn- form-extra-items [post-options role-options]
  [[antd/form-item {:style {:marginBottom 0} :label "用户性别" :name "sex"}
    [antd/select {:placeholder "请选择性别" :allowClear true :style {:height 42}}
     [antd/select-option {:value "0"} "男"]
     [antd/select-option {:value "1"} "女"]
     [antd/select-option {:value "2"} "未知"]]]
   [antd/form-item {:style {:marginBottom 0} :label "状态" :name "status"}
    [antd/radio-group
     [antd/radio {:value "0"} "正常"]
     [antd/radio {:value "1"} "停用"]]]
   [antd/form-item {:style {:marginBottom 0} :label "岗位" :name "posts"}
    [antd/select {:mode "multiple" :placeholder "请选择岗位" :allowClear true :style {:minHeight 42}}
     (for [post post-options]
       ^{:key (:post_id post)} [antd/select-option {:value (:post_id post)} (:post_name post)])]]
   [antd/form-item {:style {:marginBottom 0} :label "角色" :name "roles"}
    [antd/select {:mode "multiple" :placeholder "请选择角色" :allowClear true :style {:minHeight 42}}
     (for [role role-options]
       ^{:key (:role_id role)} [antd/select-option {:value (:role_id role)} (:role_name role)])]]
   [antd/form-item {:style {:gridColumn "1 / -1" :marginBottom 0} :label "备注" :name "remark"}
    [antd/text-area {:placeholder "请输入内容"
                     :style {:height 68 :borderRadius 4 :resize "vertical"}}]]])

(defn- form-grid [form editing post-options role-options]
  (into [:div {:style {:display "grid" :gridTemplateColumns "1fr 1fr" :columnGap 24 :rowGap 28}}]
        (concat (form-basic-items form editing)
                (form-extra-items post-options role-options))))

(defn- modal-header [editing on-close]
  [:div {:style {:display "flex" :justifyContent "space-between" :alignItems "center"
                 :marginBottom 22}}
   [:h3 {:style {:margin 0 :fontSize 22 :fontWeight 500 :color "var(--app-text-primary)"}} (if editing "修改用户" "添加用户")]
   [antd/button {:type "text"
                 :style {:fontSize 24 :color "var(--app-text-secondary)" :width 32 :height 32}
                 :on-click on-close} "×"]])

(defn- modal-footer [on-close]
  [:div {:style {:display "flex" :justifyContent "flex-end" :gap 12 :marginTop 46}}
   [antd/button {:type "primary" :htmlType "submit"
                 :style {:width 86 :height 42 :fontSize 16 :borderRadius 4 :background "#409eff"}}
    "确定"]
   [antd/button {:on-click on-close
                 :style {:width 86 :height 42 :fontSize 16 :borderRadius 4}}
    "取消"]])

(defn- initial-values [form-data]
  (let [base (merge {:status "0" :password "123456" :roles [] :posts []} form-data)]
    (clj->js (assoc base
                    :roles (mapv :role_id (:roles form-data))
                    :posts (mapv :post_id (:posts form-data))))))

(defn form-modal
  "用户新增/编辑弹窗。props: :visible? :editing :form-data :role-options :post-options
   :on-close :on-submit :on-fetch-options"
  [{:keys [visible? editing form-data role-options post-options on-close on-submit on-fetch-options]}]
  (let [[form] (antd/form-use-form)]
    (hooks/use-effect
     (fn []
       (when visible?
         (.resetFields form)
         (on-fetch-options)
         (.setFieldsValue form (initial-values form-data)))
       js/undefined)
     [visible? form-data])
    (when visible?
      [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0
                     :background "rgba(0,0,0,0.45)" :zIndex 1050
                     :display "flex" :justifyContent "center" :alignItems "flex-start"}}
       [:div {:style {:background "var(--ant-color-bg-container, #fff)" :padding "24px 24px 26px" :borderRadius 4 :width 700 :marginTop 76
                      :maxHeight "calc(100vh - 96px)" :overflow "auto" :boxShadow "0 2px 12px rgba(0,0,0,0.18)"}}
        [modal-header editing on-close]
        [antd/form {:form form
                    :layout "horizontal"
                    :labelCol {:style {:width 86}}
                    :wrapperCol {:style {:flex 1}}
                    :preserve false
                    :onFinish (fn [values]
                                (on-submit (js->clj values :keywordize-keys true)))
                    :initialValues (initial-values form-data)}
         [form-grid form editing post-options role-options]
         [modal-footer on-close]]]])))
