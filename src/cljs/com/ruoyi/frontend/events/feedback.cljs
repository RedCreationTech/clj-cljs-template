(ns com.ruoyi.frontend.events.feedback
  "接口失败的统一反馈(api.transport 在请求失败时派发 :api/error):
   弹出提示(相同文案只显示一条),并复位各模块的 loading 状态。"
  (:require
   [com.ruoyi.frontend.antd :as antd]
   [com.ruoyi.frontend.events.common :as ec]
   [com.ruoyi.frontend.i18n :as i18n]
   [re-frame.core :as rf]))

(rf/reg-event-fx :api/error
                 (fn [{:keys [db]} [_ msg]]
                   {:db (ec/stop-all-loading db)
                    :api/error-toast msg}))

(rf/reg-fx :api/error-toast
           (fn [msg]
             (antd/error! (i18n/tr msg))))
