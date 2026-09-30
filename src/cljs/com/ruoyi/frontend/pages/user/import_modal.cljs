(ns com.ruoyi.frontend.pages.user.import-modal
  "用户导入弹窗:下载 CSV 模板 → 选择文件 → 提交。
   后端逐行建用户(默认密码 123456),返回成功 / 失败条数,失败原因在 details 里。"
  (:require
   ["@ant-design/icons" :refer [DownloadOutlined UploadOutlined]]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.impexp :as impexp-api]
   [com.ruoyi.frontend.i18n :as i18n]
   [re-frame.core :as rf]
   [reagent.core :as r]))

(defn- header [on-close]
  [:div {:style {:display "flex" :justifyContent "space-between" :alignItems "center"
                 :marginBottom 18}}
   [:h3 {:style {:margin 0 :fontSize 18 :fontWeight 500 :color "var(--app-text-primary)"}}
    (i18n/tr "用户导入")]
   [antd/button {:type "text"
                 :style {:fontSize 22 :color "var(--app-text-secondary)" :width 32 :height 32}
                 :on-click on-close}
    "×"]])

(defn- template-row []
  [:div {:style {:display "flex" :alignItems "center" :gap 12 :marginBottom 16
                 :color "var(--app-text-regular)" :fontSize 14}}
   [:span (i18n/tr "请选择 CSV 格式的文件,首行是表头:user_name / nick_name / email / phonenumber / sex / status / dept_id / remark")]
   [antd/button {:type "link" :size "small"
                 :icon (r/as-element [:> DownloadOutlined])
                 :on-click #(impexp-api/download-user-import-template)}
    (i18n/tr "下载模板")]])

(defn- file-row [file]
  [:div {:style {:display "flex" :alignItems "center" :gap 12}}
   [antd/upload {:showUploadList false
                 :accept ".csv"
                 :beforeUpload (fn [^js picked]
                                 (rf/dispatch [:users/set-import-file picked])
                                 ;; 返回 false 阻止 antd 自己发请求,上传交给「确定」按钮
                                 false)}
    [antd/button {:icon (r/as-element [:> UploadOutlined])} (i18n/tr "选择文件")]]
   [:span {:style {:color (if file "var(--app-link)" "var(--app-text-placeholder)"), :fontSize 14}}
    (if file (.-name file) (i18n/tr "尚未选择文件"))]])

(defn- footer [loading? file on-close]
  [:div {:style {:display "flex" :justifyContent "flex-end" :gap 10 :marginTop 24}}
   [antd/button {:on-click on-close} (i18n/tr "取消")]
   [antd/button {:type "primary" :loading loading? :disabled (nil? file)
                 :on-click #(rf/dispatch [:users/import])}
    (i18n/tr "确定")]])

(defn import-modal []
  (let [visible? @(rf/subscribe [:users/import-visible?])
        loading? @(rf/subscribe [:users/import-loading?])
        file @(rf/subscribe [:users/import-file])
        on-close #(rf/dispatch [:users/close-import])]
    (when visible?
      [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0
                     :background "rgba(0,0,0,0.45)" :zIndex 1055
                     :display "flex" :justifyContent "center" :alignItems "flex-start"}}
       [:div {:id "user-import-modal"
              :style {:background "var(--ant-color-bg-container, #fff)" :padding "22px 24px 24px"
                      :borderRadius 4 :width 620 :marginTop 96
                      :boxShadow "0 2px 12px rgba(0,0,0,0.18)"}}
        [header on-close]
        [template-row]
        [file-row file]
        [footer loading? file on-close]]])))
