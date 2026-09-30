(ns com.ruoyi.web.request-test
  "集成测试 — 启动完整系统并通过 HTTP 请求测试 API。"
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.test-utils :refer [GET PUT system-fixture system-state]]
   [peridot.core :as p]))

(defonce ^:private shared-token (atom nil))

(use-fixtures :once
  (fn [f]
    ((system-fixture) (fn [] (reset! shared-token nil) (f)))))

(defn- handler []
  (:handler/ring (system-state)))

(defn- parse-json [resp]
  (when (:body resp)
    (try (json/read-str (:body resp) :key-fn keyword)
         (catch Exception _ nil))))

(defn- login-token []
  (let [ctx (-> (p/session (handler))
                (p/request "/api/auth/login"
                           :request-method :post
                           :content-type "application/json"
                           :body (json/write-str {:username "admin" :password "admin123"})))
        resp (:response ctx)]
    (when-let [body (parse-json resp)]
      (get-in body [:data :token]))))

(defn- token
  "整个命名空间共用一次登录(登录要做 bcrypt 校验,每个用例都登录会明显拖慢测试)。
   需要独立会话的用例(登出、续期)请直接调用 login-token。"
  []
  (or @shared-token (reset! shared-token (login-token))))

(defn- auth-headers [token]
  {"authorization" (str "Bearer " token)})

