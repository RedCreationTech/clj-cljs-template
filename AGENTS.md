# Agent Instructions

## 代码规模约束（强制 · 持续重构）

- **单个源文件（`.clj/.cljs/.cljc/.cljd`）尽量不超过 500 行**；超过必须按职责/领域拆分为多个命名空间或组件文件。
- **单个函数 / Reagent 组件不超过 50 行**（`defn`/`defn-`/`defmacro`/`defmethod`，按原始行数计，含 docstring 与空行）；超过必须抽取私有辅助函数、拆分子组件或将长逻辑下沉。
- **持续重构**：任何改动若使文件/函数超限，应在本次提交内顺手拆分，不要留待以后。
- `bb check` 检查 `src` / `env` / `test` / `bb` / `scripts` / `mobile/src` / `mobile/test` 下全部 Clojure 文件（含 `.cljd`：移动端靠命名空间拆分遵守同一套纪律），当前全部达标，CI 强制执行；超限即失败。
- 该约束与 `README.md`「开发约定」一致。

## 任务入口与质量门禁

- **所有任务走 babashka**：`bb tasks` 列出全部任务；不要再写 Makefile / shell 脚本，新任务加到 `bb.edn`，实现放 `bb/tasks/*.clj`（跨平台，Windows 也能跑）。
- **提交前**：`bb ci`（= `bb lint` + `bb fmt:check` + `bb test` + `bb test:cljs`）。`bb lint` 包含 clj-kondo（warning 即失败，配置 `.clj-kondo/config.edn`，**不含 `.cljd`**，见「移动端」）、`bb lint:migrations`、`bb check`、`bb lint:pagination`（列表页表格的 `:pagination` 必须来自 `components/pagination`，见「Frontend 组件规范」§11b）、`bb lint:scaffold`（`bb new-module` 的 `;; [new-module] <tag>` 登记点自检，不改工作区）、`bb lint:dev`（开发期约定：系统状态只有一份、`reload-exclusions` 跟得上源码，见「后端热重载」）、`bb lint:e2e`（`tests/e2e/**/*.js`：不许残留 `test.only`/`fixme`、分页参数只用 `page`/`size`、不许 `goto('http…')` 写死绝对地址、`*.spec.js` 不许 `waitForTimeout` 且必须有断言、`playwright.config.js` 必须仍然 `testIgnore` 掉 tour）；这些都是内部步骤，不是独立任务名。格式问题用 `bb fmt` 自动修复（cljfmt，配置 `.cljfmt.edn`）。
- **移动端不在 `bb ci` 里**：改了 `mobile/` 要单独跑 `bb mobile:compile`（ClojureDart 编译器就是移动端的静态检查）与 `bb mobile:test`；CI 用独立的 `mobile` job 装 Flutter 跑这两条。理由见「移动端（ClojureDart）」。
- **CI**（`.github/workflows/ci.yml`）只调用 bb 任务：lint、SQLite 测试 + 迁移往返、MySQL 8.4 / PostgreSQL 17 测试 + 迁移往返、前端 release（warning 即失败）+ E2E、`bb new-module` 脚手架冒烟、移动端编译 + 单元测试。改了任务名或参数，要同步改 CI。
- **新增业务模块用 `bb new-module`**（见 README「新增业务模块」）。源码里的 `;; [new-module] <tag>` 注释是脚手架的登记点（`system.edn` 的 components / route-services / sql-files、`web/routes/api.clj`、`router.cljs` 的 routes / page-names、`menu_data.cljs` 的 menu-keys / breadcrumbs / icons、`page_view.cljs`、`events/common.cljs` 的 tab-meta；`core.clj` 与 `events.cljs` 没有标记行，脚手架按字母序给它们补 require），**不要删除或改写这些标记行**；重构这些文件时把标记保留在对应集合的末尾。改动脚手架模板（`bb/tasks/scaffold/*.clj`）后，至少生成一个模块跑一遍 lint / fmt:check / 生成的测试 / `bb cljs:check`（CI 的 scaffold 任务会做完整检查）。
- **生产密钥**：prod profile 下 `JWT_SECRET`（≥32 字符）与 `COOKIE_SECRET`（16 字节）缺失或为内置默认值时拒绝启动（`com.ruoyi.infra.secrets`）；dev/test 用默认值即可，不要把真实密钥写进仓库。
- **第三方服务密钥同样只走环境变量**：导览旁白的 MiMo TTS 密钥（`bb video:narrate`）只从 `MIMO_API_KEY` 或 gitignore 掉的 `target/tour/mimo.key` 读取，请求失败时不要把密钥写进任何报错、任务参数或仓库文件。
- **数据库选择见 README「启动开发环境」：三套迁移须同名成对，共用查询的 nullable guard 用 `CAST(:param AS CHAR) IS NULL` 保证 PG 参数可推断。配置只走 `system.edn` + 环境变量，不要在读到配置的代码里再读 `System/getenv`**：环境相关项在 `resources/system.edn` 用 `#env`/`#profile` 声明，跨方言兜底（迁移目录、连接池）与 prod 体检由 `com.ruoyi.config` 的纯函数（`with-default-migration-dir` / `with-dialect-pool` / `prod-warnings`）在 `system-config` 里统一处理后交给 Integrant，新增这类规则请在这里加纯函数并补 `config_test`，不要在控制器里读环境变量。连接池：SQLite 强制单连接，MySQL 默认 10（`DB_MAX_ACTIVE` 可覆盖）；`system.edn` 里 `:db.sql/connection` 的池参数**必须用 HikariCP 的键名**（`:maximum-pool-size` / `:minimum-idle`），写成 `:max-active` / `:init-size` 会被 conman 静默忽略、兜底落空（`config_test` 的 `pool-keys-reach-hikari-test` 会真的建一次池来验证）；数据源监控读的是解包后的 Hikari 池，新增这类组件时要走 `infra.datasource/get-delegate`；定时任务用内存 JobStore，多实例部署时非主实例设 `SCHEDULER_ENABLED=false`；启动迁移可用 `MIGRATE_ON_INIT=false` 关闭。
- **5xx 响应不能带内部信息**：`web.middleware.exception/handler` 对 5xx 只回 `{:code :msg}`（通用文案），异常类名、URI、`ex-data`、堆栈只进服务端日志；4xx 才保留异常消息与 `ex-data` 便于前端定位。给响应加字段前先确认不会泄露路径、SQL、依赖版本等。
- **错误只有一种抛法、响应只有一种形状**：业务规则不通过一律在**领域层** `(errors/fail! "中文原因")`（`com.ruoyi.infra.errors`，抛 `{:type :system.exception/business}` 的 `ex-info`），异常中间件的 `business-handler` 转成 HTTP 200 + `{:code 500 :msg}`，与 RuoYi 前端契约一致；控制器只用 `com.ruoyi.web.response` 的 `ok`（单参是 `:data`，`msg` 固定「操作成功」；`(res/ok code msg)` 才是文案）、`fail`、`deny`（401/403，状态码=业务码），**不要在控制器里 try/catch 把异常拼进响应**，也不要再各自 `defn- ok/fail/deny`。唯一例外：CSV 逐行导入要收集每行结果（`import_export/row-failure-msg` 区分 business 与意外错误）。单元测试直接传带 `:upload-config` 等的 context，用 `test/clj/com/ruoyi/web/controller_test_helper.clj` 的 `call`/`business-msg` 经真实中间件断言响应，别只测裸返回值。写唯一性校验时先加 `find-*-by-key` 查询（共用 SQL），领域层查重后再入库，让重复键错误也有中文提示，而不是 JDBC 的 `Unique constraint` 文本。
- **函数 docstring 紧跟名字、在参数向量之前**（`(defn f "doc" [args] …)`，`defmacro`/`defmethod` 同理）；写反了 clj-kondo 会报 Misplaced docstring / Unused value。`ns` 的 docstring 若多行，结尾用 `"` 而不是 `")`，否则 `(:require …)` 被挤出 ns 表单、运行期才报 ClassNotFoundException。docstring 里**不要写裸双引号**（例如举例 `(errors/fail! "原因")`），字符串会提前结束、命名空间解析失败而 clj-kondo 只报 "Can't parse"；举例用无反引号的 `errors/fail!`，或用「」。
- **前端编译前必须打 `node_modules` 补丁**：Quill/Parchment 的 static super 会被 Closure Compiler 编译坏（公告富文本渲染不出来），补丁表在 `bb/tasks/vendor.clj`，由 `bb release` / `bb cljs:check` / `bb dev` / `bb e2e` 自动执行。手动跑 shadow-cljs 或 `npm install` 之后要先 `bb patch:vendor`；升级 quill/parchment 时补丁命中数对不上会直接失败，需要重新核对补丁表或确认上游已修复后删除。

