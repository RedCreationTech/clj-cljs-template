(ns com.ruoyi.frontend.events.auth
  "认证事件:登录页配置、登录、令牌续期、会话过期、登出、当前用户信息。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.api.auth :as auth-api]
   [com.ruoyi.frontend.i18n :as i18n]
   [com.ruoyi.frontend.storage :as storage]
   [re-frame.core :as rf]))

(defn clear-session
  "清掉 app-db 里与登录态相关的数据(令牌、用户、菜单、Tab)。"
  [db]
  (-> db
      (assoc-in [:auth :token] nil)
      (assoc-in [:auth :user] nil)
      (assoc-in [:auth :refreshing?] false)
      (assoc-in [:menus :items] [])
      (assoc-in [:menus :tree-data] [])
      (assoc :tabs {:items [{:key :dashboard :label "首页" :closable false}]
                    :active :dashboard})))

(rf/reg-fx :auth/toast
           (fn [[kind text]]
             (case kind
               :error (antd/error! text)
               :warning (antd/warning! text)
               (antd/success! text))))

(rf/reg-fx :storage/remove-session
           (fn [_] (storage/remove-item! :token :user)))

(rf/reg-event-db :auth/set-token
                 (fn [db [_ token]]
                   (assoc-in db [:auth :token] token)))

(rf/reg-event-fx :auth/set-user
                 (fn [{:keys [db]} [_ user]]
                   (storage/set-json! :user user)
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

;; ─── 登录页配置 ─────────────────────────────────────────────

(rf/reg-event-fx :auth/fetch-login-config
                 (fn [_ _] {:api/login-config nil}))

(rf/reg-fx :api/login-config
           (fn [_]
             (auth-api/login-config
              #(rf/dispatch [:auth/set-login-config (:data %)])
              #(rf/dispatch [:auth/set-login-config {}]))))

(rf/reg-event-db :auth/set-login-config
                 (fn [db [_ config]]
                   (assoc-in db [:auth :login-config] config)))

;; ─── 登录 ───────────────────────────────────────────────────

(rf/reg-event-fx :auth/login
                 (fn [{:keys [db]} [_ params]]
                   {:db (assoc-in db [:auth :loading?] true)
                    :api/login params}))

(rf/reg-fx :api/login
           (fn [params]
             (auth-api/login params
                             (fn [result]
                               (if (= 200 (:code result))
                                 (rf/dispatch [:auth/login-success (:data result)])
                                 (rf/dispatch [:auth/login-failure (:msg result)])))
                             ;; HTTP 失败(网络、5xx)的提示由 transport 统一弹出,这里只复位按钮
                             (fn [_]
                               (rf/dispatch [:auth/login-failure nil])))))

(rf/reg-event-fx :auth/login-success
                 (fn [{:keys [db]} [_ data]]
                   (let [token (:token data)]
                     (storage/set-item! :token token)
                     {:db (-> db
                              (assoc-in [:auth :token] token)
                              (assoc-in [:auth :loading?] false))
                      :dispatch-n [[:navigate :dashboard] [:auth/fetch-info]]})))

(rf/reg-event-fx :auth/login-failure
                 (fn [{:keys [db]} [_ msg]]
                   ;; failures 计数变化会让登录页换一张验证码(旧验证码提交后已作废)
                   (cond-> {:db (-> db
                                    (assoc-in [:auth :loading?] false)
                                    (update-in [:auth :failures] (fnil inc 0)))}
                     msg (assoc :auth/toast [:error msg]))))

;; ─── 续期与过期 ─────────────────────────────────────────────

(rf/reg-event-fx :auth/refresh
                 (fn [{:keys [db]} _]
                   (when (and (get-in db [:auth :token])
                              (not (get-in db [:auth :refreshing?])))
                     {:db (assoc-in db [:auth :refreshing?] true)
                      :api/refresh nil})))

(rf/reg-fx :api/refresh
           (fn [_]
             (auth-api/refresh #(rf/dispatch [:auth/refreshed %])
                               #(rf/dispatch [:auth/refreshed nil]))))

(rf/reg-event-db :auth/refreshed
                 (fn [db [_ result]]
                   (let [token (when (= 200 (:code result)) (get-in result [:data :token]))]
                     (when token (storage/set-item! :token token))
                     (cond-> (assoc-in db [:auth :refreshing?] false)
                       ;; 续期期间已登出/过期的话不要把令牌写回去
                       (and token (get-in db [:auth :token])) (assoc-in [:auth :token] token)))))

(rf/reg-event-fx :auth/session-expired
                 (fn [{:keys [db]} _]
                   (when (get-in db [:auth :token])
                     {:db (clear-session db)
                      :storage/remove-session nil
                      :auth/toast [:warning (i18n/tr "登录状态已过期,请重新登录")]
                      :dispatch [:navigate :login]})))

;; ─── 登出与用户信息 ─────────────────────────────────────────

(rf/reg-event-fx :auth/logout
                 (fn [{:keys [db]} _]
                   {:db (clear-session db)
                    :storage/remove-session nil
                    ;; 先取出令牌再清库,否则登出请求不带令牌,服务端会话删不掉
                    :api/logout (get-in db [:auth :token])
                    :dispatch [:navigate :login]}))

(rf/reg-fx :api/logout
           (fn [token]
             (when token
               (auth-api/logout token (fn [_]) (fn [_])))))

(rf/reg-event-fx :auth/fetch-info
                 (fn [_ _]
                   {:api/get-info nil}))

(rf/reg-fx :api/get-info
           (fn [_]
             (auth-api/get-info
              (fn [result]
                (when (= 200 (:code result))
                  (rf/dispatch [:auth/set-user (:data result)])))
              (fn [_]))))
