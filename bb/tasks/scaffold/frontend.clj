(ns tasks.scaffold.frontend
  "生成前端代码(api / re-frame 事件与订阅 / 页面)与 Playwright 用例。"
  (:require
   [clojure.string :as str]
   [tasks.scaffold.model :as m]))

(defn api [{:keys [ns-root module label api-path]}]
  (str "(ns " ns-root ".frontend.api." module "\n"
       "  \"" label "接口(bb new-module 生成)。\"\n"
       "  (:require\n"
       "   [" ns-root ".frontend.api.transport :as t]))\n\n"
       "(def ^:private base \"" api-path "\")\n\n"
       "(defn list-page [params on-success on-error]\n"
       "  (t/request {:method :get :uri base :params params :on-success on-success :on-error on-error}))\n\n"
       "(defn create! [body on-success on-error]\n"
       "  (t/request {:method :post :uri base :params body :on-success on-success :on-error on-error}))\n\n"
       "(defn update! [id body on-success on-error]\n"
       "  (t/request {:method :put :uri (str base \"/\" id) :params body :on-success on-success :on-error on-error}))\n\n"
       "(defn delete! [id on-success on-error]\n"
       "  (t/request {:method :delete :uri (str base \"/\" id) :on-success on-success :on-error on-error}))\n"))

