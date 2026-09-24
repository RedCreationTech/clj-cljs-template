(ns tasks.scaffold.backend
  "生成后端代码:领域服务(Integrant 组件)、控制器、路由(Malli 参数校验)与集成测试。"
  (:require
   [clojure.string :as str]
   [tasks.scaffold.model :as m]))

(defn- kw-list [cols] (str/join " " (map #(str ":" %) cols)))

(defn domain [{:keys [ns-root module label fields service-key]}]
  (let [cols (map :col fields)
        filters (map :col (m/searchable fields))
        defaults (for [{:keys [col type]} fields :when (= type :bool)] (str ":" col " \"0\""))]
    (str "(ns " ns-root ".domain." module "\n"
         "  \"" label "领域服务(bb new-module 生成)。输入输出都是普通 map,不依赖 HTTP。\"\n"
         "  (:require\n"
         "   [" ns-root ".infra.db :as db]\n"
         "   [integrant.core :as ig]))\n\n"
         "(def ^:private columns [" (kw-list cols) "])\n\n"
         "(def ^:private filter-keys [" (kw-list filters) "])\n\n"
         "(def ^:private defaults {" (str/join " " defaults) "})\n\n"
         "(defn- row-params\n"
         "  \"只保留表字段,缺省的补 nil(HugSQL 要求参数齐全)。\"\n"
         "  [params]\n"
         "  (merge (zipmap columns (repeat nil)) defaults (select-keys params columns)))\n\n"
         "(defn list-page\n"
         "  \"分页查询,返回 {:rows [...] :total n};params 支持 :page :size 与各搜索字段。\"\n"
         "  [{:keys [query-fn]} {:keys [page size] :or {page 1 size 10} :as params}]\n"
         "  (let [filters (merge (zipmap filter-keys (repeat nil)) (select-keys params filter-keys))]\n"
         "    {:rows (query-fn :list-" module " (assoc filters :page_size size :offset (* (dec page) size)))\n"
         "     :total (:total (query-fn :count-" module " filters))}))\n\n"
         "(defn find-by-id [{:keys [query-fn]} id]\n"
         "  (query-fn :find-" module "-by-id {:id id}))\n\n"
         "(defn create!\n"
         "  \"新增,返回新记录 id。\"\n"
         "  [{:keys [query-fn db]} params user-name]\n"
         "  (db/insert-and-get-id! query-fn db :create-" module "! (assoc (row-params params) :create_by user-name)))\n\n"
         "(defn update! [{:keys [query-fn]} id params user-name]\n"
         "  (query-fn :update-" module "! (assoc (row-params params) :id id :update_by user-name)))\n\n"
         "(defn delete! [{:keys [query-fn]} id]\n"
         "  (query-fn :delete-" module "! {:id id}))\n\n"
         "(defmethod ig/init-key " service-key "\n"
         "  [_ {:keys [query-fn db]}]\n"
         "  {:query-fn query-fn :db db})\n")))

(defn controller [{:keys [ns-root module label service-arg]}]
  (str "(ns " ns-root ".web.controllers." module "\n"
       "  \"" label "控制器(bb new-module 生成):请求 ↔ 领域服务,统一 {:code :msg :data} 信封。\"\n"
       "  (:require\n"
       "   [" ns-root ".domain." module " :as svc]\n"
       "   [ring.util.response :as response]))\n\n"
       "(defn- ok [data]\n"
       "  (-> (response/response {:code 200 :msg \"操作成功\" :data data})\n"
       "      (response/content-type \"application/json\")))\n\n"
       "(defn- fail [msg]\n"
       "  (-> (response/response {:code 500 :msg msg})\n"
       "      (response/content-type \"application/json\")))\n\n"
       "(defn- user-name [request] (get-in request [:identity :user-name] \"\"))\n\n"
       "(defn- path-id [request] (get-in request [:parameters :path :id]))\n\n"
       "(defn list-page [{:keys [" service-arg "]} request]\n"
       "  (ok (svc/list-page " service-arg " (get-in request [:parameters :query]))))\n\n"
       "(defn get-one [{:keys [" service-arg "]} request]\n"
       "  (if-let [row (svc/find-by-id " service-arg " (path-id request))]\n"
       "    (ok row)\n"
       "    (fail \"记录不存在\")))\n\n"
       "(defn create [{:keys [" service-arg "]} request]\n"
       "  (ok {:id (svc/create! " service-arg " (get-in request [:parameters :body]) (user-name request))}))\n\n"
       "(defn update-one [{:keys [" service-arg "]} request]\n"
       "  (svc/update! " service-arg " (path-id request) (get-in request [:parameters :body]) (user-name request))\n"
       "  (ok \"更新成功\"))\n\n"
       "(defn delete-one [{:keys [" service-arg "]} request]\n"
       "  (svc/delete! " service-arg " (path-id request))\n"
       "  (ok \"删除成功\"))\n"))

(defn- body-entry [{:keys [col type required?]}]
  (let [schema (get-in m/types [type :malli])]
    (if required?
      (str "[:" col " " (if (#{:string :text} type) "[:string {:min 1}]" schema) "]")
      (str "[:" col " {:optional true} [:maybe " schema "]]"))))

(defn- query-entry [{:keys [col type]}]
  (str "[:" col " {:optional true} " (get-in m/types [type :malli]) "]"))

(defn routes [{:keys [ns-root module label fields service-arg api-path perm-prefix]}]
  (str "(ns " ns-root ".web.routes." module "\n"
       "  \"" label "路由(bb new-module 生成):要求登录,各接口用 :perms 声明按钮权限(见 web.middleware.auth),\n"
       "   Malli 校验参数并生成 Swagger 文档。\"\n"
       "  (:require\n"
       "   [" ns-root ".web.controllers." module " :as ctrl]))\n\n"
       "(def ^:private Query\n"
       "  [:map\n   [:page {:optional true} [:int {:min 1}]]\n   [:size {:optional true} [:int {:min 1 :max 500}]]"
       (apply str (map #(str "\n   " (query-entry %)) (m/searchable fields))) "])\n\n"
       "(def ^:private Body\n  [:map"
       (apply str (map #(str "\n   " (body-entry %)) fields)) "])\n\n"
       "(def ^:private Path [:map [:id :int]])\n\n"
       "(defn routes [{:keys [" service-arg "]}]\n"
       "  (let [ctx {:" service-arg " " service-arg "}]\n"
       "    [\"" api-path "\"\n"
       "     {:auth? true\n"
       "      :swagger {:tags [\"" label "\"]}}\n"
       "     [\"\" {:get {:perms \"" perm-prefix ":list\" :summary \"" label "列表(分页)\"\n"
       "                :parameters {:query Query} :handler (partial ctrl/list-page ctx)}\n"
       "          :post {:perms \"" perm-prefix ":add\" :summary \"新增" label "\"\n"
       "                 :parameters {:body Body} :handler (partial ctrl/create ctx)}}]\n"
       "     [\"/:id\" {:parameters {:path Path}\n"
       "              :get {:perms \"" perm-prefix ":query\" :summary \"" label "详情\" :handler (partial ctrl/get-one ctx)}\n"
       "              :put {:perms \"" perm-prefix ":edit\" :summary \"修改" label "\"\n"
       "                    :parameters {:body Body} :handler (partial ctrl/update-one ctx)}\n"
       "              :delete {:perms \"" perm-prefix ":remove\" :summary \"删除" label "\"\n"
       "                       :handler (partial ctrl/delete-one ctx)}}]]))\n"))

(defn- sample-body [fields suffix]
  (str "{" (str/join " " (for [{:keys [col type]} fields]
                           (str ":" col " "
                                (if (= type :string)
                                  (str "\"示例-" suffix "\"")
                                  (get-in m/types [type :sample])))))
       "}"))

(defn test-ns [{:keys [ns-root module label fields api-path]}]
  (let [first-string (:col (first (filter #(= :string (:type %)) fields)))
        url (str "/api" api-path)]
    (str "(ns " ns-root ".web.controllers." module "-test\n"
         "  \"" label " API 的集成测试(bb new-module 生成):走真实路由、中间件与数据库。\"\n"
         "  (:require\n"
         "   [clojure.data.json :as json]\n"
         "   [clojure.test :refer [deftest is testing use-fixtures]]\n"
         "   [" ns-root ".test-utils :refer [system-fixture system-state]]\n"
         "   [peridot.core :as p]))\n\n"
         "(use-fixtures :once (system-fixture))\n\n"
         "(defn- call [method uri & [{:keys [token body]}]]\n"
         "  (let [resp (-> (p/session (:handler/ring (system-state)))\n"
         "                 (p/request uri :request-method method :content-type \"application/json\"\n"
         "                            :headers (if token {\"authorization\" (str \"Bearer \" token)} {})\n"
         "                            :body (when body (json/write-str body)))\n"
         "                 :response)]\n"
         "    (assoc resp :json (try (json/read-str (let [b (:body resp)] (if (string? b) b (slurp b))) :key-fn keyword)\n"
         "                           (catch Exception _ nil)))))\n\n"
         "(defn- token []\n"
         "  (get-in (call :post \"/api/auth/login\" {:body {:username \"admin\" :password \"admin123\"}}) [:json :data :token]))\n\n"
         "(deftest requires-login-test\n"
         "  (is (= 401 (:status (call :get \"" url "\")))))\n\n"
         "(deftest crud-test\n"
         "  (let [t (token)\n"
         "        created (call :post \"" url "\" {:token t :body " (sample-body fields "a") "})\n"
         "        id (get-in created [:json :data :id])]\n"
         "    (testing \"新增\"\n"
         "      (is (= 200 (get-in created [:json :code])))\n"
         "      (is (pos-int? id)))\n"
         "    (testing \"列表与详情\"\n"
         "      (is (<= 1 (get-in (call :get \"" url "?page=1&size=10\" {:token t}) [:json :data :total])))\n"
         "      (is (= id (get-in (call :get (str \"" url "/\" id) {:token t}) [:json :data :id]))))\n"
         "    (testing \"修改\"\n"
         "      (is (= 200 (get-in (call :put (str \"" url "/\" id) {:token t :body " (sample-body fields "b") "}) [:json :code])))"
         (when first-string
           (str "\n      (is (= \"示例-b\" (get-in (call :get (str \"" url "/\" id) {:token t}) [:json :data :" first-string "])))"))
         ")\n"
         "    (testing \"参数校验\"\n"
         "      (is (= 400 (:status (call :get \"" url "?page=0\" {:token t})))))\n"
         "    (testing \"删除\"\n"
         "      (is (= 200 (get-in (call :delete (str \"" url "/\" id) {:token t}) [:json :code])))\n"
         "      (is (= 500 (get-in (call :get (str \"" url "/\" id) {:token t}) [:json :code]))))))\n")))
