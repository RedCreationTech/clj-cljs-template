(ns com.ruoyi.frontend.components.pagination
  "分页组件封装。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.i18n :as i18n]))

(defn pagination [{:keys [page page-size total on-change]}]
  [antd/pagination {:current page
                    :pageSize page-size
                    :total total
                    :showSizeChanger true
                    :showTotal (fn [total] (i18n/tr "共 {0} 条" total))
                    :onChange on-change}])
