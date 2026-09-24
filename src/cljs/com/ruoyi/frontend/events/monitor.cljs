(ns com.ruoyi.frontend.events.monitor
  "仪表盘、服务器、缓存、数据源与 Integrant 监控事件。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.monitor :as monitor-api]
   [re-frame.core :as rf]))

(rf/reg-event-db :dashboard/set-stats
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:dashboard :stats] data)
                       (assoc-in [:dashboard :loading?] false))))

(rf/reg-event-fx :dashboard/fetch
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:dashboard :loading?] true)
                    :api/get-dashboard-stats nil}))

(rf/reg-fx :api/get-dashboard-stats
           (fn [_]
             (monitor-api/get-dashboard-stats
              (fn [r] (when (= 200 (:code r))
                        (rf/dispatch [:dashboard/set-stats (:data r)])))
              (fn [_] (rf/dispatch [:dashboard/set-stats nil])))))

(rf/reg-event-db :server/set-data
                 (fn [db [_ data]]
                   (-> db (assoc-in [:server :data] data) (assoc-in [:server :loading?] false))))

(rf/reg-event-fx :server/fetch
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:server :loading?] true) :api/get-server-info nil}))

(rf/reg-fx :api/get-server-info
           (fn [_]
             (monitor-api/get-server-info
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:server/set-data (:data r)])))
              (fn [_] (rf/dispatch [:server/set-data nil])))))

(rf/reg-event-db :cache/set-info
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:cache :data] data)
                       (assoc-in [:cache :loading?] false))))

(rf/reg-event-fx :cache/fetch-info
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:cache :loading?] true)
                    :api/get-cache-info nil}))

(rf/reg-fx :api/get-cache-info
           (fn [_]
             (monitor-api/get-cache-info
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:cache/set-info (:data r)])))
              (fn [_]))))

(rf/reg-event-db :cache/set-names
                 (fn [db [_ data]]
                   (assoc-in db [:cache :names] (or (:cacheNames data) []))))

(rf/reg-event-fx :cache/fetch-names
                 (fn [{:keys [db]} _]
                   {:db db :api/get-cache-names nil}))

(rf/reg-fx :api/get-cache-names
           (fn [_]
             (monitor-api/get-cache-names
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:cache/set-names (:data r)])))
              (fn [_]))))

(rf/reg-event-db :cache/select-name
                 (fn [db [_ cache-name]]
                   (assoc-in db [:cache :selected-name] cache-name)))

(rf/reg-event-db :cache/set-keys
                 (fn [db [_ data]]
                   (assoc-in db [:cache :keys] (or (:keys data) []))))

(rf/reg-event-fx :cache/fetch-keys
                 (fn [{:keys [db]} _]
                   (let [cache-name (get-in db [:cache :selected-name])]
                     (if cache-name
                       {:db db :api/get-cache-keys-by-name cache-name}
                       {:db db}))))

(rf/reg-fx :api/get-cache-keys-by-name
           (fn [cache-name]
             (monitor-api/get-cache-keys-by-name
              cache-name
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:cache/set-keys (:data r)])))
              (fn [_]))))

(rf/reg-event-db :cache/set-value
                 (fn [db [_ value]]
                   (assoc-in db [:cache :value] value)))

(rf/reg-event-db :cache/show-value
                 (fn [db _]
                   (assoc-in db [:cache :value-visible?] true)))

(rf/reg-event-db :cache/close-value
                 (fn [db _]
                   (-> db
                       (assoc-in [:cache :value-visible?] false)
                       (assoc-in [:cache :value] nil))))

(rf/reg-event-fx :cache/fetch-value
                 (fn [_ [_ cache-name cache-key]]
                   {:api/get-cache-value [cache-name cache-key]}))

(rf/reg-fx :api/get-cache-value
           (fn [[cache-name cache-key]]
             (monitor-api/get-cache-value
              cache-name cache-key
              (fn [r] (when (= 200 (:code r))
                        (rf/dispatch [:cache/set-value (get-in r [:data :value] "")])
                        (rf/dispatch [:cache/show-value])))
              (fn [_] (antd/error! "获取缓存值失败")))))

