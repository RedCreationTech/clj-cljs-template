# clojure-template — Clojure / ClojureScript 全栈管理系统模板

一个可直接复用的 **Clojure 后端 + ClojureScript 前端** 管理后台起步模板。后端基于 **Kit 框架**（Integrant + Reitit + Ring + Undertow），前端基于 **shadow-cljs + Reagent + re-frame + Ant Design 6**，内置 RuoYi 风格的用户/角色/菜单/部门/字典/日志/定时任务等通用能力，并用 `bb new-module` 一条命令生成新的业务模块。需要 App 时还有可选的 **ClojureDart 移动端**模板（`mobile/`，Flutter + re-dash，打同一套后端接口）。

克隆后三条命令就能得到一个带业务模块、可运行的新项目：`bb rename` 改名 → `bb new-module` 生成业务模块 → `bb dev` 启动。所有日常任务都通过 babashka 统一入口（`bb tasks` 查看），macOS / Linux / Windows 通用，CI 也只调用这些任务。架构说明见 [`docs/architecture/c4-model.org`](docs/architecture/c4-model.org)（C4 模型，org-mode 可执行文档；[HTML 版](docs/architecture/c4-model.html)）。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Clojure 1.12.6, Kit 1.0.x, Integrant, Reitit 0.11, Ring 1.15, Undertow, next.jdbc / conman (HugSQL), Migratus, Malli 0.20, HikariCP 7 |
| 数据库 | SQLite 3.53（默认，零配置）；MySQL 8.4（Connector/J 26.7，切换环境变量即可；`docker-compose.yml` 提供本地实例） |
| 安全 | Buddy（JWT + bcrypt）；会话以 `sys_online` 为准（登出/强退/空闲超时即失效）+ 滑动续期；验证码、失败计数、续期宽限存 `sys_kv`，可多实例部署；登录失败限流；验证码开关（生产默认开）；CORS 白名单；上传下载防目录穿越；prod 下强制校验 `JWT_SECRET` / `COOKIE_SECRET` |
| 前端 | ClojureScript 1.12, shadow-cljs 3.5, Reagent 2.0 (函数组件 + Hooks), re-frame 1.4, React 19.3, Ant Design 6.6；中英文界面切换（`i18n/tr`）、浅色/暗色主题 |
| 任务调度 | Quartz 2.5（`sys_job` 表驱动，支持暂停/恢复/立即执行） |
| 工具链 | babashka（`bb.edn` 统一任务入口 + `bb new-module` 脚手架）, Clojure CLI (`deps.edn`), tools.build 0.10, clj-kondo 2026.08, cljfmt 0.16, cljs.test（Node）, Playwright 1.63 (E2E), cloverage, GitHub Actions |
| 移动端（可选） | [ClojureDart](https://github.com/tensegritics/ClojureDart) 0.9（Clojure → Dart）+ Flutter 3.35 + [re-dash](https://github.com/hti/re-dash) 1.2（re-frame 移植），独立工程 `mobile/`，打同一套 `{:code :msg :data}` 契约；见 [`mobile/README.md`](mobile/README.md) |

---

## 内置功能

**系统管理**：用户、角色（RBAC + 数据权限）、菜单（树形 + 按钮权限）、部门（树形）、岗位、字典、参数配置、通知公告（顶部铃铛按用户记录已读）、文件管理。

**权限控制**：
- **认证**：JWT Bearer，令牌带会话 ID（`jti`），会话存 `sys_online`：登出、强退、空闲 30 分钟后令牌立即失效；前端在令牌过半时自动续期，任何 401 都回到登录页。登录失败限流、验证码开关、默认关闭的自助注册。验证码、失败计数、续期宽限都存在数据库（`sys_kv`），多个实例共享。
- **按钮级权限**：路由用数据声明 `{:auth? true}`（要求登录）与 `{:perms "system:user:add"}`（要求权限，集合表示满足任一）；未登录 401、无权限 403。权限来自「用户 → 启用的角色 → 角色勾选的菜单/按钮（`sys_menu.perms`）」，每次请求实时计算，改角色立即生效；`admin` 角色拥有 `*:*:*`。前端按 `getInfo` 下发的 `permissions` 隐藏无权限的按钮（`:perm` / `perm/when-allowed`）。
- **数据权限**：角色的 `data_scope`（1 全部 / 2 自定义部门 / 3 本部门 / 4 本部门及以下 / 5 仅本人，多角色取并集）真正作用于用户管理：列表按范围过滤，详情、修改、删除、重置密码等先检查目标用户（与要改到的部门）是否在范围内；业务模块可复用 `domain.system.data-scope`。

**日志审计**：操作日志中间件自动记录所有 API 请求；登录日志记录 IP / 浏览器 / 结果。

**系统监控**：服务器（CPU / 内存 / JVM / 磁盘）、HikariCP 连接池、内存缓存、Integrant 组件依赖图与函数调用追踪、定时任务。

**开发工具**：`bb new-module` 业务模块脚手架（命令行，生成即可运行并自动登记）、Swagger UI、表单构建器。

---

## 项目结构

```
.
├── bb.edn                       # 统一任务入口(bb tasks 查看);实现在 bb/tasks/
├── bb/tasks/                    # dev / lint / rename / new_module 及 scaffold/ 模板
├── deps.edn                     # Clojure 依赖与别名 (:dev :test :build :coverage :nrepl :cider :fmt)
├── shadow-cljs.edn              # ClojureScript 构建 (:app -> resources/public/js)
├── package.json                 # NPM 依赖 (React 19, antd 6, Playwright)
├── build.clj                    # tools.build: uberjar
├── docker-compose.yml           # 本地 MySQL 8.4(端口 3308),bb test:mysql 自动使用
├── Dockerfile                   # 多阶段镜像
├── .github/workflows/ci.yml     # CI:lint / SQLite / MySQL / E2E / 脚手架冒烟
├── .clj-kondo/ .cljfmt.edn .lsp/ .editorconfig   # 静态检查与格式化配置
├── kit.edn                      # 项目命名空间与路径(bb 任务与改名工具从这里读取)
├── resources/
│   ├── system.edn               # Integrant 系统配置（唯一的组件装配点）
│   ├── migrations-sqlite/       # SQLite 迁移（Migratus）
│   ├── migrations/              # MySQL 迁移（与 SQLite 目录一一对应）
│   ├── sql/*.sql                # HugSQL 查询
│   └── public/index.html        # SPA 入口（js/ 由 shadow-cljs 生成）
├── src/clj/com/ruoyi/           # 后端
│   ├── core.clj                 # 入口：密钥校验 → 加载 edge / domain / routes → 启动 Integrant
│   ├── config.clj               # 读取 system.edn（aero）
│   ├── infra/                   # 基础设施：db 抽象、clock(:now 本地时间)、kv(共享键值)、json(统一编码)、errors(业务错误)、security(JWT)、secrets、online(会话)、login-guard(限流)、files、cache、scheduler
│   ├── domain/                  # 领域服务（system/*：user, role, menu, dept, ...）
│   ├── web/handler.clj          # Ring handler / 路由器 / SPA fallback
│   ├── web/response.clj         # 统一响应形状（ok / fail / deny）
│   ├── web/middleware/          # auth、operlog、exception、formats、core
│   ├── web/routes/              # Reitit 路由（api.clj 聚合）
│   └── web/controllers/         # 控制器（请求 <-> 领域服务）
├── src/cljs/com/ruoyi/frontend/ # 前端
│   ├── app.cljs                 # 入口：ConfigProvider + 路由初始化 + 渲染
│   ├── config.cljs              # 应用名称等品牌配置（复用模板改这里）
│   ├── router.cljs              # bidi 路由表 + history 集成
│   ├── db.cljs                  # re-frame 初始 app-db
│   ├── events.cljs, events/     # re-frame 事件（按领域拆分）
│   ├── subs.cljs, subs/         # re-frame 订阅
│   ├── api/                     # HTTP 客户端:transport(续期 / 401)、token、各领域 api
│   ├── i18n.cljs                # 界面多语言:(tr "中文原文"),英文词典在同一文件
│   ├── storage.cljs             # localStorage 唯一入口(ruoyi_ 前缀、不可用时降级)
│   ├── antd.cljs                # Ant Design 组件适配
│   ├── components/              # 通用组件（page-card、page-search、pagination、...）
│   └── pages/                   # 页面（layout/ 为主布局：菜单、Tab、面包屑、页面分发）
├── env/{dev,test,prod}          # 环境差异：dev 中间件、user.clj REPL 助手、logback
├── test/clj                     # 后端单元/集成测试（clojure.test）
├── test/cljs                    # 前端单元测试（cljs.test,Node 运行）
├── tests/e2e                    # Playwright 端到端测试
├── mobile/                      # 可选的 ClojureDart 移动端(独立 deps.edn + pubspec.yaml,不进 bb ci)
│   ├── deps.edn                 # ClojureDart(锁 git tag/sha) + re-dash;:cljd/opts {:kind :flutter}
│   ├── pubspec.yaml             # Dart 依赖(package:http、dev:test)
│   ├── src/ruoyi/               # main/config/json/api/fx/model/events/subs/theme/views(与网页端同分层)
│   └── test/ruoyi/              # cljd.test 单元测试(bb mobile:test,不需要后端在跑)
├── scripts/db.clj               # 数据库重置 / 迁移往返检查(bb test:mysql、bb db:roundtrip 调用)
└── docs/
    ├── architecture/c4-model.org  # C4 架构文档（Context/Container/Component/Code + 动态/部署视图）
    ├── architecture/c4-model.html # 上面 org 的自包含 HTML 版（bb docs 生成）
    ├── index.html                 # 项目文档站
    └── training/                  # 5 节入门课程（Clojure 基础 -> 前端状态 -> 后端请求流 -> 基础设施）
```

---

## 快速开始

### 环境要求

- JDK 21（Dockerfile 与 CI 以 21 为准；17 也能跑）
- [babashka](https://github.com/babashka/babashka#installation) 1.12.200+（所有任务的入口）
- Node.js 18+（CI 用 22）
- 推荐安装 [Clojure CLI](https://clojure.org/guides/install_clojure)；没装时 bb 会自动退回内置的 `bb clojure`
- 可选：Docker（`bb test:mysql` 起本地 MySQL）、clj-kondo（没装时 `bb lint` 自动下载 pod）、clojure-lsp
- 可选：[Flutter](https://docs.flutter.dev/get-started/install) 3.35+（只有做移动端 `bb mobile:*` 才需要；macOS 上 `brew install flutter` 会从源码编译，建议直接下官方 tarball）

### 1. 用模板创建新项目

```bash
git clone <this-repo> my-app && cd my-app
bb rename com.acme.myapp myapp   # 改命名空间与项目名:移动目录、改 deps/kit/build/package/localStorage 前缀等
rm -rf .git && git init && git add -A && git commit -m "init from clojure-template"
bb ci                            # lint + 格式检查 + 后端测试,确认改名后一切正常
```

`bb rename` 只做确定性的文本替换与目录移动，可重复执行；之后再改 `src/cljs/<ns>/frontend/config.cljs` 里的应用名与仓库链接。

### 2. 启动开发环境

```bash
bb dev                 # 后端 3000 / nREPL 7000 + 前端 shadow-cljs watch(9630)
bb dev --reset-db      # 先删除本地 SQLite 库(ruoyi.db)再启动
bb dev --backend-only  # 只起后端
PORT=3200 NREPL_PORT=7200 bb dev   # 端口被占用时换端口
```

首次启动自动执行迁移并写入种子数据（`admin / admin123`），打开 http://localhost:3000 即可；端口被占用会提示占用的端口与查进程的命令。`Ctrl-C` 同时停止前后端。

切换 MySQL：

```bash
docker compose up -d mysql       # 或使用已有实例
JDBC_URL="jdbc:mysql://127.0.0.1:3308/ruoyi?user=root&password=password&useSSL=false&allowPublicKeyRetrieval=true" bb dev
```

`JDBC_URL` 是 MySQL 时自动使用 `resources/migrations`（SQLite 用 `migrations-sqlite`）；也可以用 `MIGRATION_DIR` 显式指定。

`bb test:mysql` 自动使用当前 checkout 独立的 Compose 项目及数据卷；多个模板副本仍需分别设置空闲端口，例如 `MYSQL_TEST_PORT=3318 bb test:mysql`。需要手动管理同一组容器时，先明确设置 `COMPOSE_PROJECT_NAME=ruoyi-test-local`，任务与 `docker compose -p ruoyi-test-local logs/down` 使用同一个项目。显式提供 `JDBC_URL` 时任务直接使用该库并清空测试数据，应指向专用测试库。

### 3. 生成第一个业务模块

```bash
bb new-module customer --label 客户 \
  --fields "name:string:required:名称,phone:string:电话,level:int:等级,vip:bool:VIP,remark:text:备注"
bb test -n com.acme.myapp.web.controllers.customer-test   # 生成的集成测试
bb dev                                                      # 重启后菜单「业务管理 / 客户」即可用
```

详见下文「新增业务模块」。

### 4. 生产构建与部署

```bash
bb uberjar                                   # 前端 release(warning 即失败)+ target/ruoyi-standalone.jar
JWT_SECRET=$(openssl rand -hex 32) \
COOKIE_SECRET=$(openssl rand -hex 8) \
java -jar target/ruoyi-standalone.jar        # 其它配置同样由环境变量注入,见下表
```

**生产密钥是强制的**：prod profile（uberjar / Docker 镜像）下，`JWT_SECRET` 缺失、等于内置默认值或短于 32 字符，或 `COOKIE_SECRET` 缺失、等于默认值或不是 16 字节时，应用拒绝启动并打印原因与生成命令（`com.ruoyi.infra.secrets`）。dev / test 继续使用内置默认值，无需配置。

常用环境变量（完整说明见 C4 文档 §8.1）：

| 变量 | 默认 | 说明 |
|------|------|------|
| `JWT_SECRET` / `COOKIE_SECRET` | 开发值 | prod 必填，见上 |
| `JDBC_URL` / `MIGRATION_DIR` | `jdbc:sqlite:ruoyi.db` / 按 URL 推导 | 数据库连接与迁移目录 |
| `PORT` / `NREPL_PORT` | 3000 / 7000 | HTTP 与 nREPL 端口 |
| `CAPTCHA_ENABLED` | prod `true`，dev/test `false` | 登录验证码；开启后留空验证码判失败 |
| `TOKEN_TTL_MINUTES` | 720 | 令牌有效期；活跃用户在过半时自动续期 |
| `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES` | 5 / 10 | 同一用户名失败次数上限与锁定时长 |
| `REGISTER_ENABLED` | `false` | 自助注册；开启后只接受用户名密码，新用户无角色 |
| `CORS_ORIGINS` | 空（只允许同源） | 允许跨域的前端地址，逗号分隔；`*` 仅建议开发用 |
| `UPLOAD_DIR` / `UPLOAD_MAX_MB` / `UPLOAD_EXTENSIONS` | `uploads` / 10 / 内置白名单 | 上传根目录（头像在其下的 `avatar/`，下载/删除接口只能访问这个目录）、单文件大小上限与允许的扩展名（逗号分隔）；头像另限图片、2MB。请求体整体大小请在反向代理上再限制 |
| `DB_MAX_ACTIVE` | SQLite 1；MySQL 等 10 | HikariCP 最大连接数。**连接池会按方言兜底**：SQLite 一律强制 1（多连接只会互相等锁），MySQL 若还是 system.edn 里那组给 SQLite 的 1 则抬到 10；显式设过本变量或改过配置的都不动 |
| `MIGRATE_ON_INIT` | `true` | 启动时自动跑迁移；已有专门的发布流程或想避免多实例同时迁移时设为 `false` |
| `SCHEDULER_ENABLED` | `true` | 是否装载 `sys_job` 定时任务。多实例部署时只在主实例保持 `true`，其它实例设 `false`，否则同一个任务会被每个实例各跑一遍（Quartz 用的是内存 JobStore，见下） |
| `TZ` | 主机时区 | 写库与接口返回的时间都是 JVM 默认时区的本地时间（`yyyy-MM-dd HH:mm:ss`），容器里请显式设置，如 `TZ=Asia/Shanghai` |

`Dockerfile` 提供多阶段镜像构建（`clojure:temurin-21-tools-deps` 构建 → `eclipse-temurin:21-jre-alpine` 运行）；构建镜像前先执行 `bb release`，运行时用 `-e JWT_SECRET=… -e COOKIE_SECRET=…` 注入密钥。

**多实例部署**：会话、验证码、登录失败计数与锁定、续期宽限都在数据库里（`sys_online`、`sys_kv`），多个实例连同一个 MySQL 即可放在负载均衡后面，不需要 Redis 或会话粘滞。各实例请使用相同的 `TZ`、`JWT_SECRET`、`COOKIE_SECRET`。例外是定时任务：Quartz 用的是内存 JobStore，每个实例都会各自调度 `sys_job`，且某个实例上改的任务不会同步到其它实例——把除主实例以外的实例设 `SCHEDULER_ENABLED=false`（主实例同时在「定时任务」页负责增删改），需要真正集群化时改用 Quartz 的 JDBC 存储并建 11 张 `QRTZ_*` 表（见 C4 文档 §9.7）。生产环境启动时会打印配置体检结果：仍用 SQLite、或未设 `TZ` 都会给出警告。

---

## 常用命令

`bb tasks` 列出全部任务，下面是最常用的：

| 任务 | 命令 |
|------|------|
| 开发 | `bb dev`（前后端）、`bb backend` / `bb frontend`（单独起）、`bb nrepl` / `bb cider`（只起 REPL，在 REPL 里 `(user/go)`） |
| 后端测试 | `bb test`（独立的 `test.db`、端口 3100/7100，不影响正在运行的 `bb dev`；约 30 秒）；单个命名空间 `bb test -n com.ruoyi.web.handler-test`，按正则 `bb test -r '.*user.*'` |
| 前端单元测试 | `bb test:cljs`（`test/cljs` 下 `*-test` 命名空间，shadow-cljs `:test` 构建，Node 运行） |
| MySQL 测试 | `bb test:mysql`（自动 `docker compose up -d mysql`；设置 `JDBC_URL` 则用已有实例，每次先清空库） |
| 迁移往返 | `bb db:roundtrip`（up → down → up；默认临时 SQLite，设 `JDBC_URL` 则检查该库） |
| E2E | `bb e2e`（需后端已在 3000 运行；首次运行先 `npx playwright install chromium`）；单个用例 `bb e2e tests/e2e/post-crud.spec.js` |
| 功能导览录像 | `bb video:tour`（需后端 + `bb release` + ffmpeg）→ `target/tour/tour.mp4`，14 段约 16 分 56 秒（含文件管理、生产部署与 babashka 工程链），左侧常驻目录、内嵌章节与烧录台词；只重新合成用 `bb video:tour --compose-only` |
| 导览旁白配音 | `bb video:narrate`（默认小米 MiMo TTS，密钥 `export MIMO_API_KEY=…` 或写进 `target/tour/mimo.key`，不要把密钥提交进仓库）：逐句合成 273 句台词，剪掉每句首尾静音，按烧录字幕的时刻排一条整片音轨（装不下就最多提速 1.3 倍追字幕，两句之间留 0.2 秒空隙，追不上才会舍弃），画面流 copy 所以字幕/章节/目录带不变，可反复执行；语音缓存于 `target/tour/narration/`（剪静音的副本在 `narration/trimmed/`），改哪句只重合成哪句，`--force` 会连同缓存一起重灌（会计费）。没网或没额度时用离线兜底 `bb video:narrate --provider say`，只补缓存用 `--only 分镜号` |
| 移动端导览录像 | `bb video:mobile`（需 ffmpeg + Chromium）：重编网页版 → 在 3210 起**独立库**的录屏后端（`target/mobile-demo.db`，写 20 条演示岗位凑成两页）→ 录 `tests/e2e/mobile/` 四段分镜（功能/通讯方式/状态管理/主题设置）→ 拼成 `target/mobile-tour/mobile.mp4` 并配旁白。舞台页自带目录与假鼠标，台词烧在画面里；`--compose-only` 只重新合成，`--no-narrate` 不出音轨。详见 [`mobile/README.md`](mobile/README.md)「网页版与录屏」 |
| 覆盖率 | `bb coverage` → `target/coverage/index.html` |
| 移动端（可选） | `bb mobile:doctor`（查工具链与 macOS 网络权限）→ `bb mobile:create`（生成平台工程目录，幂等）→ `bb mobile:compile`（= 移动端的静态检查）→ `bb mobile:run`（编译并热重载运行）/ `bb mobile:test`（`.cljd` 单元测试，不需要后端）/ `bb mobile:clean`。这些不在 `bb ci` 里，改了 `mobile/` 单独跑；详见 [`mobile/README.md`](mobile/README.md) |
| 静态检查 | `bb lint` = `lint:kondo`（warning 即失败）+ `lint:migrations`（两套迁移成对、`--;;` 分隔、无对方方言）+ `check`（规模约束）+ `lint:pagination`（列表页表格的 `:pagination` 必须来自 `components/pagination`）+ `lint:scaffold`（`bb new-module` 的登记点自检）+ `lint:dev`（开发期约定：系统状态只有一份、`reload-exclusions` 与源码对得上）+ `lint:e2e`（Playwright 用例：不残留 `test.only`/`fixme`、分页只用 `page`/`size`、不写死 `goto('http…')`、`*.spec.js` 不 `waitForTimeout` 且必须有断言、tour与mobile录屏目录被 `testIgnore` 排除） |
| 格式化 | `bb fmt`（cljfmt 修改）/ `bb fmt:check`（只检查） |
| 构建 | `bb release`（前端）、`bb uberjar`（前端 + 后端 jar）、`bb cljs:check`（快速编译检查）、`bb patch:vendor`（给 node_modules 打补丁，见下） |
| 与 CI 相同的快速检查 | `bb ci`（lint + fmt:check + test + test:cljs） |
| 其它 | `bb new-module`、`bb rename`、`bb docs`（重新生成架构文档 HTML）、`bb clean` |

**第三方依赖补丁**：公告正文用的富文本编辑器（`react-quill-new` → Quill 2 / Parchment 3）依赖 ES class 静态方法里的 `super.create()`，而 Closure Compiler v20250407 之后会把它编译成 `Parent.create()`、丢掉 `this`，于是静态方法永远拿到父类 Blot 的 `tagName`——轻则 link/image 变成 `<span>`，重则抛 `[Parchment] Blot definition missing tagName`，公告编辑弹窗整个渲染不出来（dev 与 release 都中招）。`bb/tasks/vendor.clj` 在前端编译之前把 `node_modules` 里那 12 处改写成等价的 `Parent.create.call(this, ...)`，编译器就不会再动它。`bb release` / `bb cljs:check` 会自动打，`bb dev` / `bb frontend` / `bb e2e` 通过 `ensure-npm-deps!` 打，也可以手动 `bb patch:vendor`；补丁是幂等的，但 `npm install` 重装依赖后需要重打（所以别绕过 bb 直接跑 shadow-cljs）。命中数量与预期不符时任务会直接失败并提示：要么依赖升级了需要核对补丁表，要么上游已修复可以删掉本补丁。

REPL 助手在 `env/dev/clj/com/ruoyi/dev.clj`，`env/dev/clj/user.clj` 只挂短名字：`(user/rd)` 重载改过的命名空间（待重载集合由 tools.namespace 从文件时间戳派生，没有手抄清单；改到被组件抓住的可变容器时会自动补一次 halt+init），`(user/rr)` 重启系统且 nREPL 不断开（实测 20 个组件 136 ms，重启进程约 40 s），`(user/rs)` 看运行中的 profile/组件/连接池，`(user/q :find-user-by-name {:user_name "admin"})` 在运行中的库上跑命名查询，`(user/req :get "/system/post" :params {:page 1 :size 2})` 在进程内打真实接口（认证、权限、分页、异常中间件全都走），`(user/reset-db)` 重建数据库。详见 `AGENTS.md`「后端热重载」。

---

## 新增业务模块

### 用脚手架（推荐）

```bash
bb new-module <模块名> [--label 中文名] [--fields "字段规格,..."] [--dry-run]
```

- **模块名**：小写 kebab-case，如 `notice-board` → 表 `biz_notice_board`、接口 `/api/biz/notice-board`、页面 `/biz/notice-board`。
- **字段规格**：`name:type[:required][:标签]`，逗号分隔；`type` 可选 `string`（前 3 个可搜索字段做模糊查询）、`text`、`int`、`decimal`、`date`（`yyyy-MM-dd`）、`bool`（存 `"0"`/`"1"`）。`id`、`create_by/time`、`update_by/time` 自动生成。
- `--dry-run` 只列出将要新建和修改的文件。

一条命令生成可直接运行的完整模块：两套迁移（建表 + 共享的「业务管理」目录菜单 + 本模块菜单与查询/新增/修改/删除按钮权限，并授权 admin 角色）、HugSQL 查询、领域服务（Integrant 组件）、控制器、路由（登录校验 + `biz:<模块>:list/query/add/edit/remove` 按钮权限 + Malli 参数校验 + Swagger）、后端集成测试、前端 api / re-frame 事件 / 页面（搜索、分页表格、新增编辑弹窗、删除，按钮按权限显示）、Playwright 用例；并在 `system.edn`、`api.clj`、`core.clj`、`user.clj`、`router.cljs`、`menu_data.cljs`、`page_view.cljs`、`events.cljs`、`events/common.cljs` 的 `;; [new-module] <tag>` 标记处自动登记（标记行请保留，可反复生成多个模块）。生成前会检查文件、前端关键字与 HugSQL 查询名冲突，有冲突一个文件都不写。撤销：`git checkout . && git clean -fd`。

CI 的 `scaffold` 任务每次都会生成一个覆盖全部字段类型的模块，并要求 lint、格式、后端测试、迁移往返、前端零 warning 编译与生成的 E2E 全部通过，保证脚手架与模板同步演进。

### 手工步骤（理解脚手架做了什么）

以 `example` 模块为例（完整清单见 C4 文档「扩展指南」一节）：

1. **迁移**：`resources/migrations-sqlite/<ts>-create-example.up/down.sql` 与 `resources/migrations/` 各一份（多条语句用 `--;;` 分隔）；菜单（C）与按钮（F，`perms` 如 `biz:example:add`）通过 `INSERT INTO sys_menu` 写入并授权给角色。
2. **SQL**：`resources/sql/example.sql`（HugSQL），并加入 `system.edn` 的 `:db.sql/query-fn :filenames`。
3. **领域层**：`src/clj/com/ruoyi/domain/example.clj`，用 `defmethod ig/init-key :app.example/service` 暴露服务；在 `system.edn` 登记组件并注入到 `:reitit.routes/api`。
4. **控制器 + 路由**：`web/controllers/example.clj`、`web/routes/example.clj`（路由组 `{:auth? true}`，各接口 `:perms`），在 `web/routes/api.clj` 的 `api-routes` 追加路由组；在 `core.clj` require 新命名空间。列表接口的查询参数用 `controllers.params/query` 转成关键字键。
5. **前端**：`api/example.cljs` → `events/example.cljs` → `pages/example.cljs`（按钮用 `:perm` / `perm/when-allowed` 按权限显示）；在 `router.cljs`、`pages/layout/menu_data.cljs`、`pages/layout/page_view.cljs`、`events.cljs`、`events/common.cljs` 各登记一行。
6. **测试**：`test/clj/.../example_test.clj` + `tests/e2e/example.spec.js`。

模板不再提供页面内的代码生成器（「系统工具 → 代码生成」已移除，见 C4 文档 ADR-012）：它生成的代码不能直接运行，对 `sys_` 表「部署」还会覆盖现有模块。新模块一律用 `bb new-module`。

---

## 持续集成

`.github/workflows/ci.yml` 在 push / PR 时并行运行 6 个任务，全部只调用 bb 任务，本地可原样复现：

| 任务 | 内容 |
|------|------|
| lint | `bb lint`（clj-kondo、迁移检查、规模约束、分页约定、脚手架登记点、开发期约定、E2E 用例约定）+ `bb fmt:check` + `bb test:tasks`（模拟命令的开发任务回归） |
| test-sqlite | `bb test` + `bb db:roundtrip` |
| test-mysql | MySQL 8.4 service 上 `bb test:mysql` + `bb db:roundtrip` |
| e2e | `bb test:cljs` → `bb release`（warning 即失败）→ 启动后端 → `bb e2e`，失败时上传报告与后端日志 |
| scaffold | `bb new-module` 生成示例模块后跑 lint、格式、生成的测试、迁移往返、`bb cljs:check`、生成的 E2E |
| mobile | 装 Flutter 3.35 后 `bb mobile:compile` + `bb mobile:test` + `bb mobile:web`；独立后端运行真实Flutter登录、分页、主题、账号、空态及重试E2E并上传截图。仍不并入 `bb ci` |

公共环境（JDK 21、Clojure CLI、bb、clj-kondo、Node、依赖缓存）封装在 `.github/actions/setup`。

---

## 默认账号

| 账号 | 密码 |
|------|------|
| admin | admin123 |

---

## API 概览

| 路径 | 说明 |
|------|------|
| GET /api/auth/config | 登录页配置（是否显示验证码、是否开放注册；开发环境附演示账号） |
| POST /api/auth/login | 登录（返回 JWT；连续失败会被临时锁定） |
| POST /api/auth/refresh | 令牌续期（前端自动调用） |
| POST /api/auth/logout | 登出（会话立即失效） |
| GET /api/auth/getInfo | 当前用户信息（角色/权限标识/菜单；admin 的权限为 `["*:*:*"]`） |
| GET /api/system/{user,role,menu,dept,post,notice,config} | 系统管理 CRUD |
| GET /api/system/dict/{type,data} | 字典 |
| PUT /api/system/role/dataScope | 角色数据范围（1~5，自定义时带部门） |
| GET /api/system/notice/latest | 最新通知与当前用户的未读数（顶部铃铛，登录即可） |
| PUT /api/system/notice/read-all | 当前用户把已发布通知全部标为已读 |
| GET /api/system/{oper-log,login-log,online} | 审计与在线用户 |
| PUT /api/system/login-log/unlock/{userName} | 解除登录失败锁定 |
| POST /api/common/upload、GET /api/common/download | 通用上传下载（登录；类型与大小受限） |
| GET /api/common/avatar/{name} | 头像图片（公开，只读头像目录里的图片） |
| GET /api/system/job | 定时任务 |
| GET /api/system/{server,datasource,cache,integrant} | 监控 |
| GET /api/system/dashboard/stats | 首页统计 |
| GET/POST/PUT/DELETE /api/biz/&lt;module&gt; | `bb new-module` 生成的业务模块 |
| GET /api/health | 健康检查 |

Swagger UI：http://localhost:3000/api

---

## 开发约定

- 后端分层：`route -> controller -> domain(service) -> HugSQL query -> db`；组件全部在 `system.edn` 装配。
- API 契约：响应信封 `{:code :msg :data}`，分页 `{:total :rows}`，参数 `page`/`size`，字段 snake_case（详见 C4 文档 §6.6）；列表接口用 `controllers.params/query` 读查询参数（字符串键 → 关键字键，空串视为未填），领域层用 `domain.paging/paginate` 配对 `LIMIT/OFFSET` 列表与 `count-*` 查询。
- 错误处理：业务规则不通过由领域层抛 `(errors/fail! "中文原因")`（`ex-info`，`{:type :system.exception/business}`），`web/middleware/exception` 把它转成 **HTTP 200 + `{:code 500 :msg}`**，前端照现有契约弹出这条 `msg`；控制器只负责判断直接返回响应（`web/response` 的 `ok` / `fail` / `deny`，`deny` 用于 401/403 状态码与业务码一致的场景），**不要写 try/catch 吞异常**，意外错误（数据库、IO）由中间件出 5xx 通用文案，细节只进服务端日志。单元测试用 `test/clj/com/ruoyi/web/controller_test_helper.clj` 的 `call` / `business-msg` 走真实中间件断言响应形状。
- 权限：新接口在路由数据里声明 `:auth?` / `:perms`，对应按钮菜单（F）写进迁移；前端按钮加 `:perm` 或包 `perm/when-allowed`。前端只负责显隐，拦截以后端为准。
- 接口失败由 `api.transport` 统一提示（403、5xx、网络断开、业务码非 200）并复位 loading（`events.common/stop-all-loading` 把 app-db 里各模块的 `:loading?` 清掉，请求失败后表格不会一直转圈），调用方的 `on-error` 只做收尾，不要再各自弹「网络错误」；上传用 `t/request` 的 `:body`，带令牌下载用 `t/download!`。
- 每个 namespace ≤ 500 行、函数 ≤ 50 行（`bb check` 检查 src / env / test / bb / scripts / mobile）；超限时拆分。
- clj-kondo 零 warning、cljfmt 格式一致（`bb lint`、`bb fmt:check`，CI 强制）。
- 时间由应用生成：SQL 里写 `:now`（`infra.clock` 自动注入本地时间），不写 `CURRENT_TIMESTAMP` / `NOW()`（lint 检查）；接口里的时间统一编码为 `yyyy-MM-dd HH:mm:ss`（`infra.json`），两库一致。需要多实例共享的临时状态用 `infra.kv`，不要放 atom。
- SQL 统一放 `resources/sql/*.sql`；两套迁移目录必须同步（`bb lint:migrations` 检查）；共用 SQL 只写两库都支持的语法（如 `INSTR` 代替 `||`、派生表代替 `DUAL`）。
- 前端状态统一 re-frame；组件局部状态用 Hooks，不用 `reagent/atom`；分页参数固定 `page` / `size`。
- 列表页一律服务端分页：模块在 app-db 存 `:query-params`（含 `:page` / `:size`），取数走 `events.common/fetch-with-query`，表格的 `:pagination` 只用 `components/pagination` 的 `table-pagination`（服务端）/ `client-pagination`（本地翻页），不要在页面里手写（`bb lint:pagination` 检查）。
- 前端界面文案用 `(i18n/tr "中文原文")` 包裹、英文译文加到 `i18n.cljs`（外壳与通用组件已完成，业务页面可逐步迁移）；localStorage 只通过 `storage` 命名空间访问；内联样式的颜色用 `var(--app-*)` 变量（见 `resources/public/css/app.css`），暗色主题才能自动适配。
- 中文 docstring 描述职责、参数与返回值；docstring 紧跟函数名、在参数向量之前（`(defn f "doc" [args] …)`），里面不要写裸双引号。
- 更多 antd 6 适配与坑位清单见 `AGENTS.md`。

## 许可

[MIT](LICENSE)。RuoYi 相关设计参考 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)。
