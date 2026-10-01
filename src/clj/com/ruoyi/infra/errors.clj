(ns com.ruoyi.infra.errors
  "业务错误的统一出口。

   领域层校验不通过时用 `fail!` 抛带类型的 ex-info,不要返回 `:error` 标记、也不要在控制器里 try/catch:
   `web.middleware.exception` 会把它转成前端约定的 HTTP 200 + `{:code 500 :msg}`,和控制器手写 fail 的效果一致。

   消息会原样显示给用户,所以只写中文业务提示,不要拼 SQL、路径、异常类名等内部信息 ——
   那类意外错误让它照常抛出,走 5xx 的通用文案。")

(def business-type
  "业务规则不通过。异常中间件按这个 :type 分派。"
  :system.exception/business)

(defn fail!
  "抛业务错误:msg 是给前端展示的中文提示。"
  ([msg]
   (throw (ex-info msg {:type business-type})))
  ([msg data]
   (throw (ex-info msg (assoc data :type business-type)))))
