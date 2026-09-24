(ns com.ruoyi.frontend.events.core
  "核心导航、路由与多 Tab 事件。"
  (:require
   [com.ruoyi.frontend.db :as db]
   [com.ruoyi.frontend.events.common :as ec]
   [com.ruoyi.frontend.router :as router]
   [re-frame.core :as rf]))

(rf/reg-event-db :initialize-db
                 (fn [_ _]
                   db/default-db))

(rf/reg-event-fx :navigate
                 (fn [{:keys [db]} [_ page]]
                   (let [fetch (case page
                                 :user [:users/fetch {}]
                                 :dict [:dicts/fetch-types {}]
                                 :config [:configs/fetch {}]
                                 :oper-log [:oper-logs/fetch {}]
                                 :login-log [:login-logs/fetch {}]
                                 :online [:online-users/fetch {}]
                                 :job [:jobs/fetch {}]
                                 :role [:roles/fetch {}]
                                 :menu [:menus/fetch]
                                 :dept [:depts/fetch {}]
                                 :post [:posts/fetch {}]
                                 :notice [:notices/fetch {}]
                                 nil)
                         effects {:db (ec/activate-page-tab db page)
                                  :router/navigate! page}]
                     (if fetch
                       (assoc effects :dispatch fetch)
                       effects))))

(rf/reg-event-fx :tabs/add
                 (fn [{:keys [db]} [_ key label icon]]
                   (let [tabs (get-in db [:tabs :items] [])
                         exists? (some #(= (:key %) key) tabs)
                         refresh-tab (fn [tab]
                                       (cond-> tab
                                         (= (:key tab) key)
                                         (merge (cond-> {}
                                                  (seq label) (assoc :label label)
                                                  icon (assoc :icon icon)))))]
                     (if exists?
                       {:db (-> db
                                (update-in [:tabs :items] #(mapv refresh-tab %))
                                (assoc-in [:tabs :active] key))}
                       {:db (-> db
                                (update-in [:tabs :items] conj {:key key :label label :icon icon :closable (not= key :dashboard)})
                                (assoc-in [:tabs :active] key))}))))

(rf/reg-event-db :tabs/activate
                 (fn [db [_ key]]
                   (assoc-in db [:tabs :active] key)))

(rf/reg-event-fx :tabs/close
                 (fn [{:keys [db]} [_ key]]
                   {:db db
                    :dispatch [:tabs/remove key]}))

(rf/reg-event-fx :tabs/remove
                 (fn [{:keys [db]} [_ key]]
                   (let [tabs (get-in db [:tabs :items] [])
                         active (get-in db [:tabs :active])
                         remaining (filterv #(not= (:key %) key) tabs)]
                     (if (= active key)
                       (let [new-active (if-let [last-rem (last remaining)] (:key last-rem) :dashboard)]
                         {:db (-> db
                                  (assoc-in [:tabs :items] remaining)
                                  (assoc-in [:tabs :active] new-active))
                          :dispatch [:navigate new-active]})
                       {:db (assoc-in db [:tabs :items] remaining)}))))

(rf/reg-event-fx :tabs/remove-others
                 (fn [{:keys [db]} [_ key]]
                   (let [tabs (get-in db [:tabs :items] [])
                         home-tab (first (filter #(= (:key %) :dashboard) tabs))
                         keep-tab (first (filter #(= (:key %) key) tabs))]
                     {:db (-> db
                              (assoc-in [:tabs :items] (filterv some? [home-tab keep-tab]))
                              (assoc-in [:tabs :active] key))
                      :dispatch [:navigate key]})))

(rf/reg-event-fx :tabs/remove-all
                 (fn [{:keys [db]} _]
                   (let [home-tab (first (filter #(= (:key %) :dashboard) (get-in db [:tabs :items] [])))]
                     {:db (-> db
                              (assoc-in [:tabs :items] (if home-tab [home-tab] []))
                              (assoc-in [:tabs :active] :dashboard))
                      :dispatch [:navigate :dashboard]})))

(rf/reg-event-fx :tabs/remove-right
                 (fn [{:keys [db]} [_ key]]
                   (let [tabs (get-in db [:tabs :items] [])
                         idx (first (keep-indexed #(when (= (:key %2) key) %1) tabs))
                         remaining (if idx (subvec tabs 0 (inc idx)) tabs)]
                     {:db (-> db
                              (assoc-in [:tabs :items] remaining)
                              (assoc-in [:tabs :active] key))
                      :dispatch [:navigate key]})))

(rf/reg-fx :tabs/fullscreen!
           (fn [_]
             (let [el (or (.-documentElement js/document) (.-body js/document))]
               (if (.-fullscreenElement js/document)
                 (.exitFullscreen js/document)
                 (.requestFullscreen el)))))

(rf/reg-event-fx :tabs/fullscreen
                 (fn [_ _]
                   {:tabs/fullscreen! nil}))

(rf/reg-fx :router/navigate!
           (fn [page]
             (router/navigate! page)))
