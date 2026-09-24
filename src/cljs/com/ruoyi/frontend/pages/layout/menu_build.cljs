(ns com.ruoyi.frontend.pages.layout.menu-build
  "把后端返回的菜单树转换为 antd Menu 结构,并派生页面标签/图标/展开键。"
  (:require
   [com.ruoyi.frontend.components.icon-picker :as icon-picker]
   [com.ruoyi.frontend.router :as router]))

(defn filter-visible-menus
  "过滤掉 F 类型（按钮权限）菜单，只保留 M 目录和 C 菜单。"
  [menus]
  (->> menus
       (filter #(contains? #{"M" "C"} (:menu_type %)))
       (mapv (fn [m]
               (if (seq (:children m))
                 (assoc m :children (filter-visible-menus (:children m)))
                 m)))))

(defn menu->antd-items
  "将后端菜单树转换为 antd Menu 的 items 结构。"
  ([menus] (menu->antd-items menus ""))
  ([menus parent-path]
   (clj->js
    (mapv (fn [m]
            (let [path (:path m)
                  full-path (cond
                              (not (seq path)) parent-path
                              (seq parent-path) (str parent-path "/" path)
                              :else path)
                  item-key (if (seq full-path)
                             full-path
                             (str "menu-" (:menu_id m)))
                  item {:key item-key
                        :label (:menu_name m)}
                  icon-name (or (and (seq (:icon m)) (not= (:icon m) "#") (:icon m))
                                "ContainerOutlined")
                  icon-el (icon-picker/icon-element icon-name {:style {:fontSize 14}})]
              (cond-> item
                icon-el
                (assoc :icon icon-el)
                (seq (:children m))
                (assoc :children (menu->antd-items (:children m) full-path)))))
          menus))))

(defn page-labels
  "从菜单树递归提取页面路径到标签的映射。"
  ([menus] (page-labels menus ""))
  ([menus parent-path]
   (reduce (fn [acc m]
             (let [path (:path m)
                   full-path (cond
                               (not (seq path)) parent-path
                               (seq parent-path) (str parent-path "/" path)
                               :else path)
                   ;; 查找路由关键词，如 system/user -> :user
                   matched (when (seq full-path)
                             (router/match-route (str "/" full-path)))
                   route-key (:handler matched)
                   acc (if route-key
                         (assoc acc route-key (:menu_name m))
                         acc)]
               (if (seq (:children m))
                 (merge acc (page-labels (:children m) full-path))
                 acc)))
           {}
           menus)))

(defn page-icons
  "从菜单树递归提取页面路径到图标的映射。"
  ([menus] (page-icons menus ""))
  ([menus parent-path]
   (reduce (fn [acc m]
             (let [path (:path m)
                   full-path (cond
                               (not (seq path)) parent-path
                               (seq parent-path) (str parent-path "/" path)
                               :else path)
                   matched (when (seq full-path)
                             (router/match-route (str "/" full-path)))
                   route-key (:handler matched)
                   icon (or (and (seq (:icon m)) (not= (:icon m) "#") (:icon m))
                            "ContainerOutlined")
                   acc (if route-key
                         (assoc acc route-key icon)
                         acc)]
               (if (seq (:children m))
                 (merge acc (page-icons (:children m) full-path))
                 acc)))
           {}
           menus)))

(defn- find-ancestor-keys
  "深度优先定位 target-key,返回其祖先分组 key 列表(不含自身);未命中返回 nil。"
  [menus target-key parent-path group-trail]
  (reduce (fn [acc m]
            (if acc
              acc
              (let [path (:path m)
                    full-path (cond
                                (not (seq path)) parent-path
                                (seq parent-path) (str parent-path "/" path)
                                :else path)
                    item-key (if (seq full-path)
                               full-path
                               (str "menu-" (:menu_id m)))
                    children (:children m)]
                (cond
                  (= item-key target-key) group-trail
                  (seq children) (find-ancestor-keys children target-key full-path
                                                     (conj group-trail item-key))
                  :else nil))))
          nil
          menus))

(defn menu-ancestor-keys
  "定位 target-key 需要展开的各级祖先分组 key(默认折叠时只展开当前页所在分支)。未命中返回 []。"
  [menus target-key]
  (or (when (seq target-key) (find-ancestor-keys menus target-key "" [])) []))
