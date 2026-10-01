(ns com.ruoyi.web.response
  "Web 层 JSON 响应的统一形状,控制器与鉴权中间件都从这里出响应:

     ok    200 + {:code 200 :msg :data}      成功
     fail  200 + {:code 500 :msg}            业务失败
     deny  <status> + {:code status :msg}    未登录 / 无权限(HTTP 状态与业务码一致)

   业务失败通常不必手写 `fail`:领域层用 `com.ruoyi.infra.errors/fail!` 抛错,
   异常中间件会生成一模一样的响应。`fail` 只留给控制器自己判断出来的失败,
   例如按 id 取记录取不到。"
  (:require
   [ring.util.response :as response]))

(defn- json
  "业务响应统一是 HTTP 200 + JSON body,由前端按 :code 判断成败。"
  [body]
  (-> (response/response body)
      (response/content-type "application/json")))

(defn ok
  "成功响应。msg 给前端直接提示(新增 / 修改 / 删除会弹它)。"
  ([data]
   (ok 200 "操作成功" data))
  ([code msg data]
   (json {:code code :msg msg :data data}))
  ([code msg]
   (json {:code code :msg msg})))

(defn fail
  "业务失败:HTTP 仍是 200,:code 500。"
  [msg]
  (json {:code 500 :msg msg}))

(defn deny
  "拒绝访问:HTTP 状态与业务码一致,前端据此跳登录页或提示无权限。"
  [status msg]
  (-> (json {:code status :msg msg})
      (response/status status)))