## Non-Interactive Shell Commands

**ALWAYS use non-interactive flags** with file operations to avoid hanging on confirmation prompts.

Shell commands like `cp`, `mv`, and `rm` may be aliased to include `-i` (interactive) mode on some systems, causing the agent to hang indefinitely waiting for y/n input.

**Use these forms instead:**
```bash
# Force overwrite without prompting
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file

# For recursive operations
rm -rf directory            # NOT: rm -r directory
cp -rf source dest          # NOT: cp -r source dest
```

**Other commands that may prompt:**
- `scp` - use `-o BatchMode=yes` for non-interactive
- `ssh` - use `-o BatchMode=yes` to fail instead of prompting
- `apt-get` - use `-y` flag
- `brew` - use `HOMEBREW_NO_AUTO_UPDATE=1` env var

## 后端热重载 (nREPL)

**系统状态只有一份**：`com.ruoyi.integrant.state/system`（`com.ruoyi.core/system` 是它的别名），
`core/start-app` 写的就是它，测试与开发期助手读的也是它。`integrant.repl` 已从依赖里移除，
`bb lint:dev` 会拦住任何再次引入第二份状态的改动。

助手都在 `env/dev/clj/com/ruoyi/dev.clj`，`user.clj` 只挂手敲用的短名字。连上 nREPL（`bb dev` 默认 7000，
macOS 上 7000 被 Control Center 占用时用 `NREPL_PORT=7200 bb dev`）：

| 命令 | 作用 | 实测（2026-10-01，M 系列 macOS，JVM 已起） |
|------|------|-------------------------------------------|
| `(user/rd)` | 只重载磁盘上改过的命名空间，不重建组件 | 无改动 0 ns / 12 ms；改一个领域叶子 22 ns / 1.2 s；改 `web.response` 连带 27 个控制器/路由 1.0 s，重载完 `(req …)` 立刻看到新文案 |
| `(user/rr)` | refresh 源码 → halt（**不 halt nREPL**）→ init | 130~150 ms（20 个组件 halt+init，冷 JVM 后第一次约 200 ms），REPL 和 3000 端口都还在；重启整个进程约 40 s |
| `(user/rs)` | profile、组件数、连接池实时数字 | 20 个组件 + `sqlite:ruoyi.db` / 驱动版本，不是 unknown |
| `(user/q :find-user-by-name {:user_name "admin"})` | 在运行中的库上跑一条命名查询 | 打的是原始 SQL，不经领域层脱敏，别把结果直接回给前端 |
| `(user/req :get "/system/post" :params {:page 1 :size 2})` | 进程内打真实接口，完整走认证/权限/异常/分页中间件 | 路径不带 `/api`（助手补前缀），列表路由是 `/system/post` 而不是 `/system/post/list`，写错会被 `{id}` 路由接住变成 malli 强制转换错误；返回解析过的 body，看 `:code` / `:data`；令牌真签、会话临时登记进 `sys_online` 用完删掉，所以 `:perms` 真的生效 |
| `(user/ra)` | 与 `rd` 是同一个函数（老手敲习惯的别名） | 待重载集合本来就是派生的，没有「只重载一部分」的清单可言 |

**修改任何 `.clj` 文件后，必须连上 nREPL 跑重载命令，让运行中的后端用上新代码。**

```bash
clj-nrepl-eval -p 7200 '(user/rd)'          # 日常改逻辑
clj-nrepl-eval -p 7200 '(user/rr)'          # 改 .sql / system.edn / 路由数据
clj-nrepl-eval -p 7200 '(user/rs)'          # 看状态
clj-nrepl-eval -p 7200 '(user/reset-db)'    # 清空重建开发库
clj-nrepl-eval -p 7200 '(user/migrate)'     # 跑待处理迁移
```

- **不要再抄重载清单**：待重载集合由 tools.namespace 按文件时间戳从源码本身派生；
  `com.ruoyi.dev/reload-exclusions` 只登记「卸载重载会换掉运行期身份」的命名空间
  （`com.ruoyi.integrant.trace` / `com.ruoyi.integrant.state` / `com.ruoyi.infra.datasource`，
  为什么是这三个见该 var 的 docstring）。新增这类 ns 时 `bb lint:dev` 会要求登记；
  清单里出现不存在的、或已经不再危险的 ns 同样直接失败。
- `(user/rd)` 如果发现改到的 ns 里有被组件抓住的可变容器（atom / ref / agent / volatile!）换了对象，
  会自动补一次 halt+init，返回 `{:reloaded n :reset [...]}` —— 不需要手工换成 `(user/rr)`。