(defn- events-core
  "ns、状态辅助函数、订阅、列表加载与请求 effect。"
  [{:keys [ns-root module label]}]
  (let [k #(str ":" module "/" %)]
    (str "(ns " ns-root ".frontend.events." module "\n"
         "  \"" label "的 re-frame 事件与订阅(bb new-module 生成)。状态在 app-db 的 :" module " 下。\"\n"
         "  (:require\n"
         "   [clojure.string :as str]\n"
         "   [" ns-root ".frontend.antd :as antd]\n"
         "   [" ns-root ".frontend.api." module " :as api]\n"
         "   [re-frame.core :as rf]))\n\n"
         "(def ^:private initial {:items [] :total 0 :loading? false :page 1 :size 10 :query {} :modal nil})\n\n"
         "(defn- state [db] (merge initial (:" module " db)))\n\n"
         "(defn- present\n  \"去掉空值,避免把空串发给后端的类型校验。\"\n  [m]\n"
         "  (into {} (remove (fn [[_ v]] (or (nil? v) (and (string? v) (str/blank? v))))) m))\n\n"
         "(defn- error-msg [resp]\n"
         "  (let [r (:response resp)]\n"
         "    (or (:msg r) (some-> (:humanized r) pr-str) \"请求失败\")))\n\n"
         "(rf/reg-sub " (k "state") " (fn [db _] (state db)))\n\n"
         "(rf/reg-event-fx " (k "fetch") "\n"
         "                 (fn [{:keys [db]} [_ overrides]]\n"
         "                   (let [s (merge (state db) overrides)]\n"
         "                     {:db (assoc db :" module " (assoc s :loading? true))\n"
         "                      " (k "request") " [:list (merge (present (:query s)) {:page (:page s) :size (:size s)})]})))\n\n"
         "(rf/reg-fx " (k "request") "\n"
         "           (fn [[op & args]]\n"
         "             (let [ok #(rf/dispatch [" (k "done") " op %])\n"
         "                   err #(rf/dispatch [" (k "failed") " %])]\n"
         "               (case op\n"
         "                 :list (api/list-page (first args) ok err)\n"
         "                 :create (api/create! (first args) ok err)\n"
         "                 :update (api/update! (first args) (second args) ok err)\n"
         "                 :delete (api/delete! (first args) ok err)))))\n\n")))

(defn- events-actions
  "请求结果处理、提示、搜索条件、弹窗与保存/删除。"
  [{:keys [module]}]
  (let [k #(str ":" module "/" %)]
    (str "(rf/reg-event-fx " (k "done") "\n"
         "                 (fn [{:keys [db]} [_ op {:keys [code msg data]}]]\n"
         "                   (cond\n"
         "                     (not= 200 code) {:db (assoc-in db [:" module " :loading?] false)\n"
         "                                      " (k "toast") " [:error (or msg \"操作失败\")]}\n"
         "                     (= op :list) {:db (update db :" module " merge {:items (:rows data) :total (:total data) :loading? false})}\n"
         "                     :else {:db (assoc-in db [:" module " :modal] nil)\n"
         "                            " (k "toast") " [:success msg]\n"
         "                            :dispatch [" (k "fetch") " {}]})))\n\n"
         "(rf/reg-event-fx " (k "failed") "\n"
         "                 (fn [{:keys [db]} [_ resp]]\n"
         "                   {:db (assoc-in db [:" module " :loading?] false)\n"
         "                    " (k "toast") " [:error (error-msg resp)]}))\n\n"
         "(rf/reg-fx " (k "toast") "\n"
         "           (fn [[kind text]] (if (= kind :error) (antd/error! text) (antd/success! text))))\n\n"
         "(rf/reg-event-db " (k "set-query") " (fn [db [_ field v]] (assoc-in db [:" module " :query field] v)))\n\n"
         "(rf/reg-event-fx " (k "reset-query") "\n"
         "                 (fn [{:keys [db]} _]\n"
         "                   {:db (assoc-in db [:" module " :query] {}) :dispatch [" (k "fetch") " {:page 1}]}))\n\n"
         "(rf/reg-event-db " (k "open-modal") " (fn [db [_ record]] (assoc-in db [:" module " :modal] {:record record})))\n\n"
         "(rf/reg-event-db " (k "close-modal") " (fn [db _] (assoc-in db [:" module " :modal] nil)))\n\n"
         "(rf/reg-event-fx " (k "save") "\n"
         "                 (fn [{:keys [db]} [_ values]]\n"
         "                   (let [id (get-in db [:" module " :modal :record :id])]\n"
         "                     {" (k "request") " (if id [:update id values] [:create values])})))\n\n"
         "(rf/reg-event-fx " (k "delete") " (fn [_ [_ id]] {" (k "request") " [:delete id]}))\n")))

(defn events [ctx]
  (str (events-core ctx) (events-actions ctx)))

(defn- field-literal [{:keys [col label type required?]}]
  (str "{:key :" col " :label \"" label "\" :type :" (name type) (when required? " :required? true") "}"))

(defn- page-head [{:keys [ns-root module label fields]}]
  (str "(ns " ns-root ".frontend.pages." module "\n"
       "  \"" label "页面(bb new-module 生成):搜索、分页表格、新增/编辑弹窗、删除。\"\n"
       "  (:require\n"
       "   [\"@ant-design/icons\" :refer [DeleteOutlined EditOutlined PlusOutlined ReloadOutlined SearchOutlined]]\n"
       "   [\"antd\" :refer [InputNumber]]\n"
       "   [" ns-root ".frontend.antd :as antd]\n"
       "   [" ns-root ".frontend.components.page-search :as page-search]\n"
       "   [" ns-root ".frontend.components.page-toolbar :as page-toolbar]\n"
       "   [" ns-root ".frontend.i18n :as i18n]\n"
       "   [re-frame.core :as rf]\n"
       "   [reagent.core :as r]\n"
       "   [reagent.hooks :as hooks]))\n\n"
       "(def ^:private fields\n"
       "  \"字段定义:标签、控件类型、必填都在这里调整。\"\n"
       "  [" (str/join "\n   " (map field-literal fields)) "])\n\n"
       "(def ^:private search-keys #{" (str/join " " (map #(str ":" (:col %)) (m/searchable fields))) "})\n\n"
       "(def ^:private bool-keys (set (keep #(when (= :bool (:type %)) (:key %)) fields)))\n\n"
       "(defn- ->form\n  \"后端记录 → 表单值:布尔字段 \\\"1\\\"/\\\"0\\\" → true/false。\"\n  [record]\n"
       "  (reduce (fn [m k] (update m k #(= \"1\" %))) (or record {}) bool-keys))\n\n"
       "(defn- <-form\n  \"表单值 → 请求体:空串转 nil,布尔字段转回 \\\"1\\\"/\\\"0\\\"。\"\n  [values]\n"
       "  (reduce (fn [m k] (update m k #(if % \"1\" \"0\")))\n"
       "          (update-vals values #(if (= \"\" %) nil %))\n"
       "          bool-keys))\n\n"))

(defn- page-search-part [{:keys [module]}]
  (str "(defn- search-input [{:keys [key label type]} query]\n"
       "  (let [on-change #(rf/dispatch [:" module "/set-query key %])]\n"
       "    [page-search/search-item label\n"
       "     (case type\n"
       "       :bool [antd/select {:placeholder label :style page-search/select-style :allowClear true\n"
       "                           :value (get query key) :on-change on-change}\n"
       "              [antd/select-option {:value \"1\"} \"是\"]\n"
       "              [antd/select-option {:value \"0\"} \"否\"]]\n"
       "       :int [:> InputNumber {:placeholder label :style page-search/input-style\n"
       "                             :value (get query key) :onChange on-change}]\n"
       "       [antd/input {:placeholder (str \"请输入\" label) :style page-search/input-style\n"
       "                    :type (if (= type :date) \"date\" \"text\")\n"
       "                    :value (get query key) :on-change #(on-change (.. % -target -value))}])]))\n\n"
       "(defn- search-form [query]\n"
       "  [page-search/page-search {:visible? true}\n"
       "   (into [page-search/search-row]\n"
       "         (concat (for [f fields :when (search-keys (:key f))] [search-input f query])\n"
       "                 [[page-search/search-actions\n"
       "                   [page-toolbar/search-button {:icon (r/as-element [:> SearchOutlined])\n"
       "                                                :on-click #(rf/dispatch [:" module "/fetch {:page 1}])}]\n"
       "                   [page-toolbar/reset-button {:icon (r/as-element [:> ReloadOutlined])\n"
       "                                               :on-click #(rf/dispatch [:" module "/reset-query])}]]]))])\n\n"
       "(defn- toolbar []\n"
       "  [page-toolbar/page-toolbar\n"
       "   {:left [page-toolbar/toolbar-left\n"
       "           [page-toolbar/toolbar-button {:kind :add :label (i18n/tr \"新增\") :icon (r/as-element [:> PlusOutlined])\n"
       "                                         :on-click #(rf/dispatch [:" module "/open-modal nil])}]]\n"
       "    :right [page-toolbar/toolbar-right\n"
       "            [page-toolbar/round-tool-button {:title (i18n/tr \"刷新\") :icon (r/as-element [:> ReloadOutlined])\n"
       "                                             :on-click #(rf/dispatch [:" module "/fetch {}])}]]}])\n\n"))

(defn- page-table-part [{:keys [module]}]
  (str "(defn- row-actions [^js record]\n"
       "  (let [row (js->clj record :keywordize-keys true)]\n"
       "    [antd/space\n"
       "     [antd/button {:type \"link\" :size \"small\" :icon (r/as-element [:> EditOutlined])\n"
       "                   :on-click #(rf/dispatch [:" module "/open-modal row])} (i18n/tr \"编辑\")]\n"
       "     [antd/popconfirm {:title \"确认删除这条记录?\" :onConfirm #(rf/dispatch [:" module "/delete (:id row)])}\n"
       "      [antd/button {:type \"link\" :danger true :size \"small\" :icon (r/as-element [:> DeleteOutlined])} (i18n/tr \"删除\")]]]))\n\n"
       "(defn- columns []\n"
       "  (clj->js\n"
       "   (concat\n"
       "    [{:title \"ID\" :dataIndex \"id\" :key \"id\" :width 80}]\n"
       "    (for [{:keys [key label type]} fields :when (not= type :text)]\n"
       "      (cond-> {:title label :dataIndex (name key) :key (name key)}\n"
       "        (= type :bool) (assoc :render #(if (= \"1\" %) \"是\" \"否\"))))\n"
       "    [{:title \"创建时间\" :dataIndex \"create_time\" :key \"create_time\" :width 180}\n"
       "     {:title \"操作\" :key \"action\" :width 160\n"
       "      :render (fn [_ record] (r/as-element [row-actions record]))}])))\n\n"))

(defn- page-form-part [{:keys [module label]}]
  (str "(defn- form-control [{:keys [label type]}]\n"
       "  (case type\n"
       "    :text [antd/text-area {:rows 3 :placeholder (str \"请输入\" label)}]\n"
       "    :int [:> InputNumber {:precision 0 :style {:width \"100%\"}}]\n"
       "    :decimal [:> InputNumber {:style {:width \"100%\"}}]\n"
       "    :date [antd/input {:type \"date\"}]\n"
       "    :bool [antd/switch]\n"
       "    [antd/input {:placeholder (str \"请输入\" label)}]))\n\n"
       "(defn- form-item [{:keys [key label type required?] :as field}]\n"
       "  [antd/form-item (cond-> {:label label :name (name key)}\n"
       "                    required? (assoc :rules #js [#js {:required true :message (str \"请输入\" label)}])\n"
       "                    (= type :bool) (assoc :valuePropName \"checked\"))\n"
       "   ;; 以函数调用得到 antd 控件本身:Form.Item 要把 value/onChange/id 注入到直接子元素上\n"
       "   (form-control field)])\n\n"
       "(defn- edit-modal [modal]\n"
       "  (let [[form] (antd/form-use-form)\n"
       "        editing? (some? (get-in modal [:record :id]))]\n"
       "    (hooks/use-effect\n"
       "     (fn []\n"
       "       (when modal\n"
       "         (.resetFields form)\n"
       "         (.setFieldsValue form (clj->js (->form (:record modal)))))\n"
       "       js/undefined)\n"
       "     [modal])\n"
       "    [antd/modal {:title (str (if editing? \"修改\" \"新增\") \"" label "\")\n"
       "                 :open (some? modal) :forceRender true\n"
       "                 :onOk #(.submit form)\n"
       "                 :onCancel #(rf/dispatch [:" module "/close-modal])}\n"
       "     (into [antd/form {:form form :labelCol {:span 6} :wrapperCol {:span 16}\n"
       "                       :onFinish #(rf/dispatch [:" module "/save (<-form (js->clj % :keywordize-keys true))])}]\n"
       "           (map (fn [f] [form-item f]) fields))]))\n\n"
       "(defn " module "-page []\n"
       "  (hooks/use-effect (fn [] (rf/dispatch [:" module "/fetch {}]) js/undefined) [])\n"
       "  (let [{:keys [items total loading? page size query modal]} @(rf/subscribe [:" module "/state])]\n"
       "    [:div\n"
       "     [search-form query]\n"
       "     [toolbar]\n"
       "     [antd/table {:rowKey \"id\" :loading loading? :columns (columns) :dataSource (clj->js items)\n"
       "                  :scroll #js {:x \"max-content\"}\n"
       "                  :pagination #js {:current page :pageSize size :total total :showSizeChanger true\n"
       "                                   :showTotal (fn [t] (i18n/tr \"共 {0} 条\" t))\n"
       "                                   :onChange (fn [p s] (rf/dispatch [:" module "/fetch {:page p :size s}]))}}]\n"
       "     [edit-modal modal]]))\n"))

(defn page [ctx]
  (str (page-head ctx) (page-search-part ctx) (page-table-part ctx) (page-form-part ctx)))

(defn- e2e-fill [dialog {:keys [label type]} value-expr]
  (case type
    :bool nil
    (str "  await " dialog ".getByLabel('" label "').fill(" value-expr ");\n")))

(defn- e2e-value [{:keys [type]} stamp-expr]
  (case type
    (:string :text) stamp-expr
    (:int :decimal) "'1'"
    :date "'2026-01-01'"
    nil))

(defn e2e
  "Playwright 用例:新增 → 列表出现 → 修改 → 删除。需要至少一个 string 字段来定位行。"
  [{:keys [module label fields menu-path]}]
  (let [key-field (first (filter #(= :string (:type %)) fields))
        required (filter :required? (remove #{key-field} fields))
        fills (fn [dialog stamp]
                (apply str (e2e-fill dialog key-field stamp)
                       (for [f required] (e2e-fill dialog f (e2e-value f stamp)))))]
    (str "// " label ":bb new-module 生成的端到端用例(需后端已在 3000 运行:bb e2e)\n"
         "const { test, expect } = require('playwright/test');\n"
         "const { login } = require('./auth-helper');\n\n"
         "const OK = /^(确 ?定|OK)$/;\n\n"
         "test('" label ":新增、修改、删除', async ({ page }) => {\n"
         "  await login(page);\n"
         "  await page.goto('/" menu-path "');\n"
         (if-not key-field
           "  await expect(page.locator('table')).toBeVisible();\n"
           (str "  const stamp = `e2e-${Date.now()}`;\n"
                "  await page.getByRole('button', { name: /新增/ }).first().click();\n"
                "  const add = page.getByRole('dialog', { name: '新增" label "' });\n"
                "  await expect(add).toBeVisible();\n"
                (fills "add" "stamp")
                "  await add.getByRole('button', { name: OK }).click();\n"
                "  await expect(add).toBeHidden();\n"
                "  const row = page.locator('table tbody tr', { hasText: stamp });\n"
                "  await expect(row).toBeVisible({ timeout: 10000 });\n\n"
                "  await row.getByRole('button', { name: /编辑/ }).click();\n"
                "  const edit = page.getByRole('dialog', { name: '修改" label "' });\n"
                "  await expect(edit).toBeVisible();\n"
                (e2e-fill "edit" key-field "`${stamp}-2`")
                "  await edit.getByRole('button', { name: OK }).click();\n"
                "  await expect(edit).toBeHidden();\n"
                "  const edited = page.locator('table tbody tr', { hasText: `${stamp}-2` });\n"
                "  await expect(edited).toBeVisible({ timeout: 10000 });\n\n"
                "  await edited.getByRole('button', { name: /删除/ }).click();\n"
                "  await page.getByRole('tooltip').getByRole('button', { name: OK }).click();\n"
                "  await expect(edited).toBeHidden({ timeout: 10000 });\n"))
         "});\n")))
