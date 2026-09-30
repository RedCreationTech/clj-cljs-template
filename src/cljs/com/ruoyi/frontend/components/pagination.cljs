(ns com.ruoyi.frontend.components.pagination
  "列表分页属性封装:所有服务端分页的表格都从这里取 :pagination,
   这样页码/每页条数的回调不会在某一个页面被漏掉(漏掉时页码会跳但数据不换)。"
  (:require
   [com.ruoyi.frontend.i18n :as i18n]))

(defn table-pagination
  "antd Table 的 :pagination 属性。on-change 收 (page, page-size),两个都必须重新请求。"
  [{:keys [total page page-size on-change]}]
  {:total (or total 0)
   :current (or page 1)
   :pageSize (or page-size 10)
   :showSizeChanger true
   :showTotal (fn [t] (i18n/tr "共 {0} 条" t))
   :onChange (fn [page size] (on-change (js/parseInt page 10) (js/parseInt size 10)))})

(defn client-pagination
  "一次性拿到全部数据、由 antd 在本地翻页的表格(如弹窗里的分配用户列表)。
   服务端分页的列表一律用 table-pagination,不要用这个——否则页码换了数据不换。"
  ([] (client-pagination {:page-size 10}))
  ([{:keys [page-size]}]
   {:pageSize (or page-size 10)
    :showSizeChanger true
    :showTotal (fn [t] (i18n/tr "共 {0} 条" t))}))