- **何时用 `(user/rr)`**：HugSQL `.sql` 变更、`resources/system.edn` 变更、路由数据 / Integrant 组件结构变更。
- **何时必须重启进程**（`bb dev` / `bb backend`）：改 `env/dev/clj` 里的助手本身（它们不在扫描目录里，
  免得重载把正在使用的函数换掉而 `user` 的短名字还指着旧那份），或改 `reload-exclusions` 里那三个 ns。
- `(user/rr)` 报 `BindException: Address already in use` 说明端口没释放，停掉 `bb dev` 重新起。

## Frontend 组件规范

### 使用 React Hooks，不用 reagent/atom

项目统一使用 Reagent 2 的函数组件 + React Hooks 管理局部状态，**禁止使用 `reagent/atom`**。

```clojure
;; ❌ 错误 — 用 reagent/atom
(let [expanded? (r/atom false)]
  [:div {:on-click #(reset! expanded? true)} ...])

;; ✅ 正确 — 用 hooks/use-state
(let [[expanded? set-expanded!] (hooks/use-state false)]
  [:div {:on-click #(set-expanded! true)} ...])
```

常用 hooks：
| Hook | 用途 |
|------|------|
| `hooks/use-state` | 局部状态（替代 r/atom） |
| `hooks/use-effect` | 副作用（替代 Form-2 的 `:component-did-mount`） |
| `hooks/use-callback` | 缓存回调函数 |
| `hooks/use-memo` | 缓存计算结果 |

**原则**：能用 re-frame subscription 的全局状态用 re-frame，组件内部局部状态用 hooks，不要用 r/atom。

### 接口、存储、文案、颜色

- **接口**：直接 require 领域命名空间，例如 `[com.ruoyi.frontend.api.users :as users-api]`（已没有 `api.cljs` 门面）。所有请求走 `api.transport/request`：它负责带令牌、令牌过半时触发 `:auth/refresh`、收到 401 时触发 `:auth/session-expired`，页面不需要自己处理登录失效。
- **失败提示统一**：其它失败（403 无权限、5xx、网络断开、HTTP 200 但业务码非 200）由 transport 派发 `:api/error` 统一弹出后端的 `msg`（文案见 `api.errors`），同时复位各模块的 `:loading?`。调用方的 `on-error` 只做收尾，**不要**再弹「网络错误」；后台请求（续期、铃铛轮询）传 `:silent? true`。上传文件用 `t/request` 的 `:body`（`js/FormData`），带令牌下载用 `t/download!`（`<a href>` 带不了令牌）。
- **按钮权限**：工具栏按钮给 `page-toolbar/toolbar-button` 传 `:perm "system:user:add"`；其它元素（行内按钮、Popconfirm、Dropdown）包一层 `[perm/when-allowed "system:user:edit" ...]`。权限集合来自 `getInfo` 的 `permissions`（sub `:auth/permissions`），admin 为 `*:*:*`。前端只负责显隐，后端路由的 `:perms` 才是拦截。
- **localStorage**：只通过 `com.ruoyi.frontend.storage`（`get-item` / `set-item!` / `get-json` / `set-json!` / `remove-item!`），键名自动加 `ruoyi_` 前缀、隐私模式下不会抛异常。
- **界面文案**：用 `(i18n/tr "中文原文")`，英文译文加到 `i18n.cljs` 的 `en-US` 词典；没有译文时显示中文，不会出现键名。带参数：`(i18n/tr "共 {0} 条" total)`。外壳（登录、头部、菜单、Tab、面包屑、通用工具栏、分页）已完成，业务页面按需逐步迁移。
- **颜色**：内联样式里的背景、文字、边框颜色用 `resources/public/css/app.css` 定义的变量（`var(--app-bg)`、`var(--app-text-regular)`、`var(--app-border-light)` 等），不要写死 `#fff` / `#606266`，否则暗色主题会出现白块或看不清的文字。增删改导入导出按钮的配色是 `--app-btn-<add|edit|delete|import|export>-{color,border,bg}`（亮 / 暗两套），自己画的按钮用 `(page-toolbar/kind-style :add)` 取同一套样式。
- **纯函数放可测试的命名空间**：不依赖浏览器 / HTTP 库的逻辑（如 `api.token`）单独成命名空间，才能在 `test/cljs` 里用 Node 跑单元测试（`bb test:cljs`）。

## 认证与会话（改动认证相关代码前先读）

- 令牌 claims：`:user-id :user-name :roles :jti :iat :exp`，`:exp` 是 Unix 秒。`:jti` 是会话 ID，对应 `sys_online.session_id`。
- **会话是否有效以 `sys_online` 为准**：`wrap-jwt-auth` 每次请求更新心跳，更新 0 行即视为未登录；登出、强退、空闲超过 30 分钟（后台清理）都会删掉会话行，令牌立即失效。续期（`/api/auth/refresh`）把会话改挂到新 `jti`，旧令牌 30 秒宽限（宽限记在 `infra.kv`，多实例共享）。
- 登录：验证码开关（`:auth-config :captcha-enabled?`，prod 默认开）→ 失败限流（`infra.login-guard`，按用户名）→ 校验密码与状态。失败提示统一为“用户名或密码错误”，不要区分“用户不存在”。
- **需要跨实例共享的临时状态放 `infra.kv`**（`put!` 带毫秒 TTL、`get-val`、`take!` 一次性读取、`del!`）：系统运行时存在 `sys_kv` 表，单元测试 / REPL 没起系统时是进程内存（测试里用 `(kv/use-store! (kv/memory-store))` 隔离）。验证码（`captcha:<uuid>`）、登录失败计数与锁定（`login-fail:` / `login-lock:`）、续期宽限（`grace:<jti>`）都在这里；不要再用 atom 存这类状态，否则多实例部署时各实例各记一份。过期键由会话清理线程每 5 分钟顺带删除。
- **鉴权由路由数据驱动**(`web.middleware.auth`,挂在 `routes/api.clj` 的顶层):`wrap-jwt-auth` 写入 `:identity`,`authorize` 读取路由数据 `:auth? true`(要求登录,可放在路由组上)与 `:perms "模块:资源:动作"`(字符串或集合,满足任一);未登录 401、无权限 403。**不要**再在路由组里挂中间件。权限按请求实时算(`domain.system.permission`),新接口的权限标识要在迁移里登记成按钮菜单(F)并授权给 admin 角色(参考 `20260924000001-add-button-perms`)。
- **数据权限**:`domain.system.data-scope` 计算当前用户可见范围(角色 `data_scope` 1~5,多角色取并集),列表查询把 `sql-params` 传给 SQL 里固定的 `AND (:scope_all = 1 OR x.dept_id IN (:v*:scope_dept_ids) OR x.user_id = :scope_user_id)`,单条操作用 `allows?` 检查(用户管理控制器是完整示例)。
- 文件上传下载要求登录,路径必须经 `infra.files/resolve-in` / `resolve-under` / `store!`,不能直接用请求里的文件名拼路径;上传先用 `files/upload-error` 按 `:upload-config`(类型白名单、大小上限)校验。**上传根目录只有一个来源**:`:upload-config` 的 `:dir`(环境变量 `UPLOAD_DIR`,默认 `uploads`),取路径用 `files/upload-dir` / `files/avatar-dir` / `files/resource-dir`,控制器通过路由数据 `(partial handler {:upload-config upload-config})` 拿到它,不要再各自 `def upload-dir` 写死。测试里直接传带 `:upload-config` 的 context,不用 `with-redefs`。
- 列表接口读查询参数用 `controllers.params/query`:reitit 的 `:query-params` 是字符串键,直接传给领域层会让筛选条件被静默忽略。
- 用户记录不带密码哈希(`find-user-by-id` 已去掉 `:password`,列表 SQL 不查);校验密码用 `user-service/password-matches?`。`update-user!` 只改给出的字段,空密码视为不修改。
- 认证相关配置都在 `system.edn` 的 `:reitit.routes/api :auth-config`，环境变量覆盖见 README。

