(ns com.ruoyi.web.controllers.system.notice
  "通知公告控制器。"
  (:require
   [com.ruoyi.domain.paging :as paging]
   [com.ruoyi.web.controllers.params :as params]
   [com.ruoyi.web.response :as res]))

(defn- parse-int
  "将字符串解析为整数。"
  [v]
  (when v (Integer/parseInt v)))

(defn list-notices
  "查询通知公告列表。"
  [{:keys [query-fn]} request]
  (res/ok (paging/paginate query-fn :list-notices :count-notices
                           {:notice_name nil :notice_type nil :create_by nil}
                           (params/query request))))

(defn latest-notices
  "顶部铃铛(登录即可访问):最新 5 条已发布通知(带 is_read)与当前用户的未读总数。"
  [{:keys [query-fn]} request]
  (let [user-id (get-in request [:identity :user-id])]
    (res/ok {:rows (query-fn :list-latest-notices {:user_id user-id :limit 5})
             :unread (:total (query-fn :count-unread-notices {:user_id user-id}))})))

(defn read-all-notices
  "把当前所有已发布通知记为当前用户已读(打开铃铛时调用)。"
  [{:keys [query-fn]} request]
  (query-fn :mark-all-notices-read! {:user_id (get-in request [:identity :user-id])})
  (res/ok 200 "操作成功" {}))

(defn get-notice
  "获取通知公告详情。"
  [{:keys [query-fn]} request]
  (let [notice-id (parse-int (get-in request [:path-params :id]))]
    (if-let [notice (query-fn :find-notice-by-id {:notice_id notice-id})]
      (res/ok notice)
      (res/fail "通知公告不存在"))))

(defn create-notice
  "新增通知公告。"
  [{:keys [query-fn]} request]
  (let [body (:body-params request)
        identity (:identity request)
        params {:notice_name (:notice_name body)
                :notice_type (:notice_type body "1")
                :status      (:status body "0")
                :create_by   (:user-name identity "")
                :notice_content (:notice_content body "")
                :remark      (:remark body "")}]
    (query-fn :create-notice! params)
    (res/ok "创建成功")))

(defn update-notice
  "更新通知公告。"
  [{:keys [query-fn]} request]
  (let [notice-id (parse-int (get-in request [:path-params :id]))
        body (:body-params request)
        params {:notice_id   notice-id
                :notice_name (:notice_name body)
                :notice_type (:notice_type body)
                :status      (:status body)
                :notice_content (:notice_content body)
                :update_by   (get-in request [:identity :user-name] "")
                :remark      (:remark body)}]
    (query-fn :update-notice! params)
    (res/ok "更新成功")))

(defn delete-notice
  "删除通知公告。"
  [{:keys [query-fn]} request]
  (let [notice-id (parse-int (get-in request [:path-params :id]))]
    (query-fn :delete-notice-reads! {:notice_id notice-id})
    (query-fn :delete-notice! {:notice_id notice-id})
    (res/ok 200 "删除成功" {})))