(defn- POST [path body headers]
  (-> (p/session (handler))
      (p/request path :request-method :post :content-type "application/json"
                 :headers headers :body (json/write-str body))
      :response
      (update :body #(if (string? %) % (some-> % slurp)))))

;; ─── 健康检查 ──────────────────────────────────────────────────────

(deftest health-test
  (testing "健康检查 API"
    (let [resp (GET (handler) "/api/health" {} {})]
      (is (= 200 (:status resp)))
      (is (= "up" (get-in (parse-json resp) [:db :status])) "健康检查真正连了数据库"))))

;; ─── 认证 ──────────────────────────────────────────────────────────

(deftest login-test
  (testing "登录 API"
    (let [token (login-token)]
      (is (string? token))
      (is (pos? (count token))))))

(deftest get-info-test
  (testing "获取用户信息 API"
    (let [resp (GET (handler) "/api/auth/getInfo" {} (auth-headers (token)))
          body (parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= 200 (:code body))))))

(deftest auth-config-test
  (testing "登录页配置:test profile 关闭验证码并给出演示账号"
    (let [body (parse-json (GET (handler) "/api/auth/config" {} {}))]
      (is (false? (get-in body [:data :captchaEnabled])))
      (is (false? (get-in body [:data :registerEnabled])))
      (is (= "admin" (get-in body [:data :demoAccount :username]))))))

(deftest logout-invalidates-token-test
  (testing "登出后同一令牌立即失效"
    (let [t (login-token)]
      (is (= 200 (:status (GET (handler) "/api/auth/getInfo" {} (auth-headers t)))))
      (is (= 200 (:status (POST "/api/auth/logout" {} (auth-headers t)))))
      (is (= 401 (:status (GET (handler) "/api/auth/getInfo" {} (auth-headers t))))))))

(deftest refresh-test
  (testing "续期:新令牌可用,旧令牌在宽限期内仍可用"
    (let [t (login-token)
          body (parse-json (POST "/api/auth/refresh" {} (auth-headers t)))
          t2 (get-in body [:data :token])]
      (is (= 200 (:code body)))
      (is (pos-int? (get-in body [:data :expiresIn])))
      (is (and (string? t2) (not= t t2)))
      (is (= 200 (:status (GET (handler) "/api/auth/getInfo" {} (auth-headers t2)))))
      (is (= 200 (:status (GET (handler) "/api/auth/getInfo" {} (auth-headers t))))))))

(deftest anonymous-file-access-test
  (testing "文件上传下载需要登录"
    (is (= 401 (:status (POST "/api/common/upload" {} {}))))
    (is (= 401 (:status (GET (handler) "/api/common/download?fileName=x" {} {}))))
    (is (= 401 (:status (GET (handler) "/api/common/download/resource?resource=x" {} {}))))))

(deftest register-disabled-test
  (testing "默认不开放注册,也就不能借注册接口创建带角色的账号"
    (let [body (parse-json (POST "/api/auth/register" {:username "evil" :password "123456" :roles [1]} {}))]
      (is (= 403 (:code body))))))

;; ─── 系统管理 ──────────────────────────────────────────────────────

(deftest user-list-test
  (testing "用户列表 API"
    (let [resp (GET (handler) "/api/system/user" {} (auth-headers (token)))
          _body (parse-json resp)]
      (is (= 200 (:status resp))))))

(deftest role-list-test
  (testing "角色列表 API"
    (let [resp (GET (handler) "/api/system/role" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest menu-list-test
  (testing "菜单列表 API"
    (let [resp (GET (handler) "/api/system/menu" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest dept-list-test
  (testing "部门列表 API"
    (let [resp (GET (handler) "/api/system/dept" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest post-list-test
  (testing "岗位列表 API"
    (let [resp (GET (handler) "/api/system/post" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest dict-type-list-test
  (testing "字典类型列表 API"
    (let [resp (GET (handler) "/api/system/dict/type" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest config-list-test
  (testing "参数列表 API"
    (let [resp (GET (handler) "/api/system/config" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest notice-list-test
  (testing "通知公告列表 API"
    (let [resp (GET (handler) "/api/system/notice" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

;; ─── 监控 ──────────────────────────────────────────────────────────

(deftest server-monitor-test
  (testing "服务器监控 API"
    (let [resp (GET (handler) "/api/system/server" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest datasource-monitor-test
  (testing "数据源监控 API"
    (let [resp (GET (handler) "/api/system/datasource" {} (auth-headers (token)))
          body (parse-json resp)]
      (is (= 200 (:status resp)))
      (is (some? (get-in body [:data :active_connections]))))))

(deftest online-list-test
  (testing "在线用户列表 API"
    (let [token (token)
          resp (GET (handler) "/api/system/online" {} (auth-headers token))
          body (parse-json resp)]
      (is (= 200 (:status resp)))
      (is (vector? (get-in body [:data :rows]))))))

(deftest operlog-list-test
  (testing "操作日志列表 API"
    (let [resp (GET (handler) "/api/system/oper-log" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest loginlog-list-test
  (testing "登录日志列表 API"
    (let [resp (GET (handler) "/api/system/login-log" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest job-list-test
  (testing "定时任务列表 API"
    (let [resp (GET (handler) "/api/system/job" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest job-run-once-test
  (testing "定时任务立即执行"
    (let [token (token)
          create-ctx (-> (p/session (handler))
                         (p/request "/api/system/job"
                                    :request-method :post
                                    :content-type "application/json"
                                    :headers (auth-headers token)
                                    :body (json/write-str {:job_name "test-job"
                                                           :job_group "DEFAULT"
                                                           :invoke_target "com.ruoyi.task/ry-no-params"
                                                           :cron_expression "0 0 1 * * ?"
                                                           :misfire_policy "3"
                                                           :concurrent "1"
                                                           :status "0"
                                                           :create_by "admin"
                                                           :remark "test"})))
          job-id (get-in (parse-json (:response create-ctx)) [:data :job_id])
          run-resp (PUT (handler) (str "/api/system/job/" job-id "/run") {} (auth-headers token))
          _ (Thread/sleep 1200)
          all-log-resp (GET (handler) "/api/system/job-log?page=1&size=10" {} (auth-headers token))
          log-resp (GET (handler) "/api/system/job-log?page=1&size=10&job_name=test-job" {} (auth-headers token))
          log-body (parse-json log-resp)
          all-log-body (parse-json all-log-resp)]
      (is (some? job-id))
      (is (= 200 (:status run-resp)))
      (is (pos? (count (get-in all-log-body [:data :rows]))))
      (is (pos? (count (get-in log-body [:data :rows])))))))

;; ─── 已移除的代码生成器 ──────────────────────────────────────────────

(deftest code-gen-removed-test
  (testing "页面代码生成器已移除(迁移 20260924000005):接口不存在,菜单与按钮权限也不在了"
    (is (= 404 (:status (GET (handler) "/api/tool/gen/tables" {} (auth-headers (token))))))
    (let [menus (->> (parse-json (GET (handler) "/api/system/menu" {} (auth-headers (token))))
                     (tree-seq coll? seq)
                     (filter map?)
                     (filter :menu_id))]
      (is (seq menus))
      (is (not-any? #(some-> (:perms %) (str/starts-with? "tool:gen")) menus))
      (is (not-any? #(= "gen" (:path %)) menus)))))

;; ─── 导出 ──────────────────────────────────────────────────────────

(deftest export-role-test
  (testing "导出角色数据 API"
    (let [resp (GET (handler) "/api/system/role/export" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))

(deftest export-user-test
  (testing "导出用户数据 API"
    (let [resp (GET (handler) "/api/system/user/export" {} (auth-headers (token)))]
      (is (= 200 (:status resp))))))