## Frontend Ant Design 常见错误

### 1. Button 的 `:icon` 属性必须是 React 元素，不能传字符串

```clojure
;; ❌ 错误（antd 6 不接受字符串 icon）
[antd/button {:type "primary" :icon "search"} "搜索"]

;; ✅ 正确
[antd/button {:type "primary"
              :icon (r/as-element [:> SearchOutlined])}
 "搜索"]
```

### 2. Dropdown menu 的 `:label` 必须用 `r/as-element` 包裹

```clojure
;; ❌ 错误（Objects are not valid as a React child）
{:key "user_id" :label [:div {:onClick ...} [:span "用户编号"]]}

;; ✅ 正确
{:key "user_id"
 :label (r/as-element [:div {:onClick (fn [e] ...)}
                        [:span "用户编号"]])}
```

### 3. 列显隐必须检查 `:visible?` 字段，不能直接取 key

```clojure
;; ❌ 错误（取 key 返回 map，永远是 truthy）
(when (:user_id columns-config) ...)

;; ✅ 正确
(when (get-in columns-config [:user_id :visible?]) ...)
```

### 4. Drawer 的宽高用 `:size` 而不是 `:width`

```clojure
;; ❌ 错误（antd 6 告警 width deprecated）
[antd/drawer {:width 500 ...}]

;; ✅ 正确
[antd/drawer {:size "default" ...}]
;; 或 {:size "large"}
;; 需要精确宽度时用 :style
[antd/drawer {:style {:width 500} ...}]
```

### 5. 外部组件必须导入后定义，不能直接引用

```clojure
;; ❌ 错误（x.reagent_component undefined）
[antd/switch]  ;; 未在 antd.cljs 中定义

;; ✅ antd.cljs 中先定义
(def switch (r/adapt-react-class Switch))
;; 然后才能使用 [antd/switch]
```

### 6. `TextArea` 在 antd 6 中通过 `Input.TextArea` 访问

```clojure
;; ❌ 错误
["antd" :refer [TextArea]]  ;; TextArea is undefined

;; ✅ 正确
(def text-area (r/adapt-react-class (.-TextArea Input)))
```

### 7. 路由初始化必须在 app 渲染之后，且 navigate! 需检查初始化状态

路由是手写的（`frontend/router.cljs`：bidi 匹配 + `history.pushState` + `popstate` 监听），**项目里没有 accountant**（依赖已按 ADR-007 移除），不要照着老例子写 `accountant/navigate!`。

```clojure
;; navigate! 必须等 init-routes! 调用后方可执行，用 initialized? 保护
(defonce initialized? (volatile! false))

(defn navigate! [page]          ;; 只更新 URL，页面由 :navigate 事件驱动
  (when @initialized?
    (.pushState js/history nil "" (page-path page))))

(defn init-routes! []           ;; 在 app 渲染之后调用
  (.addEventListener js/window "popstate" on-popstate)
  (vreset! initialized? true)
  (rf/dispatch-sync [:navigate (or (:handler (match-route (.-pathname js/location))) :dashboard)]))
```

### 8. antd `message` 必须用 `App` 组件上下文，不能直接用静态方法

```clojure
;; ❌ 错误（antd 6 告警 Static function can not consume context）
(.success js/antd.message "创建成功")

;; ✅ 正确：在 antd.cljs 中通过 App.useApp 获取 message 实例
(def app (r/adapt-react-class App))
(defonce message-api (atom nil))

(defn use-app-message []
  (let [api (.useApp App)]
    (reset! message-api (.-message api))))

(defn success! [text]
  (if-let [api @message-api]
    (.success api text)
    (.success message text)))

;; app.cljs 中包裹应用
[:> ConfigProvider {...}
 [antd/app
  [message-init]   ;; 调用 use-app-message 的组件
  [layout/main-layout]]]
```

### 9. Card 的 `bodyStyle` 已废弃，改用 `styles.body`

```clojure
;; ❌ 错误
[antd/card {:title "xxx" :bodyStyle {:padding 12}} ...]

;; ✅ 正确
[antd/card {:title "xxx" :styles {:body {:padding 12}}} ...]
```

### 10. 自定义表单控件不要依赖 Form.Item 自动注入 value/onChange

Reagent 函数组件作为 `Form.Item` 子元素时，antd 无法像对原生 Input/Select 那样自动注入
`value` 和 `onChange`。需要手动通过 `Form.useForm` 实例读写字段。

```clojure
;; ❌ 错误（选中后表单无反应）
[antd/form-item {:label "归属部门" :name "dept_id"}
 [dept-tree-select {:placeholder "请选择"}]]

;; ✅ 正确
(let [[form] (antd/form-use-form)]
  [antd/form-item {:label "归属部门"}
   [dept-tree-select {:placeholder "请选择"
                      :value (.getFieldValue form "dept_id")
                      :on-change (fn [v]
                                   (.setFieldsValue form #js {"dept_id" v}))}]])
```

如果只是按条件选择不同的 antd 控件，不要把选择逻辑写成 Reagent 组件 `[form-control f]`，而是普通函数调用 `(form-control f)` 直接返回控件本身（`bb new-module` 生成的页面就是这样），Form.Item 才能把 `value`/`onChange`/`id` 注入到它身上。

### 11. 后端分页参数使用 `page` / `size`

前端传给后端列表接口的分页参数必须是 `page` 和 `size`，而不是 `pageNum`/`pageSize`/`page-num`/`page-size`。

