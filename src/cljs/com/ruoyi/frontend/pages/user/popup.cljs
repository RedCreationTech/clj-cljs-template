(ns com.ruoyi.frontend.pages.user.popup
  "自定义用户弹窗的下拉层宿主。")

(defn container
  "下拉层属于当前遮罩的层叠上下文，且不被表单滚动面板裁切。"
  [trigger]
  (or (.closest trigger "[data-user-popup-host]") (.-body js/document)))
