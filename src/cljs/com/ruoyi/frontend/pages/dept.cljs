(ns com.ruoyi.frontend.pages.dept
  "部门管理页面 — 树形表格、CRUD。"
  (:require
   ["@ant-design/icons" :refer [CheckOutlined ColumnHeightOutlined DeleteOutlined EditOutlined
                                PlusOutlined ReloadOutlined SearchOutlined]]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.components.page-search :as page-search]
   [com.ruoyi.frontend.components.page-toolbar :as page-toolbar]
   [com.ruoyi.frontend.perm :as perm]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

;; ─── 辅助函数 ──────────────────────────────────────────────────────

(defn- build-dept-tree
  "将平铺部门列表转换为树形结构。"
  [items parent-id]
  (->> items
       (filter #(= parent-id (:parent_id %)))
       (mapv (fn [d]
               (let [children (build-dept-tree items (:dept_id d))]
                 (if (seq children)
                   (assoc d :children children)
                   d))))))

(defn- dept-tree-options
  "部门树 → antd TreeSelect 的 treeData（上级部门选择器用）。"
  [nodes]
  (mapv (fn [d]
          (let [node {:title (:dept_name d) :value (:dept_id d) :key (:dept_id d)}]
            (if-let [children (seq (:children d))]
              (assoc node :children (dept-tree-options children))
              node)))
        nodes))

(defn- expandable-dept-ids
  "树中所有有子节点的部门 id，用于展开状态。"
  [nodes]
  (->> nodes
       (filter #(seq (:children %)))
       (mapcat #(cons (:dept_id %) (expandable-dept-ids (:children %))))
       vec))

;; ─── 工具栏 ────────────────────────────────────────────────────────

(defn- search-bar []
  (let [[dept-name set-dept-name!] (hooks/use-state "")
        [status set-status!] (hooks/use-state nil)]
    [page-search/page-search {:visible? true}
     [page-search/search-row
      [page-search/search-item
       "部门名称"
       [antd/input {:placeholder "请输入部门名称"
                    :style page-search/input-style
                    :value dept-name
                    :onChange #(set-dept-name! (-> % .-target .-value))}]]
      [page-search/search-item
       "状态"
       [antd/select {:placeholder "部门状态"
                     :style page-search/select-style
                     :allowClear true
                     :value status
                     :onChange set-status!}
        [antd/select-option {:value "0"} "正常"]
        [antd/select-option {:value "1"} "停用"]]]
      [page-search/search-actions
       [page-toolbar/search-button {:icon (r/as-element [:> SearchOutlined])
                                    :on-click #(rf/dispatch [:depts/search {:dept_name dept-name :status status}])}]
       [page-toolbar/reset-button {:icon (r/as-element [:> ReloadOutlined])
                                   :on-click #(do (set-dept-name! "")
                                                  (set-status! nil)
                                                  (rf/dispatch [:depts/fetch {}]))}]]]]))

(defn- toolbar [{:keys [on-toggle-expands]}]
  [page-toolbar/page-toolbar
   {:left [page-toolbar/toolbar-left
           [page-toolbar/toolbar-button {:perm "system:dept:add"
                                         :kind :add
                                         :icon (r/as-element [:> PlusOutlined])
                                         :on-click #(rf/dispatch [:depts/open-modal])
                                         :label "新增"}]
           [page-toolbar/toolbar-button {:perm "system:dept:edit"
                                         :kind :export
                                         :icon (r/as-element [:> CheckOutlined])
                                         :label "保存排序"}]
           [page-toolbar/toolbar-button {:kind :import
                                         :icon (r/as-element [:> ColumnHeightOutlined])
                                         :on-click on-toggle-expands
                                         :label "展开/折叠"}]]
    :right [page-toolbar/toolbar-right
            [page-toolbar/round-tool-button {:title "搜索"
                                             :icon (r/as-element [:> SearchOutlined])
                                             :on-click #(rf/dispatch [:depts/search {}])}]
            [page-toolbar/round-tool-button {:title "刷新"
                                             :icon (r/as-element [:> ReloadOutlined])
                                             :on-click #(rf/dispatch [:depts/fetch {}])}]]}])

;; ─── 表格列 ──────────────────────────────────────────────────────

(defn- dept-columns []
  #js [#js {:title "部门名称" :dataIndex "dept_name" :key "dept_name" :width 200}
       #js {:title "排序" :dataIndex "order_num" :key "order_num" :width 80}
       #js {:title "负责人" :dataIndex "leader" :key "leader" :width 120}
       #js {:title "电话" :dataIndex "phone" :key "phone" :width 150}
       #js {:title "状态" :dataIndex "status" :key "status" :width 100
            :render (fn [v _]
                      (r/as-element
                       [antd/tag {:className "ruoyi-status-tag"}
                        (if (= v "0") "正常" "停用")]))}
       #js {:title "创建时间" :dataIndex "create_time" :key "create_time" :width 180}
       #js {:title "操作" :key "action" :width 220
            :render (fn [_ ^js record]
                      (r/as-element
                       [antd/space
                        [perm/when-allowed "system:dept:edit"
                         [antd/button {:type "link" :size "small"
                                       :icon (r/as-element [:> EditOutlined])
                                       :on-click #(rf/dispatch [:depts/edit (js->clj record :keywordize-keys true)])}
                          "修改"]]
                        [perm/when-allowed "system:dept:add"
                         [antd/button {:type "link" :size "small"
                                       :icon (r/as-element [:> PlusOutlined])
                                       :on-click #(rf/dispatch [:depts/open-modal {:parent_id (.-dept_id record)}])}
                          "新增"]]
                        [perm/when-allowed "system:dept:remove"
                         [antd/popconfirm {:title "确认删除该部门？"
                                           :onConfirm #(rf/dispatch [:depts/delete (.-dept_id record)])}
                          [antd/button {:type "link" :danger true :size "small"
                                        :icon (r/as-element [:> DeleteOutlined])}
                           "删除"]]]]))}])

;; ─── 编辑弹窗 ──────────────────────────────────────────────────────

(defn- edit-modal []
  (let [visible? @(rf/subscribe [:depts/modal-visible?])
        editing @(rf/subscribe [:depts/editing])
        form-data @(rf/subscribe [:depts/form-data])
        items @(rf/subscribe [:depts/items])
        [form] (antd/form-use-form)]
    (hooks/use-effect
     (fn []
       (when visible?
         (.setFieldsValue form (clj->js (merge {:order_num 0 :status "0"} form-data))))
       js/undefined)
     [visible? form-data])
    [antd/modal {:title (if editing "修改部门" "新增部门")
                 :open visible?
                 :onOk #(.submit form)
                 :onCancel #(rf/dispatch [:depts/close-modal])
                 :destroyOnHidden true}
     [antd/form {:form form
                 :labelCol {:span 6}
                 :wrapperCol {:span 16}
                 :preserve false
                 :onFinish (fn [values]
                             (rf/dispatch [:depts/submit (js->clj values :keywordize-keys true)]))
                 :initialValues (clj->js (merge {:order_num 0 :status "0"} form-data))}
      [antd/form-item {:label "上级部门" :name "parent_id"}
       ;; 用原生 TreeSelect：Form.Item 只会给 antd 控件注入 value/onChange/id
       [antd/tree-select {:style {:width "100%"}
                          :placeholder "选择上级部门（空为顶级）"
                          :allowClear true
                          :treeDefaultExpandAll true
                          :treeData (clj->js (dept-tree-options (build-dept-tree items 0)))}]]
      [antd/form-item {:label "部门名称" :name "dept_name"
                       :rules [{:required true :message "请输入部门名称"}]}
       [antd/input {:placeholder "请输入部门名称"}]]
      [antd/form-item {:label "显示排序" :name "order_num"}
       [antd/input {:type "number" :placeholder "请输入显示排序"}]]
      [antd/form-item {:label "负责人" :name "leader"}
       [antd/input {:placeholder "请输入负责人"}]]
      [antd/form-item {:label "联系电话" :name "phone"}
       [antd/input {:placeholder "请输入联系电话"}]]
      [antd/form-item {:label "邮箱" :name "email"}
       [antd/input {:placeholder "请输入邮箱"}]]
      [antd/form-item {:label "状态" :name "status"}
       [antd/radio-group
        [antd/radio {:value "0"} "正常"]
        [antd/radio {:value "1"} "停用"]]]]]))

;; ─── 主页面 ──────────────────────────────────────────────────────

(defn dept-page []
  (hooks/use-effect
   (fn []
     (rf/dispatch [:depts/fetch {}])
     js/undefined)
   [])
  (let [items @(rf/subscribe [:depts/items])
        loading? @(rf/subscribe [:depts/loading?])
        ;; nil 表示用户还没手动折叠过：数据是异步到达的，defaultExpandAllRows 只看首次渲染，
        ;; 所以展开状态必须自己管
        [expanded-keys set-expanded-keys!] (hooks/use-state nil)
        tree-data (build-dept-tree items 0)
        all-ids (expandable-dept-ids tree-data)
        current-expanded (or expanded-keys all-ids)]
    [:div
     [search-bar]
     [toolbar {:on-toggle-expands #(set-expanded-keys!
                                    (if (= (count current-expanded) (count all-ids))
                                      []
                                      all-ids))}]
     [antd/table {:scroll #js {:x "max-content"} :rowKey "dept_id"
                  :loading loading?
                  :columns (dept-columns)
                  :dataSource (clj->js tree-data)
                  :pagination false
                  :expandedRowKeys (clj->js current-expanded)
                  :onExpand (fn [expanded? ^js record]
                              (let [id (.-dept_id record)]
                                (set-expanded-keys!
                                 (vec (if expanded?
                                        (conj (set current-expanded) id)
                                        (disj (set current-expanded) id))))))
                  :childrenColumnName "children"}]
     [edit-modal]]))
