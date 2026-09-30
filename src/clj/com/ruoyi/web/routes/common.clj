(ns com.ruoyi.web.routes.common
  "通用路由:验证码、头像图片可匿名访问;文件上传下载需要登录。"
  (:require
   [com.ruoyi.web.controllers.captcha :as captcha]
   [com.ruoyi.web.controllers.common :as common]))

(defn common-routes [{:keys [upload-config]}]
  (let [cfg {:upload-config upload-config}]
    [["/captchaImage" {:get {:summary "生成验证码" :handler (partial captcha/captcha-image {})}}]
     ["/common" {:auth? true}
      ;; <img> 带不了令牌,头像图片公开访问(只读头像目录里的图片)
      ["/avatar/:name" {:auth? false
                        :get {:summary "头像图片" :handler (partial common/avatar cfg)}}]
      ["/upload" {:post {:summary "通用文件上传(类型、大小与目录见 :upload-config)"
                         :handler (partial common/upload cfg)}}]
      ["/download" {:get {:summary "通用文件下载" :handler (partial common/download cfg)}}]
      ["/download/resource" {:get {:summary "下载资源文件" :handler (partial common/download-resource cfg)}}]]]))