```clojure
;; ✅ 正确
{:api/list-users (merge params {:page page :size size})}
```

### 11b. 列表页分页：服务端分页 + `components/pagination`（`bb lint:pagination` 强制）

每个列表模块在 app-db 里存 `:query-params`（含 `:page` / `:size` 与筛选条件），取数一律走 `events.common/fetch-with-query`：

```clojure
;; 事件：把 overrides 并进 [:posts :query-params] 后再请求，页码与筛选条件因此一直保留
(rf/reg-event-fx :posts/fetch
                 (fn [{:keys [db]} [_ overrides]]
                   (common/fetch-with-query db :posts :api/list-posts overrides)))

(rf/reg-event-fx :posts/change-page
                 (fn [_ [_ page page-size]]
                   {:dispatch [:posts/fetch {:page page :size page-size}]}))

;; 换搜索条件回到第 1 页
(rf/reg-event-fx :posts/search
                 (fn [{:keys [db]} _] {:dispatch [:posts/fetch {:page 1}]}))
```

新增/修改/删除成功之后的重新取数不要清空 `:query-params`：派发 `[:posts/fetch]`（停在当前页）或 `[:posts/search]`（回到第 1 页）都可以，两者都保留筛选条件；不要在 fx 里另写一套前端假搜索（按关键字在已取到的 `:items` 里 filter），那会绕过分页。

表格的分页属性只能来自 `components/pagination`，不要在页面里手写 `:pagination {…}`：

```clojure
;; ✅ 服务端分页（绝大多数列表页）
:pagination (pagination/table-pagination
             {:total total :page (:page query-params) :page-size (:size query-params)
              :on-change #(rf/dispatch [:posts/change-page % %2])})

;; ✅ 一次拿全量、由 antd 本地翻页（弹窗里的分配列表、字典等短数据）
:pagination (pagination/client-pagination)
```

漏掉 `:current` / `:onChange` 时页码会跳但数据不换（曾出现在操作日志、登录日志、角色、参数、定时任务、在线用户上）。手写还容易写成 `:show-total` 这类 kebab-case 属性，antd 直接忽略。后端对应约定：控制器用 `controllers.params/query` 读参数，领域层用 `com.ruoyi.domain.paging/paginate` 把 `{:page-num :page-size}` 换算成 `LIMIT/OFFSET` 并配对 `count-*` 查询，返回 `{:rows … :total …}`。

### 12. 不要同时设置 `:border` 和 `:borderColor`

React 会警告 shorthand 与非 shorthand 属性冲突，应把颜色合并到 `:border` 中。

```clojure
;; ❌ 错误
{:border "1px solid" :borderColor "#1677ff"}

;; ✅ 正确
{:border "1px solid #1677ff"}
```

### 13. Modal / Drawer 的 `:width` 已废弃，改用 `:style {:width N}`

```clojure
;; ❌ 错误
[antd/modal {:width 700 ...}]
[antd/drawer {:width 560 ...}]

;; ✅ 正确
[antd/modal {:style {:width 700} ...}]
[antd/drawer {:style {:width 560} ...}]
```

### 14. Modal / Drawer 的 `:destroyOnClose` 已废弃，改用 `:destroyOnHidden`

```clojure
;; ❌ 错误
[antd/modal {:destroyOnClose true ...}]

;; ✅ 正确
[antd/modal {:destroyOnHidden true ...}]
```

### 15. Progress 的 `strokeWidth` / `trailColor` 已废弃，改用 `size` / `railColor`

```clojure
;; ❌ 错误
[:> Progress {:percent percent :strokeWidth 10 :trailColor "#f0f0f0"}]

;; ✅ 正确
[:> Progress {:percent percent :size 10 :railColor "#f0f0f0"}]
```

## 移动端（ClojureDart）

