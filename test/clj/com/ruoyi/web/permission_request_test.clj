(ns com.ruoyi.web.permission-request-test
  "按钮级权限的端到端(HTTP)测试:建一个只有「用户列表 + 用户查询」权限的角色和用户,
   逐一验证放行 / 403 / 仅需登录,以及调整角色菜单、停用角色后立即生效。"
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.test-utils :refer [GET PUT system-fixture system-state]]
   [peridot.core :as p]))

(use-fixtures :once (system-fixture))

(defn- handler [] (:handler/ring (system-state)))

(defn- parse-json [resp]
  (try (json/read-str (:body resp) :key-fn keyword) (catch Exception _ nil)))

(defn- POST [path body headers]
  (-> (p/session (handler))
      (p/request path :request-method :post :content-type "application/json"
                 :headers headers :body (json/write-str body))
      :response
      (update :body #(if (string? %) % (some-> % slurp)))))

(defn- login [username password]
  (get-in (parse-json (POST "/api/auth/login" {:username username :password password} {}))
          [:data :token]))

(defn- auth [token] {"authorization" (str "Bearer " token)})

(defn- created-id
  "控制器返回 \"创建成功: <id>\"。"
  [resp]
  (some->> (get-in (parse-json resp) [:data]) (re-find #"\d+") parse-long))

(defn- setup!
  "admin 建角色(菜单 3 = 用户管理,100 = 用户查询)和用户,返回 {:admin .. :role-id .. :token ..}。"
  []
  (let [admin (auth (login "admin" "admin123"))
        suffix (str (System/currentTimeMillis))
        role-id (created-id (POST "/api/system/role"
                              {:role_name (str "只读" suffix) :role_key (str "viewer" suffix)
                               :role_sort 9 :status "0" :menu-ids [1 3 100]}
                              admin))
        user-name (str "viewer" suffix)]
    (POST "/api/system/user" {:user_name user-name :nick_name "只读用户" :password "viewer123"
                              :roles [role-id]}
      admin)
    {:admin admin :role-id role-id :viewer (auth (login user-name "viewer123"))}))

(deftest button-permission-test
  (let [{:keys [admin role-id viewer]} (setup!)
        status :status]
    (is (some? role-id))
    (testing "getInfo 返回该用户的权限标识"
      (is (= ["system:user:list" "system:user:query"]
             (get-in (parse-json (GET (handler) "/api/auth/getInfo" {} viewer)) [:data :permissions]))))
    (testing "有权限的接口放行"
      (is (= 200 (status (GET (handler) "/api/system/user" {} viewer))))
      (is (= 200 (status (GET (handler) "/api/system/user/1" {} viewer)))))
    (testing "缺少按钮权限返回 403"
      (let [resp (POST "/api/system/user" {:user_name "x" :nick_name "x" :password "x12345"} viewer)]
        (is (= 403 (status resp)))
        (is (= {:code 403 :msg "没有操作权限"} (parse-json resp))))
      (is (= 403 (status (PUT (handler) "/api/system/user/1/resetPwd" {:password "hacked1"} viewer))))
      (is (= 403 (status (GET (handler) "/api/system/role" {} viewer))))
      (is (= 403 (status (GET (handler) "/api/system/server" {} viewer))))
      (is (= 403 (status (GET (handler) "/api/system/job" {} viewer)))))
    (testing "满足任一即可:用户页的部门树可以用 system:user:list 读取"
      (is (= 200 (status (GET (handler) "/api/system/dept" {} viewer)))))
    (testing "没声明 :perms 的接口登录即可"
      (is (= 200 (status (GET (handler) "/api/system/dashboard/stats" {} viewer))))
      (is (= 200 (status (GET (handler) "/api/system/profile" {} viewer)))))
    (testing "未登录一律 401"
      (is (= 401 (status (GET (handler) "/api/system/user" {} {}))))
      (is (= 401 (status (GET (handler) "/api/system/dashboard/stats" {} {})))))
    (testing "调整角色菜单立即生效(不需要重新登录)"
      (PUT (handler) (str "/api/system/role/" role-id) {:menu-ids [1 3 100 4]} admin)
      (is (= 200 (status (GET (handler) "/api/system/role" {} viewer)))))
    (testing "停用角色后其权限立即失效"
      (PUT (handler) (str "/api/system/role/" role-id) {:status "1"} admin)
      (is (= 403 (status (GET (handler) "/api/system/user" {} viewer))))
      (is (= [] (get-in (parse-json (GET (handler) "/api/auth/getInfo" {} viewer)) [:data :permissions]))))))

;; ─── 数据权限 ───────────────────────────────────────────────────────

(defn- user-names [headers & [query]]
  (->> (get-in (parse-json (GET (handler) "/api/system/user" (merge {"size" "100"} query) headers)) [:data :rows])
       (map :user_name)
       set))

(deftest data-scope-test
  (let [admin (auth (login "admin" "admin123"))
        suffix (str (System/currentTimeMillis))
        mk-user (fn [n dept & [roles]]
                  (let [u (str n suffix)]
                    (POST "/api/system/user" {:user_name u :nick_name n :password "scope123" :dept_id dept
                                              :roles (or roles [])} admin)
                    u))
        ;; 部门:2 深圳总公司 ─ 4 研发部门 / 5 市场部门;6 财务部门(长沙)
        role-id (created-id (POST "/api/system/role" {:role_name (str "范围" suffix) :role_key (str "scope" suffix)
                                                      :role_sort 9 :status "0" :data_scope "3"
                                                      :menu-ids [1 3 100 102]} admin))
        viewer (mk-user "sv" 5 [role-id])
        same-dept (mk-user "same" 5)
        child-dept (mk-user "rd" 4)
        other (mk-user "fin" 6)
        viewer-h (auth (login viewer "scope123"))
        set-scope! (fn [scope & [dept-ids]]
                     (PUT (handler) "/api/system/role/dataScope"
                       {:role_id role-id :data_scope scope :dept_ids (or dept-ids "")} admin))
        other-id (->> (get-in (parse-json (GET (handler) "/api/system/user" {"user_name" other} admin)) [:data :rows])
                      first :user_id)]
    (testing "本部门(3):只看到部门 5 的用户"
      (let [names (user-names viewer-h)]
        (is (contains? names viewer))
        (is (contains? names same-dept))
        (is (not (contains? names child-dept)))
        (is (not (contains? names other)))
        (is (not (contains? names "admin")))))
    (testing "范围外的单条记录:详情与修改都返回 403"
      (is (= 403 (:status (GET (handler) (str "/api/system/user/" other-id) {} viewer-h))))
      (is (= 403 (:status (PUT (handler) (str "/api/system/user/" other-id) {:nick_name "x"} viewer-h)))))
    (testing "不能把用户改到范围外的部门"
      (let [same-id (->> (get-in (parse-json (GET (handler) "/api/system/user" {"user_name" same-dept} admin)) [:data :rows])
                         first :user_id)]
        (is (= 403 (:status (PUT (handler) (str "/api/system/user/" same-id) {:dept_id 6} viewer-h))))))
    (testing "自定义(2):部门 6"
      (set-scope! "2" "6")
      (is (= [6] (get-in (parse-json (GET (handler) (str "/api/system/role/deptTree/" role-id) {} admin))
                         [:data :checked-keys])))
      (let [names (user-names viewer-h)]
        (is (contains? names other))
        (is (not (contains? names same-dept)))))
    (testing "仅本人(5)"
      (set-scope! "5")
      (is (= #{viewer} (user-names viewer-h))))
    (testing "全部(1)"
      (set-scope! "1")
      (is (contains? (user-names viewer-h) "admin")))
    (testing "按部门筛选包含下级部门(admin 视角)"
      (let [names (user-names admin {"dept_id" "2"})]
        (is (contains? names same-dept))
        (is (contains? names child-dept))
        (is (not (contains? names other)))))
    (testing "按用户名筛选"
      (is (= #{other} (user-names admin {"user_name" other}))))))

(deftest admin-has-all-permissions-test
  (testing "admin 角色的权限是通配 *:*:*"
    (let [admin (auth (login "admin" "admin123"))]
      (is (= ["*:*:*"] (get-in (parse-json (GET (handler) "/api/auth/getInfo" {} admin)) [:data :permissions])))
      (is (= 200 (:status (GET (handler) "/api/system/server" {} admin)))))))
