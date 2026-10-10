(ns com.ruoyi.integrant.state)

(defonce system (atom nil))

;; 服务器保留的旧入口与重载后的 handler 共用这个受保护的运行期身份。
(defonce ring-handler (atom nil))
