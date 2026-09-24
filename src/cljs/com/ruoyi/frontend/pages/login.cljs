(ns com.ruoyi.frontend.pages.login
  "登录页面。是否显示验证码、是否预填演示账号由后端 /api/auth/config 决定
   (生产环境默认开启验证码、不预填账号)。"
  (:require
   ["@ant-design/icons" :refer [LockOutlined SafetyOutlined UserOutlined]]
   [com.ruoyi.frontend.config :as config]
   [com.ruoyi.frontend.i18n :as i18n]
   [re-frame.core :as rf]
   [reagent.hooks :as hooks]))

(defn- login-field [icon type label value set-value!]
  [:div {:style {:marginBottom 20}}
   [:div {:style {:position "relative"}}
    [:span {:style {:position "absolute" :left 12 :top "50%" :transform "translateY(-50%)"
                    :color "#bfbfbf" :fontSize 16 :zIndex 1}}
     [:> icon]]
    [:input {:type type :placeholder label :value value
             :onChange #(set-value! (-> % .-target .-value))
             :style {:width "100%" :height 44 :paddingLeft 38 :border "1px solid #d9d9d9"
                     :borderRadius 6 :fontSize 14 :outline "none" :transition "border-color 0.2s"}
             :onFocus (fn [e] (set! (.. e -target -style -borderColor) "#1677ff"))
             :onBlur (fn [e] (set! (.. e -target -style -borderColor) "#d9d9d9"))}]]])

(defn- login-captcha-field [captcha set-captcha! captcha-url refresh-captcha]
  [:div {:style {:marginBottom 24}}
   [:div {:style {:display "flex" :gap 12}}
    [:div {:style {:position "relative" :flex 1}}
     [:span {:style {:position "absolute" :left 12 :top "50%" :transform "translateY(-50%)"
                     :color "#bfbfbf" :fontSize 16 :zIndex 1}}
      [:> SafetyOutlined]]
     [:input {:type "text" :placeholder (i18n/tr "验证码") :value captcha :maxLength 4
              :onChange #(set-captcha! (-> % .-target .-value))
              :style {:width "100%" :height 44 :paddingLeft 38 :border "1px solid #d9d9d9"
                      :borderRadius 6 :fontSize 14 :outline "none" :transition "border-color 0.2s"}
              :onFocus (fn [e] (set! (.. e -target -style -borderColor) "#1677ff"))
              :onBlur (fn [e] (set! (.. e -target -style -borderColor) "#d9d9d9"))}]]
    [:img {:src captcha-url :alt (i18n/tr "验证码") :onClick refresh-captcha
           :style {:height 44 :cursor "pointer" :borderRadius 6 :border "1px solid #d9d9d9"
                   :userSelect "none"}
           :title (i18n/tr "点击刷新验证码")}]]])

(defn- login-submit-button [loading?]
  [:button {:type "submit"
            :disabled loading?
            :style {:width "100%" :height 44 :background "#1677ff" :color "#fff"
                    :border "none" :borderRadius 6 :fontSize 16 :fontWeight 500
                    :cursor (if loading? "not-allowed" "pointer")
                    :opacity (if loading? 0.65 1)
                    :transition "all 0.2s"}}
   (i18n/tr (if loading? "登录中..." "登 录"))])

(defn- login-header []
  [:div {:style {:background "rgba(255,255,255,0.1)" :padding "32px 40px 24px"
                 :textAlign "center" :backdropFilter "blur(10px)"}}
   [:h1 {:style {:margin 0 :fontSize 28 :fontWeight 600 :color "#fff" :letterSpacing 2}}
    config/app-name]
   [:p {:style {:margin "8px 0 0" :fontSize 14 :color "rgba(255,255,255,0.7)"}}
    config/app-subtitle]])

(defn- login-form
  "登录表单。挂载时配置已就绪,演示账号(仅开发/测试环境返回)直接作为初始值,不存在与输入竞争的异步预填。"
  [config]
  (let [loading? @(rf/subscribe [:auth/loading?])
        failures @(rf/subscribe [:auth/failures])
        [username set-username!] (hooks/use-state (get-in config [:demoAccount :username] ""))
        [password set-password!] (hooks/use-state (get-in config [:demoAccount :password] ""))
        [captcha set-captcha!] (hooks/use-state "")
        [captcha-uuid set-captcha-uuid!] (hooks/use-state #(str (random-uuid)))
        captcha? (:captchaEnabled config)
        refresh-captcha (fn [] (set-captcha! "") (set-captcha-uuid! (str (random-uuid))))
        handle-login (fn [e]
                       (.preventDefault e)
                       (rf/dispatch [:auth/login (cond-> {:username username :password password}
                                                   captcha? (assoc :captcha captcha :uuid captcha-uuid))]))]
    ;; 登录失败后旧验证码已作废,换一张
    (hooks/use-effect (fn [] (when (pos? failures) (refresh-captcha)) js/undefined) [failures])
    [:form {:onSubmit handle-login
            :style {:background "#fff" :padding "32px 40px 40px"}}
     [login-field UserOutlined "text" (i18n/tr "用户名") username set-username!]
     [login-field LockOutlined "password" (i18n/tr "密码") password set-password!]
     (when captcha?
       [login-captcha-field captcha set-captcha! (str "/api/captcha/image?r=" captcha-uuid) refresh-captcha])
     [login-submit-button loading?]]))

(defn login-page []
  (let [config @(rf/subscribe [:auth/login-config])]
    (hooks/use-effect (fn [] (rf/dispatch [:auth/fetch-login-config]) js/undefined) [])
    [:div {:style {:display "flex" :justifyContent "center" :alignItems "center"
                   :height "100vh"
                   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}
     [:div {:style {:width 420 :borderRadius 12 :overflow "hidden"
                    :boxShadow "0 8px 32px rgba(0,0,0,0.15)"}}
      [login-header]
      ;; 等登录页配置返回再渲染表单(是否显示验证码、是否预填由它决定;失败时按空配置渲染)
      (if config
        [login-form config]
        [:div {:style {:background "#fff" :height 240}}])]]))
