(ns com.ruoyi.frontend.events.auth
  "认证登录/登出事件。"
  (:require
   [com.ruoyi.frontend.api :as api]
   [re-frame.core :as rf]))

(rf/reg-event-db :auth/set-token
                 (fn [db [_ token]]
                   (assoc-in db [:auth :token] token)))

(rf/reg-event-fx :auth/set-user
                 (fn [{:keys [db]} [_ user]]
                   (try
                     (.setItem js/localStorage "ruoyi_user" (.stringify js/JSON (clj->js user)))
                     (catch js/Error _))
                   (let [page (:page db)
                         effects {:db (assoc-in db [:auth :user] user)}
                         ;; 只在登录后或当前页面异常时导航到 dashboard
                         non-page? (or (nil? page) (= :login page))]
                     (if non-page?
                       (assoc effects :dispatch [:navigate :dashboard])
                       effects))))

(rf/reg-event-db :auth/set-loading
                 (fn [db [_ loading?]]
                   (assoc-in db [:auth :loading?] loading?)))

(rf/reg-event-fx :auth/login
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:auth :loading?] true)
                    :api/login params}))

(rf/reg-fx :api/login
           (fn [params]
             (api/login params
                        (fn [result]
                          (when (= 200 (:code result))
                            (rf/dispatch [:auth/login-success (:data result)]))
                          (when (not= 200 (:code result))
                            (rf/dispatch [:auth/login-failure (:msg result)])))
                        (fn [_]
                          (rf/dispatch [:auth/login-failure "网络错误"])))))

(rf/reg-event-fx :auth/login-success
                 (fn [{:keys [db]} [_ data]]
                   (let [token (:token data)]
                     ;; 保存到 localStorage
                     (try (.setItem js/localStorage "ruoyi_token" token) (catch js/Error _))
                     {:db (-> db
                              (assoc-in [:auth :token] token)
                              (assoc-in [:auth :loading?] false))
                      :dispatch-n [[:navigate :dashboard] [:auth/fetch-info]]})))

(rf/reg-event-db :auth/login-failure
                 (fn [db [_ msg]]
                   (-> db
                       (assoc-in [:auth :loading?] false)
                       (assoc :notification {:type :error :message msg}))))

(rf/reg-event-fx :auth/logout
                 (fn [{:keys [db]} _]
                   (try
                     (.removeItem js/localStorage "ruoyi_token")
                     (.removeItem js/localStorage "ruoyi_user")
                     (catch js/Error _))
                   {:db (-> db
                            (assoc-in [:auth :token] nil)
                            (assoc-in [:auth :user] nil)
                            (assoc-in [:menus :items] [])
                            (assoc-in [:menus :tree-data] [])
                            (assoc :tabs {:items [{:key :dashboard :label "首页" :closable false}]
                                          :active :dashboard}))
                    :api/logout nil
                    :dispatch [:navigate :login]}))

(rf/reg-fx :api/logout
           (fn [_]
             (api/logout
              (fn [_]
                (try
                  (.removeItem js/localStorage "ruoyi_token")
                  (.removeItem js/localStorage "ruoyi_user")
                  (catch js/Error _)))
              (fn [_]
                (try
                  (.removeItem js/localStorage "ruoyi_token")
                  (.removeItem js/localStorage "ruoyi_user")
                  (catch js/Error _))))))

(rf/reg-event-fx :auth/fetch-info
                 (fn [{:keys [db]} _]
                   {:db db
                    :api/get-info nil}))

(rf/reg-fx :api/get-info
           (fn [_]
             (api/get-info
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:auth/set-user (:data result)])))
              (fn [_]))))
