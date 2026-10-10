(ns com.ruoyi.frontend.events.menu-form
  "菜单表单提交的纯函数，记录标识来自编辑状态。")

(defn form-values
  "数据库中的整型开关转为表单 radio 使用的字符串枚举。"
  [record]
  (reduce (fn [values field]
            (update values field #(if (some? %) (str %) "0")))
          record [:is_frame :is_cache]))

(defn submit-effects
  "AntD 只提交已注册字段，不能从表单 values 读取 menu_id。"
  [db values]
  (let [data (-> values
                 (update :parent_id #(or % 0))
                 (update :order_num #(if (seq (str %)) (js/parseInt % 10) 0)))
        editing (get-in db [:menus :editing])]
    (if editing
      {:db (assoc-in db [:menus :modal-visible?] false)
       :api/update-menu [editing data]}
      {:db (assoc-in db [:menus :modal-visible?] false)
       :api/create-menu data})))
