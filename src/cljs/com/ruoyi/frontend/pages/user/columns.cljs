(ns com.ruoyi.frontend.pages.user.columns
  "用户表格列定义（presentational，接收列显隐配置与回调）。"
  (:require
   ["@ant-design/icons" :refer [DeleteOutlined EditOutlined]]
   [com.ruoyi.frontend.antd :as antd]
   [reagent.core :as r]))

(defn- protected?
  "内置管理员（user_id 1 或 user_name admin）不可删除。"
  [record]
  (or (= 1 (.-user_id ^js record))
      (= "admin" (.-user_name ^js record))))

(defn- render-user-name [on-view v record]
  (r/as-element
   [:a {:style {:cursor "pointer" :color "#409eff"}
        :on-click #(on-view (.-user_id ^js record))}
    v]))

(defn- render-status [on-change-status v record]
  (r/as-element
   [antd/switch {:checked (= v "0")
                 :on-change (fn [checked?]
                              (on-change-status (.-user_id ^js record)
                                                (if checked? "0" "1")))}]))

(defn- render-more-menu [on-reset-password on-auth-role record]
  [antd/dropdown {:menu {:items (clj->js [{:key "resetPwd" :label (r/as-element [:span "重置密码"])}
                                          {:key "authRole" :label (r/as-element [:span "分配角色"])}])
                         :onClick (fn [e]
                                    (case (.-key e)
                                      "resetPwd" (on-reset-password (.-user_id ^js record))
                                      "authRole" (on-auth-role (.-user_id ^js record))
                                      nil))}}
   [antd/button {:type "link" :size "small"
                 :style {:color "#409eff"}}
    "更多"]])

(defn- render-actions [{:keys [on-edit on-delete on-reset-password on-auth-role]} record]
  (r/as-element
   [antd/space
    [antd/button {:type "link" :size "small"
                  :style {:color "#409eff"}
                  :icon (r/as-element [:> EditOutlined])
                  :on-click #(on-edit (.-user_id ^js record))}
     "修改"]
    [antd/button {:type "link" :size "small"
                  :disabled (protected? record)
                  :style {:color (if (protected? record) "#c0c4cc" "#409eff")}
                  :icon (r/as-element [:> DeleteOutlined])
                  :on-click #(on-delete (.-user_id ^js record))}
     "删除"]
    [render-more-menu on-reset-password on-auth-role record]]))

(defn user-columns
  "返回 antd Table 所需 columns JS 数组。props:
   :columns-config 列显隐配置；:on-view :on-change-status :on-edit :on-delete
   :on-reset-password :on-auth-role 交互回调。"
  [{:keys [columns-config] :as props}]
  (let [on-view (:on-view props)
        on-change-status (:on-change-status props)]
    (clj->js
     (filterv some?
              [(when (get-in columns-config [:user_id :visible?])
                 {:title "用户编号" :dataIndex "user_id" :key "user_id" :width 110 :align "center"})
               (when (get-in columns-config [:user_name :visible?])
                 {:title "用户名称" :dataIndex "user_name" :key "user_name"
                  :align "center"
                  :render (partial render-user-name on-view)})
               (when (get-in columns-config [:nick_name :visible?])
                 {:title "用户昵称" :dataIndex "nick_name" :key "nick_name" :align "center"})
               (when (get-in columns-config [:dept_name :visible?])
                 {:title "部门" :dataIndex "dept_name" :key "dept_name" :align "center"})
               (when (get-in columns-config [:phonenumber :visible?])
                 {:title "手机号码" :dataIndex "phonenumber" :key "phonenumber" :width 150 :align "center"})
               (when (get-in columns-config [:status :visible?])
                 {:title "状态" :dataIndex "status" :key "status" :width 110 :align "center"
                  :render (partial render-status on-change-status)})
               (when (get-in columns-config [:create_time :visible?])
                 {:title "创建时间" :dataIndex "create_time" :key "create_time" :width 210 :align "center"})
               {:title "操作" :key "action" :width 220 :align "center"
                :render #(render-actions props %)}]))))
