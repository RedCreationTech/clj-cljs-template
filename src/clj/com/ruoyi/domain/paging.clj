(ns com.ruoyi.domain.paging
  "列表分页样板。控制器统一用 `web.controllers.params/query` 把 `?page/?size` 规范成
   `:page-num / :page-size`,这里换算成 SQL 需要的 `:offset / :page_size`,并把
   `:list-*` 与 `:count-*` 两个查询合成 {:rows, :total}——所有列表接口都是这一个形状,
   前端才能共用一套分页组件。")

(def ^:const default-page-size 10)

(defn paginate
  "跑 list-kw 与 count-kw,返回 {:rows, :total}。defaults 给出筛选字段的默认值
   (HugSQL 要求参数齐全,漏一个就报缺参数)。page-num 从 1 起。
   :filters-fn 在换算完分页参数后再加工 filters(如用户列表要按部门子树/数据范围补键)。"
  [query-fn list-kw count-kw defaults params & {:keys [filters-fn]}]
  (let [page-num (or (:page-num params) 1)
        page-size (or (:page-size params) default-page-size)
        filters (merge defaults
                       (dissoc params :page-num :page-size)
                       {:offset (* (dec page-num) page-size) :page_size page-size})
        filters (if filters-fn (filters-fn filters) filters)]
    {:rows (query-fn list-kw filters)
     :total (or (:total (query-fn count-kw filters)) 0)}))
