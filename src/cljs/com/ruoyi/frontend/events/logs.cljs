(ns com.ruoyi.frontend.events.logs
  "操作日志与登录日志事件。"
  (:require
   [clojure.string]
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.logs :as logs-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :oper-logs/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:oper-logs :items] items)
                         (assoc-in [:oper-logs :total] total)
                         (assoc-in [:oper-logs :loading?] false)))))

(rf/reg-event-fx :oper-logs/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:oper-logs :loading?] true)
                    :api/list-oper-logs params}))

(rf/reg-fx :api/list-oper-logs
           (fn [params]
             (logs-api/list-oper-logs params
                                      (fn [result]
                                        (when (= 200 (:code result))
                                          (rf/dispatch [:oper-logs/set-list (:data result)])))
                                      (fn [_]))))

(rf/reg-event-fx :oper-logs/clear
                 (fn [{:keys [db]} _]
                   {:db db
                    :api/clear-oper-logs nil}))

(rf/reg-event-fx :oper-logs/delete
                 (fn [{:keys [db]} [_ ids]]
                   {:db db
                    :api/delete-oper-logs ids}))

(rf/reg-fx :api/delete-oper-logs
           (fn [ids]
             (logs-api/delete-oper-logs ids
                                        (fn [result]
                                          (when (= 200 (:code result))
                                            (antd/success! "删除成功")
                                            (rf/dispatch [:oper-logs/fetch {}]))
                                          (when (not= 200 (:code result))
                                            (antd/error! (:msg result))))
                                        (fn [_] (antd/error! "网络错误")))))

(rf/reg-fx :api/clear-oper-logs
           (fn [_]
             (logs-api/clear-oper-logs
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:oper-logs/cleared])
                  (antd/success! "清空成功")))
              (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :oper-logs/cleared
                 (fn [{:keys [db]} _]
                   {:db db
                    :dispatch [:oper-logs/fetch {}]}))

(rf/reg-event-fx :oper-logs/export
                 (fn [{:keys [db]} _]
                   (let [items (get-in db [:oper-logs :items] [])]
                     (when (seq items)
                       (let [headers ["日志编号" "系统模块" "操作类型" "操作人员" "操作IP" "状态" "操作时间"]
                             rows (map (fn [item]
                                         [(:oper_id item) (:title item) (:business_type item)
                                          (:oper_name item) (:oper_ip item)
                                          (if (= "0" (:status item)) "成功" "失败")
                                          (:oper_time item)])
                                       items)
                             csv (str (clojure.string/join "," headers) "\n"
                                      (clojure.string/join "\n" (map #(clojure.string/join "," %) rows)))
                             blob (js/Blob. #js [csv] #js {:type "text/csv;charset=utf-8"})
                             url (js/URL.createObjectURL blob)
                             link (.createElement js/document "a")]
                         (set! (.-href link) url)
                         (.setAttribute link "download" "oper_log.csv")
                         (.appendChild js/document.body link)
                         (.click link)
                         (.removeChild js/document.body link)
                         (js/URL.revokeObjectURL url)
                         (antd/success! "导出成功"))))
                   {:db db}))

(rf/reg-event-db :login-logs/set-list
                 (fn [db [_ data]]
                   (let [items (if (sequential? data) data (:rows data []))
                         total (if (sequential? data) (count data) (:total data 0))]
                     (-> db
                         (assoc-in [:login-logs :items] items)
                         (assoc-in [:login-logs :total] total)
                         (assoc-in [:login-logs :loading?] false)))))

(rf/reg-event-fx :login-logs/fetch
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:login-logs :loading?] true)
                    :api/list-login-logs params}))

(rf/reg-fx :api/list-login-logs
           (fn [params]
             (logs-api/list-login-logs params
                                       (fn [result]
                                         (when (= 200 (:code result))
                                           (rf/dispatch [:login-logs/set-list (:data result)])))
                                       (fn [_]))))

(rf/reg-event-fx :login-logs/clear
                 (fn [{:keys [db]} _]
                   {:db db
                    :api/clear-login-logs nil}))

(rf/reg-event-fx :login-logs/delete
                 (fn [{:keys [db]} [_ ids]]
                   {:db db
                    :api/delete-login-logs ids}))

(rf/reg-fx :api/delete-login-logs
           (fn [ids]
             (logs-api/delete-login-logs ids
                                         (fn [result]
                                           (when (= 200 (:code result))
                                             (antd/success! "删除成功")
                                             (rf/dispatch [:login-logs/fetch {}]))
                                           (when (not= 200 (:code result))
                                             (antd/error! (:msg result))))
                                         (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :login-logs/unlock
                 (fn [{:keys [db]} [_ username]]
                   (antd/success! (str "用户 " username " 解锁成功"))
                   {:db db}))

(rf/reg-fx :api/clear-login-logs
           (fn [_]
             (logs-api/clear-login-logs
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:login-logs/cleared])
                  (antd/success! "清空成功")))
              (fn [_] (antd/error! "网络错误")))))

(rf/reg-event-fx :login-logs/cleared
                 (fn [{:keys [db]} _]
                   {:db db
                    :dispatch [:login-logs/fetch {}]}))

(rf/reg-event-fx :login-logs/export
                 (fn [{:keys [db]} _]
                   (let [items (get-in db [:login-logs :items] [])]
                     (when (seq items)
                       (let [headers ["访问编号" "用户名称" "登录地址" "登录地点" "浏览器" "操作系统" "登录状态" "操作信息" "登录时间"]
                             rows (map (fn [item]
                                         [(:info_id item) (:user_name item) (:ipaddr item)
                                          (:login_location item) (:browser item) (:os item)
                                          (if (= "0" (:status item)) "成功" "失败")
                                          (:msg item) (:login_time item)])
                                       items)
                             csv (str (clojure.string/join "," headers) "\n"
                                      (clojure.string/join "\n" (map #(clojure.string/join "," %) rows)))
                             blob (js/Blob. #js [csv] #js {:type "text/csv;charset=utf-8"})
                             url (js/URL.createObjectURL blob)
                             link (.createElement js/document "a")]
                         (set! (.-href link) url)
                         (.setAttribute link "download" "login_log.csv")
                         (.appendChild js/document.body link)
                         (.click link)
                         (.removeChild js/document.body link)
                         (js/URL.revokeObjectURL url)
                         (antd/success! "导出成功"))))
                   {:db db}))

(rf/reg-event-db :oper-logs/set-detail
                 (fn [db [_ data]]
                   (assoc-in db [:oper-logs :detail-data] data)))

(rf/reg-event-db :oper-logs/show-detail
                 (fn [db [_ data]]
                   (-> db (assoc-in [:oper-logs :detail-visible?] true) (assoc-in [:oper-logs :detail-data] data))))

(rf/reg-event-db :oper-logs/hide-detail
                 (fn [db _]
                   (assoc-in db [:oper-logs :detail-visible?] false)))
