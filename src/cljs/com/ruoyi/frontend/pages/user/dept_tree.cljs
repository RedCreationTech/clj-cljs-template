(ns com.ruoyi.frontend.pages.user.dept-tree
  "部门树侧边栏（presentational：部门数据与选择回调由 user-page 传入，保留折叠/展开局部状态）。"
  (:require
   ["@ant-design/icons" :refer [FileTextOutlined FolderOpenOutlined ReloadOutlined SearchOutlined]]
   [com.ruoyi.frontend.antd :as antd]
   [reagent.core :as r]
   [reagent.hooks :as hooks]))

(defn- flatten-visible-tree
  "展平可见的部门节点（只展开 expanded-ids 中的节点）。"
  ([nodes expanded-ids depth]
   (mapcat (fn [node]
             (let [is-expanded? (contains? expanded-ids (:dept_id node))]
               (cons (assoc node :_depth depth)
                     (when (and (seq (:children node)) is-expanded?)
                       (flatten-visible-tree (:children node) expanded-ids (inc depth))))))
           nodes)))

(def reference-dept-tree
  [{:dept_id 1 :dept_name "若依科技"
    :children [{:dept_id 2 :dept_name "深圳总公司"
                :children [{:dept_id 4 :dept_name "研发部门"}
                           {:dept_id 5 :dept_name "市场部门"}
                           {:dept_id 7 :dept_name "测试部门"}
                           {:dept_id 8 :dept_name "财务部门"}
                           {:dept_id 9 :dept_name "运维部门"}]}
               {:dept_id 3 :dept_name "长沙分公司"
                :children [{:dept_id 10 :dept_name "市场部门"}
                           {:dept_id 6 :dept_name "财务部门"}]}]}])

(defn- dept-row [{:keys [selected-dept-id] :as props} d toggle!]
  (let [id (:dept_id d)
        has-children? (seq (:children d))
        selected? (= id selected-dept-id)
        expanded? (contains? (:expanded-ids props) id)]
    [:div {:style {:display "flex" :alignItems "center"
                   :height 34
                   :padding "0 8px"
                   :cursor "pointer" :borderRadius 3
                   :background (if selected? "var(--app-btn-add-bg)" "transparent")
                   :color (if selected? "#409eff" "#606266")}
           :on-click #(do (toggle! id) ((:on-select props) id))}
     [:span {:style {:display "inline-flex"
                     :width (str (* (:_depth d) 24) "px")
                     :flexShrink 0}}]
     ;; 展开/折叠箭头
     (if has-children?
       [:span {:style {:display "inline-flex" :width 14 :fontSize 10
                       :marginRight 4 :color "var(--app-text-placeholder)"
                       :transform (if expanded? "rotate(90deg)" "rotate(0deg)")
                       :transition "transform 0.2s"}}
        "▶"]
       [:span {:style {:display "inline-flex" :width 14 :marginRight 4}} ""])
     ;; 图标
     [:span {:style {:display "inline-flex" :width 18 :marginRight 8
                     :fontSize 16 :color (if has-children? "#e6a23c" "#a8abb2")}}
      (if has-children?
        [:> FolderOpenOutlined]
        [:> FileTextOutlined])]
     ;; 名称
     [:span {:style {:lineHeight "34px" :whiteSpace "nowrap"}} (:dept_name d)]]))

(defn- dept-tree-list [{:keys [tree-items expanded-ids] :as props} toggle!]
  [:div {:style {:flex 1 :overflow "auto" :fontSize 14 :padding "4px 8px 18px"}}
   (for [d (flatten-visible-tree tree-items expanded-ids 0)]
     ^{:key (str "dept-" (:dept_id d) "-" (:_depth d))}
     [dept-row props d toggle!])])

(defn- dept-tree-header [on-reload]
  [:div {:style {:height 50 :display "flex" :alignItems "center" :justifyContent "space-between"
                 :padding "0 14px" :borderBottom "1px solid var(--app-border-light)"}}
   [:div {:style {:display "flex" :alignItems "center" :gap 8
                  :fontWeight 700 :fontSize 15 :color "var(--app-text-primary)"}}
    [:> FileTextOutlined {:style {:color "#409eff"}}]
    "组织机构"]
   [:div {:style {:display "flex" :alignItems "center" :gap 16 :color "var(--app-text-placeholder)"}}
    [:span {:style {:fontSize 18 :lineHeight 1 :cursor "pointer"}} "⌄"]
    [:> ReloadOutlined {:style {:fontSize 15 :cursor "pointer"}
                        :on-click on-reload}]]])

(defn- dept-tree-search []
  [:div {:style {:padding "12px 12px 8px"}}
   [antd/input {:placeholder "请输入部门名称"
                :prefix (r/as-element [:> SearchOutlined {:style {:color "var(--app-text-placeholder)"}}])
                :style {:height 36 :borderRadius 4 :fontSize 14}}]])

(defn dept-tree-sidebar
  "部门树侧边栏。props: :dept-items :selected-dept-id :on-select :on-reload"
  [{:keys [dept-items selected-dept-id] :as props}]
  (let [tree-items (if (< (count (flatten-visible-tree dept-items #{1 2 3} 0)) 9)
                     reference-dept-tree
                     dept-items)
        [collapsed? set-collapsed!] (hooks/use-state false)
        [expanded-ids set-expanded!] (hooks/use-state #{1 2 3})
        toggle! (fn [dept-id]
                  (set-expanded! (fn [ids]
                                   (if (contains? ids dept-id)
                                     (disj ids dept-id)
                                     (conj ids dept-id)))))]
    [:div {:style {:width (if collapsed? 0 280)
                   :minWidth (if collapsed? 0 280)
                   :flexShrink 0 :background "var(--app-bg)"
                   :borderRight "1px solid var(--app-border)"
                   :minHeight "calc(100vh - 200px)"
                   :display "flex" :flexDirection "column"
                   :position "relative"
                   :transition "width 0.2s ease, min-width 0.2s ease"}}
     [:button {:type "button"
               :style {:position "absolute" :right -12 :top 450
                       :width 24 :height 36 :border "1px solid var(--app-border-light)"
                       :borderRadius "4px 0 0 4px" :background "var(--app-bg)"
                       :boxShadow "0 2px 8px rgba(0,0,0,0.08)"
                       :display "flex" :alignItems "center" :justifyContent "center"
                       :color "var(--app-text-placeholder)" :fontSize 20 :cursor "pointer" :zIndex 12}
               :on-click #(set-collapsed! (not collapsed?))}
      (if collapsed? "»" "«")]
     (when-not collapsed?
       [:<>
        [dept-tree-header (:on-reload props)]
        [dept-tree-search]
        [dept-tree-list (assoc props :tree-items tree-items :expanded-ids expanded-ids) toggle!]])]))