需要 App 时用 `mobile/`：[ClojureDart](https://github.com/tensegritics/ClojureDart)（Clojure → Dart）+ Flutter Material + [re-dash](https://github.com/hti/re-dash)（re-frame 移植）。完整说明、目录分层与踩坑清单见 [`mobile/README.md`](mobile/README.md)，改动移动端前先读那一节；它在整体设计里的位置（作为容器、组件图、共用契约、选型理由与已知缺口）见 `docs/architecture/c4-model.org` 的 §2/§3/§5.3–§5.5/§7.5/§8.3/§9.8 与 ADR-013、§13 #20–#26。

- **独立工程**：`mobile/deps.edn`、`mobile/pubspec.yaml` 与根依赖表无关；根 `deps.edn` 的 `:paths` 里没有 `mobile`，所以 `bb test` / `bb uberjar` / Docker 镜像不需要 Flutter，`bb ci` 也不含移动端。改 `mobile/` 后跑 `bb mobile:compile` + `bb mobile:test`（CI 的 `mobile` job 就是这两条）。
- **静态检查是编译器，不是 clj-kondo**：clj-kondo 没有可靠的 ClojureDart 支持（`["package:http/http.dart" :as http]`、`dart/is?`、`(.-statusCode resp)`、`f/widget :watch` 都会报假错），所以 `.cljd` 不进 `bb lint:kondo`；`clj -M:cljd compile` 通过才算静态检查通过。规模约束（≤500 行 / ≤50 函数行）**对 `.cljd` 生效**（`bb check` 覆盖 `mobile/src`、`mobile/test`），移动端靠命名空间拆分（config/json/api/fx/model/events/subs/theme/views）守住这一点。
- **打的是同一套后端契约**：响应 `{:code :msg :data}`、分页 `page`/`size`、JWT + `sys_online` 会话、401 一律归到 `:auth/session-expired`、业务失败显示后端 `msg`。所以后端改响应形状时必须同时改 `mobile/src/ruoyi/api.cljd`，两边不能各自发明新信封。
- **通讯用 JSON，不用 transit**（实测：同一批列表响应体积只差 ~6%；后端虽然仍能协商 `application/transit+json`（muuntaja 默认格式表带着 `transit-clj`），但**两个客户端都不用它**，时间戳已由 `infra.json` 统一、键由移动端 `json/->edn` 转关键字，ClojureDart 侧也没有 transit 库）。要压缩上 gzip，服务端做；想彻底关掉 transit 通道见设计文档 §13 #25。
- **三条最容易踩的运行期坑**（都表现为「不报错但没效果」）：
  1. 关键字读不到 Dart 对象字段 → 写 `(.-statusCode resp)` 并加类型提示 `^http/Response`；
  2. 函数内部 `await` 过就变成异步函数，调用方漏 `await` 拿到的是 `Future`、解构全是 `nil`（`api/call`、re-dash 的 `dispatch-sync` 都是这类）；
  3. `await` 不能写在 `let` 的解构绑定里，编译失败但报错位置指向入口 ns（`=== Faulty form ===`），要先 `(let [r (await f) {:keys [a]} r] …)`。
- **所有 `reg-event-* / reg-sub / reg-fx` 必须写在会被调用的 `register!` 里**：Dart 树摇会把顶层从未调用的注册代码直接删掉，表现是运行期「Event not found」。
- **macOS 桌面必须补出站网络权限**：`flutter create` 的模板没有 `com.apple.security.network.client`，每个请求都是 `SocketException … Operation not permitted`（只在桌面/真机出现，chrome 看不出来）。`bb mobile:create` 幂等补进 `macos/Runner/*.entitlements`，`bb mobile:doctor` 负责报告。
- **接口地址只走 `--dart-define`**（`RUOYI_API_BASE_URL`、`RUOYI_REQUEST_TIMEOUT_MS`），不要写死；注意 ClojureDart 只把**键名为字面量**的那层编成 `const String.fromEnvironment`，包一层把键当参数传进去就会静默用回默认值。
- **平台工程目录（`macos/ android/ ios/ …`）不进版本库**，由 `bb mobile:create` 重新生成；`bb rename` 会搬 `mobile/src/<旧名>`、`mobile/test/<旧名>` 并改写 pubspec / deps / `.cljd` 里的命名空间。
- **无头环境验证界面**：截图与键鼠在容器/CI 里通常不可用；用 `clj -M:cljd flutter` 起的 Dart VM service 调 `ext.flutter.debugDumpApp` 打 widget 树。

## RuoYi-Vue 对照参考

参考项目：https://gitee.com/y_project/RuoYi-Vue (master 分支, Spring Boot 4.x + Vue 3)

UI 样式权威参考：https://gitee.com/y_project/RuoYi-Vue/tree/master/ruoyi-ui

### 核心要求

- **功能 1:1** — 每个功能模块必须完整实现 RuoYi-Vue 的所有交互细节
- **颜色一致** — 按钮、标签、状态颜色严格匹配 Element UI 默认色系
  - 新增: Primary 蓝 `#409eff` / 修改: Success 绿 `#67c23a` / 删除: Danger 红 `#f56c6c` / 导入: Info 灰 `#909399` / 导出: Warning 橙 `#e6a23c`
- **布局一致** — 搜索栏/工具栏/表格/分页的位置和间距与 RuoYi 一致
  - 左侧部门树 (200px) — 右侧内容区 (flex 1)
  - 搜索栏用 `:ghost true` 风格卡片
- **视觉 1:1 复刻** — 任何 UI 修改任务都必须同时验证功能与视觉，不得只确认功能可用
  - UI 样式必须优先参考 RuoYi-Vue 的 `ruoyi-ui` 源码目录，按对应页面的 `.vue` 组件、`scss/css` 样式、Element UI 组件配置、图标和 class 命名逐项追踪
  - 必须逐项对比 RuoYi 原版页面的组件边距、页面边距、组件宽高比例、字体、字号、颜色、图标、边框、圆角、阴影、对齐方式、行高、表格密度、按钮尺寸、弹窗/抽屉尺寸、分页位置等所有影响视觉观感的 UI 元素
  - 交付前需要说明已对照的 RuoYi 页面、截图或 `ruoyi-ui` 源码文件，并列出仍存在的视觉差异或确认无明显差异
- **菜单权限逻辑不可写死** — 即使 UI 任务提供了 RuoYi 系统截图作为参考，左侧菜单也只是视觉参考，不能把截图中的菜单项写成静态数据
  - 左侧菜单必须保持按当前登录用户角色/权限从接口动态获取的逻辑，代码修改不得绕过、替换或破坏权限菜单加载流程
  - 需要复刻截图中的菜单外观时，只能调整菜单容器、缩进、图标、颜色、字号、hover/active 状态等视觉样式，菜单数据来源仍必须来自后端接口
- **操作流程** — 严格按 RuoYi 的交互顺序：确认对话框 → API 调用 → 成功提示 → 刷新列表

### 功能模块对照清单

| # | 模块 | 对照要求 | 状态 |
|---|------|---------|------|
| 1 | 用户管理 | 左侧部门树 + 搜索栏(名称/手机/状态/日期) + 工具栏(新增/修改/删除/导入/导出/搜索/刷新/显隐列) + 表格(用户名可点/状态开关/更多菜单) + 新增/编辑弹窗(双列表单+部门树选+岗位角色多选) + 详情抽屉 + 重置密码 | 🟢 基本完成 |
| 2 | 角色管理 | 列表 + 新增/编辑弹窗 + 权限分配树 + 数据权限 + 用户分配 | 🟢 基本完成 |
| 3 | 菜单管理 | 树形表格 + 新增/编辑弹窗 + 图标选择器 | 🟢 基本完成 |
| 4 | 部门管理 | 树形表格 + 新增/编辑弹窗 | 🟢 基本完成 |
| 5 | 岗位管理 | 列表 + 新增/编辑弹窗 | 🟢 基本完成 |
| 6 | 字典管理 | 字典类型列表(左) + 字典数据列表(右) + 新增/编辑弹窗 | 🟢 基本完成 |
| 7 | 参数管理 | 列表 + 新增/编辑弹窗 | 🟢 基本完成 |
| 8 | 通知公告 | 列表 + 新增/编辑弹窗 | 🟢 基本完成 |
| 9 | 操作日志 | 列表 + 详情弹窗 + 清空/导出 | 🟢 基本完成 |
| 10 | 登录日志 | 列表 + 详情弹窗 + 清空/导出 | 🟢 基本完成 |
| 11 | 在线用户 | 列表 + 强退确认 | 🟢 基本完成 |
| 12 | 定时任务 | 列表 + 新增/编辑/执行一次/暂停恢复/日志 | 🟢 基本完成 |
| 13 | 代码生成 | 已移除页面生成器，改用命令行 `bb new-module`（ADR-012） | ⚪ 不做 |
| 14 | 系统接口 | Swagger 文档 | 🟢 基本完成 |
| 15 | 服务监控 | CPU/内存/JVM/磁盘可视化 | 🟢 基本完成 |
| 16 | 缓存监控 | 内存缓存键值浏览/清除 | 🟢 基本完成 |
| 17 | 表单构建 | 拖拽设计器 | 🟢 基本完成 |
| 18 | 连接池监视 | HikariCP 状态监控 | 🟢 基本完成 |

> 🟢 = 基本对齐  🟡 = 部分实现  🔴 = 未实现  ⚪ = 有意不做

## Development Workflow

### Architecture

```
Backend  (Clojure, port 3000)    — API + serves static frontend
Frontend (ClojureScript)          — SPA via shadow-cljs, compiled to resources/public/js/
Database (SQLite)                 — ruoyi.db, auto-migrated on startup
```

Both frontend and backend share port **3000**. The backend serves both API and static files.

### Database Compatibility (SQLite / MySQL)

项目同时支持 SQLite 和 MySQL，切换靠两个环境变量：

| 环境变量 | 默认值 | MySQL 用法 |
|----------|--------|------------|
| `JDBC_URL` | `jdbc:sqlite:ruoyi.db` | `jdbc:mysql://user:pass@host:port/db?useSSL=false&allowPublicKeyRetrieval=true` |
| `MIGRATION_DIR` | 按 `JDBC_URL` 推导(MySQL → `migrations`,其它 → `migrations-sqlite`) | 一般不用设 |

#### Migration 必须完全分开

DDL 差异无法兼容，因此有两套目录：

- `resources/migrations-sqlite/` —— SQLite 专用
- `resources/migrations/` —— MySQL 专用

新增/修改表时，**两个目录必须同步更新**。常见差异：

| 场景 | SQLite | MySQL |
|------|--------|-------|
| 自增主键 | `INTEGER PRIMARY KEY` | `BIGINT AUTO_INCREMENT PRIMARY KEY` |
| 时间字段 | `TEXT`（历史表带 `DEFAULT CURRENT_TIMESTAMP`，只是兜底） | `DATETIME` / `TIMESTAMP` |
| 布尔/状态 | `CHAR(1)` / `INTEGER` | `CHAR(1)` / `TINYINT` |

#### 查询 SQL 优先共用，必要时分支

业务查询统一放在 `resources/sql/*.sql`，由 `conman` 加载。原则：

1. **优先用两库都支持的语法**

   ```sql
   -- ✅ 推荐：两库都支持
   AND (:job_name IS NULL OR INSTR(job_name, :job_name) > 0)
   LIMIT :page_size OFFSET :offset
   ```

2. **禁止在共用 SQL 里写 SQLite-only 语法**

   ```sql
   -- ❌ 错误：|| 在 MySQL 默认 sql_mode 下是逻辑 OR
   AND (:user_name IS NULL OR user_name LIKE '%' || :user_name || '%')

   -- ✅ 正确
   AND (:user_name IS NULL OR INSTR(user_name, :user_name) > 0)
   ```

3. **函数名不同就提供命名变体，在 Clojure 层选择**

   例如 `resources/sql/system.sql`：

   ```sql
   -- :name last-insert-rowid :? :1
   SELECT last_insert_rowid() AS last_insert_rowid

   -- :name last-insert-rowid-mysql :? :1
   SELECT LAST_INSERT_ID() AS last_insert_rowid
   ```

   由 `com.ruoyi.infra.db/detect-db-type` 判断后调用对应名字。

4. **元数据/动态查询在 Clojure 层分支**

   `com.ruoyi.infra.db` 里对 `paginate-query`、`adapt-sql` 等按 `:sqlite` / `:mysql` 分情况处理，不要把 `PRAGMA`、`sqlite_master`、`information_schema` 混进共用 `.sql`。

#### 时间一律由应用生成（不要在 SQL 里取当前时间）

SQLite 的 `CURRENT_TIMESTAMP` 是 UTC、MySQL 的是会话时区，两库写出来的时间不一致（仪表盘曾显示“8 小时前”）。规则：

- 写时间用参数 `:now`：`create_time = :now`、`update_time = :now`。`infra.clock/with-now` 包在 query-fn 外层，**每次调用自动注入** `:now`（JVM 默认时区的本地时间，`yyyy-MM-dd HH:mm:ss`），不用手动传；需要指定时间时自己传 `:now` 即可覆盖。
- `resources/sql/*.sql` 里禁止 `CURRENT_TIMESTAMP`、`NOW()`、`datetime(`、`||`，`bb lint:migrations` 会检查（`bb new-module` 生成的 SQL 同样用 `:now`）。
- 接口返回的时间由 `com.ruoyi.infra.json` 统一编码成本地 `yyyy-MM-dd HH:mm:ss`（日期 `yyyy-MM-dd`）：MySQL 驱动返回的 `Timestamp` / `java.time` 对象与 SQLite 的文本在前端看起来一样。控制器里直接返回时间对象，不要自己 `str`。后端 JSON 编解码只用 `infra.json`（`write-str` / `read-str`，基于 jsonista，与 muuntaja 同一个 mapper），已不再依赖 cheshire。
- 部署时各实例与数据库主机用同一个时区（容器里设 `TZ`）。

#### 迁移文件格式

- 一个迁移文件里有多条语句时，语句之间用单独一行 `--;;` 分隔（MySQL 驱动一次只能执行一条语句）；不要留只有注释的分段（MySQL 会报 `Query was empty`）。
- 每个 `.up.sql` 必须有对应的 `.down.sql`，down 能把 up 完整撤销（`bb db:roundtrip` 会执行 up → down → up 验证）。
- MySQL 目录不能出现 SQLite 语法（`AUTOINCREMENT`、`DROP INDEX IF EXISTS` 等），反之亦然；`bb lint:migrations` 会检查成对、分隔与方言。

#### 修改后必须双库跑测试

任何 `resources/migrations*` 或 `resources/sql/*.sql` 改动，都要验证两套数据库：

```bash
bb lint:migrations     # 成对 / 分隔 / 方言
bb test                # SQLite(独立的 test.db,每次从空库迁移)
bb test:mysql          # MySQL:自动 docker compose up -d mysql(3308)并清空库;或设置 JDBC_URL 用已有实例
bb db:roundtrip        # 迁移往返;设 JDBC_URL=jdbc:mysql://… 则检查 MySQL(自动用 migrations 目录)
```

### Starting Dev Environment

```bash
bb dev                 # 后端(3000 / nREPL 7000)+ 前端 watch,输出带 [backend]/[frontend] 前缀
bb dev --reset-db      # 先删除 ruoyi.db 再启动
bb dev --backend-only  # 只起后端(或分别 bb backend / bb frontend)
# 首次前端编译约 1~3 分钟,之后增量编译几秒;打开 http://localhost:3000
```

端口被占用时 `bb dev` 会直接报出占用的端口；用 `PORT=3200 NREPL_PORT=7200 bb dev` 换端口。`bb test` 使用独立端口（3100/7100）与独立的 `test.db`，可以和 `bb dev` 同时运行。

### Hot-Reload Workflow

#### Backend (Clojure) — nREPL hot-reload, no restart needed

There is **one** Integrant state holder: `com.ruoyi.integrant.state/system` (aliased by
`com.ruoyi.core/system`). `core/start-app` writes it, the tests and the dev helpers read it; `integrant.repl`
is no longer a dependency and `bb lint:dev` fails if a second holder comes back.

After editing any `.clj` file, connect to the running nREPL and reload so the live backend uses the new code.
`(user/rd)` handles everything under `src/clj` (the set is derived from file timestamps, not from a hand-copied
list); `(user/rr)` additionally rebuilds the components.

| Helper | What it does | Measured cost |
|--------|--------------|---------------|
| `(user/rd)` | Reload only the namespaces that changed on disk; no component reset. Automatically does one halt+init extra when a reloaded ns swapped a mutable container (atom / ref / agent / volatile!) that components captured — result reports `{:reloaded n :reset [...]}` (observed for `controllers.system.cache`) | 0 ns / 12 ms idle; 22 ns / 1.2 s for a domain leaf; 27 ns / 1.0 s for `web.response` + every controller that requires it |
| `(user/ra)` | Same as `rd` (kept as a mnemonic; there is no separate "reload everything" list any more) | |
| `(user/rr)` | refresh source → halt **all components except `:nrepl/server`** → `ig/init` from a freshly read `system.edn` | 130~150 ms for 20 components (first call after a cold JVM ~200 ms), REPL and port 3000 both stay up (a full process restart is ~40 s) |
| `(user/rs)` | Live state: profile, component count, Hikari/SQLite pool numbers | |
| `(user/q :find-user-by-name {:user_name "admin"})` | Named HugSQL query against the running DB (raw SQL row, no domain-layer redaction — don't return it to the browser) | |
| `(user/req :get "/system/post" :params {:page 1 :size 2})` | In-process request through the real ring handler: auth, perms, exception and pagination middleware all run. Token is really signed and the session is registered in `sys_online` for the duration, so `:perms` actually applies | |

```bash
clj-nrepl-eval -p 7200 '(user/rd)'          # routine logic changes
clj-nrepl-eval -p 7200 '(user/rr)'          # .sql / system.edn / route data changes
clj-nrepl-eval -p 7200 '(user/rs)'          # look at the running system
```

Do not add hand-copied ns lists back. If a new namespace overrides an Integrant lifecycle method *and* captures
the upstream implementation (`get-method ig/init-key`), it holds runtime identity that unload/reload would replace:
register it in `com.ruoyi.dev/reload-exclusions`, and `bb lint:dev` enforces both directions (missing entries,
entries whose file is gone, entries that are no longer dangerous).

**Note**: if `(user/rr)` fails with `BindException: Address already in use`, Undertow did not release the port —
stop `bb dev` / `bb backend` and start it again.

#### Frontend (ClojureScript) — shadow-cljs auto-compiles

```bash
# shadow-cljs watch is running in background
# Edit .cljs files → watch auto-detects → incremental compile (~5s)
# Just refresh browser to see changes
```

### When to Restart (not just reload)

`(user/rr)` covers these without leaving the JVM:

- HugSQL `.sql` file changes (queries are read again by `rr`)
- `resources/system.edn` config changes
- Route data / Integrant component structure changes

Only a process restart (`bb dev` / `bb backend`) covers:

- The dev helpers themselves (`env/dev/clj`: `user.clj`, `dev.clj` — they are deliberately outside the refresh
  scan dirs, so reloading them while the REPL is using them would leave `rd`/`rr`/`q` pointing at the old code)
- The three namespaces in `com.ruoyi.dev/reload-exclusions` (`integrant.trace`, `integrant.state`,
  `infra.datasource`), which hold runtime identity the system keeps referencing

### Build Uberjar

```bash
bb uberjar             # 前端 release(编译 warning 即失败)+ 后端 standalone jar
JWT_SECRET=$(openssl rand -hex 32) COOKIE_SECRET=$(openssl rand -hex 8) \
  java -jar target/ruoyi-standalone.jar   # prod profile:缺少合格密钥会拒绝启动
```

### E2E 测试 (Playwright)

已接入 Playwright（1.63）对主要功能做端到端验证，默认跑在 `http://localhost:3000`。

```bash
npx playwright install chromium          # 安装浏览器（首次）
bb e2e                                   # 运行全部用例(先检查后端是否在 3000 运行),生成 HTML/JSON 报告
bb e2e tests/e2e/post-crud.spec.js       # 只跑一个文件
npm run test:e2e:report                  # 查看 HTML 报告
```

测试目录：`tests/e2e/`

- `auth.spec.js` — 登录/登出、密码错误提示、登出后旧令牌失效、会话失效后自动回登录页
- `navigation.spec.js` — 系统管理、系统监控、系统工具等核心菜单可访问性
- `post-crud.spec.js` — 岗位管理新增/修改/删除示例
- `permission.spec.js` — 只读用户看不到增删改按钮、越权接口统一提示;角色自定义数据范围的勾选与回显
- `search.spec.js` — 角色、岗位、参数列表的搜索条件生效
- `user-import.spec.js` — 用户导入弹窗：打开、下载 CSV 模板、提交导入、列表出现导入的用户
- `profile.spec.js` — 个人中心头像上传（换图后档案与顶栏同步）、缓存监控「清空」命中 `/system/cache/clear`
- `error-message.spec.js` — 业务失败的端到端契约：领域层 `errors/fail!` 的中文原因回到前端（HTTP 200 + `{:code 500 :msg}`），响应里不出现异常类名/SQL 等内部信息；用「参数键名重复」做例子，干净库可跑
- `header.spec.js` — 顶部菜单搜索跳转、通知铃铛未读数与已读（按用户记录，刷新后不再显示）
- `auth-helper.js` — 登录/登出公共辅助
- `<module>.spec.js` — `bb new-module` 为每个生成的模块写的新增/修改/删除用例

报告输出：`playwright-report/`

- **`bb lint:e2e` 静态守住用例约定**（并入 `bb lint`，CI 强制）：不许残留 `test.only` / `describe.only` / `test.fixme`（会静默跳过其余用例）；请求参数只用 `page` / `size`，出现 `pageNum` / `pageSize` / `page-num` / `page-size` 即失败；`page.goto('http…')` 写死绝对地址不允许（走 `baseURL`，换端口才不用改用例）；`*.spec.js` 里不许 `waitForTimeout`（固定 sleep 在慢机器上时好时坏，辅助文件 `tour-helper.js` 为了镜头节奏例外）；每条 `*.spec.js` 至少要有一处 `expect`（只点不验的用例发现不了回归）；`playwright.config.js` 必须仍然 `testIgnore` 掉 `tour`，否则 17 分钟的录像会被当成常规门禁跑。

### API Access

- Swagger UI: `http://localhost:3000/api`
- Health: `GET /api/health`
- Login: `POST /api/auth/login` with `{"username":"admin","password":"admin123"}`
- Most endpoints require `Authorization: Bearer <token>` header