(rf/reg-event-fx :cache/clear
                 (fn [{:keys [db]} _]
                   {:db db :api/clear-cache nil}))

(rf/reg-fx :api/clear-cache
           (fn [_]
             (monitor-api/clear-cache
              (fn [r]
                (when (= 200 (:code r))
                  (antd/success! "缓存已清空")
                  (rf/dispatch [:cache/fetch-info])
                  (rf/dispatch [:cache/fetch-names])
                  (rf/dispatch [:cache/fetch-keys])))
              (fn [_] (antd/error! "清空缓存失败")))))

(rf/reg-event-fx :cache/clear-name
                 (fn [_ [_ cache-name]]
                   {:api/clear-cache-name cache-name}))

(rf/reg-fx :api/clear-cache-name
           (fn [cache-name]
             (monitor-api/clear-cache-name
              cache-name
              (fn [r]
                (when (= 200 (:code r))
                  (antd/success! "缓存已清空")
                  (rf/dispatch [:cache/fetch-info])
                  (rf/dispatch [:cache/fetch-names])
                  (rf/dispatch [:cache/fetch-keys])))
              (fn [_] (antd/error! "清空缓存失败")))))

(rf/reg-event-fx :cache/clear-key
                 (fn [_ [_ cache-name cache-key]]
                   {:api/clear-cache-key [cache-name cache-key]}))

(rf/reg-fx :api/clear-cache-key
           (fn [[cache-name cache-key]]
             (monitor-api/clear-cache-key
              cache-name cache-key
              (fn [r]
                (when (= 200 (:code r))
                  (antd/success! "缓存键已清除")
                  (rf/dispatch [:cache/fetch-keys])
                  (rf/dispatch [:cache/fetch-info])))
              (fn [_] (antd/error! "清除缓存键失败")))))

(rf/reg-event-db :server/set-datasource
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:server :datasource] data)
                       (assoc-in [:server :datasource-loading?] false))))

(rf/reg-event-fx :server/fetch-datasource
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:server :datasource-loading?] true)
                    :api/get-datasource nil}))

(rf/reg-fx :api/get-datasource
           (fn [_]
             (monitor-api/get-datasource
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:server/set-datasource (:data r)])))
              (fn [_] (rf/dispatch [:server/set-datasource nil])))))

(rf/reg-event-db :integrant/set-data
                 (fn [db [_ data]]
                   (assoc-in db [:integrant :data] data)))

(rf/reg-event-fx :integrant/fetch
                 (fn [{:keys [db]} _]
                   {:db db :api/get-integrant-info nil}))

(rf/reg-fx :api/get-integrant-info
           (fn [_]
             (monitor-api/get-integrant-info
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:integrant/set-data (:data r)])))
              (fn [_]))))

(rf/reg-event-db :integrant/set-trace
                 (fn [db [_ key data]]
                   (assoc-in db [:integrant :trace key] data)))

(rf/reg-event-fx :integrant/toggle-trace
                 (fn [{:keys [db]} [_ key enabled?]]
                   {:db db :api/set-integrant-trace [key enabled?]}))

(rf/reg-fx :api/set-integrant-trace
           (fn [[key enabled?]]
             (monitor-api/set-integrant-trace
              key enabled?
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:integrant/set-trace key (:data r)])))
              (fn [_]))))

(rf/reg-event-fx :integrant/fetch-trace-logs
                 (fn [{:keys [db]} [_ key]]
                   {:db db :api/get-integrant-trace-logs key}))

(rf/reg-fx :api/get-integrant-trace-logs
           (fn [key]
             (monitor-api/get-integrant-trace-logs
              key
              (fn [r] (when (= 200 (:code r)) (rf/dispatch [:integrant/set-trace key (:data r)])))
              (fn [_]))))
