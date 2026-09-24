(ns com.ruoyi.frontend.api.errors
  "接口失败 → 给用户看的提示(纯函数,Node 单元测试可测)。两类失败:
   - HTTP 失败(网络断开、403、5xx…):error-message;
   - HTTP 200 但业务码不是 200(若依约定 {:code 500 :msg \"用户名已存在\"}):business-message。
   transport 对经它发出的请求统一提示;调用方的 on-error 只负责收尾(关 loading 等),
   调用方如果也弹同一条 msg,antd/error! 按文案去重,不会出现两条。")

(defn error-message
  "cljs-ajax 的失败结果 {:status .. :response ..} → 提示文案;401 由会话过期流程处理,返回 nil。
   服务端的 {:msg ..} 优先(控制器与异常中间件都按这个约定返回)。"
  [{:keys [status response failure]}]
  (let [msg (when (map? response) (:msg response))]
    (cond
      (= 401 status) nil
      (not (pos? status)) "网络错误,请稍后重试"
      (= :parse failure) "响应格式错误"
      msg msg
      (= 403 status) "没有操作权限"
      (= 404 status) "接口不存在"
      (and (= 400 status) (map? response) (:humanized response)) "请求参数不合法"
      (>= status 500) "服务器错误,请稍后重试"
      :else (str "请求失败(" status ")"))))

(defn business-message
  "HTTP 成功但业务码表示失败时的提示;业务码 200、401(走会话过期)或没有业务码时返回 nil。"
  [result]
  (let [code (when (map? result) (:code result))]
    (when (and (number? code) (not= 200 code) (not= 401 code))
      (or (not-empty (:msg result)) "操作失败"))))
