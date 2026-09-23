# clojure-template — Clojure / ClojureScript 全栈管理系统模板

一个可直接复用的 **Clojure 后端 + ClojureScript 前端** 管理后台起步模板。后端基于 **Kit 框架**（Integrant + Reitit + Ring + Undertow），前端基于 **shadow-cljs + Reagent + re-frame + Ant Design 6**，内置 RuoYi 风格的用户/角色/菜单/部门/字典/日志/定时任务/代码生成等通用能力。

克隆后只需三步就能得到一个新项目：改名（`scripts/rename-project.sh`）→ 加业务模块 → 启动。架构说明见 [`docs/architecture/c4-model.org`](docs/architecture/c4-model.org)（C4 模型，org-mode 可执行文档）。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Clojure 1.12, Kit, Integrant, Reitit, Ring, Undertow, next.jdbc / conman (HugSQL), Migratus |
| 数据库 | SQLite（默认，零配置）；MySQL（切换环境变量即可） |
| 安全 | Buddy（JWT + bcrypt），图片验证码，XSS/Frame 防护 |
| 前端 | ClojureScript, shadow-cljs, Reagent 2 (函数组件 + Hooks), re-frame, React 19, Ant Design 6 |
| 任务调度 | Quartz（`sys_job` 表驱动，支持暂停/恢复/立即执行） |
| 工具链 | Clojure CLI (`deps.edn`), tools.build, babashka (`bb.edn`), Playwright (E2E), cloverage |

---

## 内置功能

**系统管理**：用户、角色（RBAC + 数据权限）、菜单（树形 + 按钮权限）、部门（树形）、岗位、字典、参数配置、通知公告、文件管理。

**权限控制**：JWT Bearer 认证；按角色动态下发菜单树与权限标识（`perms`）；在线用户与强制下线。按钮级 `perms` 校验与数据权限（`data_scope` 1~5）的 SQL 片段生成器已就位但尚未接入路由/查询，接入方式见 C4 文档 §13。

**日志审计**：操作日志中间件自动记录所有 API 请求；登录日志记录 IP / 浏览器 / 结果。

**系统监控**：服务器（CPU / 内存 / JVM / 磁盘）、HikariCP 连接池、内存缓存、Integrant 组件依赖图与函数调用追踪、定时任务。

**开发工具**：代码生成器（按表结构生成 SQL + 后端 controller + 前端页面）、Swagger UI、表单构建器。

---

## 项目结构

```
.
├── deps.edn                     # Clojure 依赖与别名 (:dev :test :build :coverage :nrepl :cider)
├── shadow-cljs.edn              # ClojureScript 构建 (:app -> resources/public/js)
├── package.json                 # NPM 依赖 (React 19, antd 6, Playwright)
├── bb.edn / Makefile            # 常用任务入口
├── build.clj                    # tools.build: uberjar
├── kit.edn                      # Kit 生成器配置
├── resources/
│   ├── system.edn               # Integrant 系统配置（唯一的组件装配点）
│   ├── migrations-sqlite/       # SQLite 迁移（Migratus）
│   ├── migrations/              # MySQL 迁移（与 SQLite 目录一一对应）
│   ├── sql/*.sql                # HugSQL 查询
│   └── public/index.html        # SPA 入口（js/ 由 shadow-cljs 生成）
├── src/clj/com/ruoyi/           # 后端
│   ├── core.clj                 # 入口：加载 edge / domain / routes 并启动 Integrant
│   ├── config.clj               # 读取 system.edn（aero）
│   ├── infra/                   # 基础设施：db 抽象、security(JWT)、online、data-perm、cache、scheduler
│   ├── domain/                  # 领域服务（system/*：user, role, menu, dept, ...；gen）
│   ├── web/handler.clj          # Ring handler / 路由器 / SPA fallback
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
│   ├── api.cljs, api/           # HTTP 客户端（transport + 各领域 api）
│   ├── antd.cljs                # Ant Design 组件适配
│   ├── components/              # 通用组件（page-card、page-search、pagination、...）
│   └── pages/                   # 页面（layout/ 为主布局：菜单、Tab、面包屑、页面分发）
├── env/{dev,test,prod}          # 环境差异：dev 中间件、user.clj REPL 助手、logback
├── test/clj                     # 后端单元/集成测试（clojure.test）
├── tests/e2e                    # Playwright 端到端测试
├── scripts/                     # rename-project.sh、check_constraints.py
└── docs/
    ├── architecture/c4-model.org  # C4 架构文档（Context/Container/Component/Code + 动态/部署视图）
    ├── index.html                 # 项目文档站
    └── training/                  # 5 节入门课程（Clojure 基础 -> 前端状态 -> 后端请求流 -> 基础设施）
```

---

## 快速开始

### 环境要求

- JDK 17+（推荐 21）
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Node.js 18+
- 可选：babashka（`bb` 任务）、clj-kondo、clojure-lsp

### 1. 用模板创建新项目

```bash
git clone <this-repo> my-app && cd my-app
# 把 com.ruoyi / rouyi 改成你的命名空间与项目名（会移动目录、改 deps/kit/build/package 等）
./scripts/rename-project.sh com.acme.myapp myapp
rm -rf .git && git init && git add -A && git commit -m "init from clojure-template"
```

`scripts/rename-project.sh` 只做确定性的文本替换与目录移动；执行后请跑一遍 `clojure -M:test` 与 `npx shadow-cljs compile app` 确认。

### 2. 启动后端

```bash
clojure -M:dev -m com.ruoyi.core      # 改名后为 -m <your-ns>.core
```

默认监听 http://localhost:3000，nREPL 7000；首次启动自动执行 SQLite 迁移并写入种子数据（`admin / admin123`）。

切换 MySQL：

```bash
JDBC_URL="jdbc:mysql://user:pass@host:3306/myapp?useSSL=false&allowPublicKeyRetrieval=true" \
MIGRATION_DIR=migrations \
clojure -M:dev -m com.ruoyi.core
```

### 3. 启动前端

```bash
npm install
npx shadow-cljs watch app             # 增量编译到 resources/public/js/
```

前端由后端同一端口提供，打开 http://localhost:3000 即可。`./start_dev.sh` 可一键拉起前后端。

### 4. 生产构建

```bash
npx shadow-cljs release app           # 前端发布包
clojure -T:build all                  # uberjar（含前端静态文件）
java -jar target/rouyi-standalone.jar # PORT / JDBC_URL / MIGRATION_DIR 等由环境变量注入
```

`Dockerfile` 提供多阶段镜像构建；`bb uberjar` / `make uberjar` 等价。

---

## 常用命令

| 任务 | 命令 |
|------|------|
| 后端测试 | `clojure -M:test`（单个命名空间：`clojure -M:test -n com.ruoyi.web.handler-test`） |
| 覆盖率 | `clojure -M:coverage` → `target/coverage/index.html` |
| E2E | `npx playwright install chromium && npm run test:e2e`（需后端已在 3000 运行） |
| 规模约束检查 | `python3 scripts/check_constraints.py`（命名空间 ≤500 行、函数 ≤50 行） |
| 格式化 | `clojure-lsp format` 或 `bb format` |
| nREPL / CIDER | `clojure -M:dev:nrepl` / `clojure -M:dev:cider` |

REPL 热重载助手在 `env/dev/clj/user.clj`：`(user/rd)` 重载领域层，`(user/rroutes)` 重载路由，`(user/rr)` 重启 Integrant 系统，`(user/reset-db)` 重建数据库。详见 `AGENTS.md`。

---

## 如何新增一个业务模块

以 `example` 模块为例（完整清单也写在 C4 文档「扩展指南」一节）：

1. **迁移**：`resources/migrations-sqlite/<ts>-create-example.up/down.sql` 与 `resources/migrations/` 各一份；菜单通过 `INSERT INTO sys_menu` 写入。
2. **SQL**：`resources/sql/example.sql`（HugSQL），并加入 `system.edn` 的 `:db.sql/query-fn :filenames`。
3. **领域层**：`src/clj/com/ruoyi/domain/example.clj`，用 `defmethod ig/init-key :app.example/service` 暴露服务；在 `system.edn` 登记组件并注入到 `:reitit.routes/api`。
4. **控制器 + 路由**：`web/controllers/example.clj`、`web/routes/example.clj`，在 `web/routes/api.clj` 的 `api-routes` 追加 `(example/example-routes opts)`；在 `core.clj` require 新命名空间。
5. **前端**：`api/example.cljs` → `events/example.cljs` → `subs/`（如需）→ `pages/example/*.cljs`；在 `router.cljs`、`pages/layout/menu_data.cljs`、`pages/layout/page_view.cljs`、`events/common.cljs` 各登记一行。
6. **测试**：`test/clj/.../example_test.clj` + `tests/e2e/example.spec.js`。

也可以用内置代码生成器（系统工具 → 代码生成）从表结构一键生成第 2~5 步的骨架。

---

## 默认账号

| 账号 | 密码 |
|------|------|
| admin | admin123 |

---

## API 概览

| 路径 | 说明 |
|------|------|
| POST /api/auth/login | 登录（返回 JWT） |
| GET /api/auth/getInfo | 当前用户信息（角色/权限/菜单） |
| GET /api/system/{user,role,menu,dept,post,notice,config} | 系统管理 CRUD |
| GET /api/system/dict/{type,data} | 字典 |
| GET /api/system/{oper-log,login-log,online} | 审计与在线用户 |
| GET /api/system/job | 定时任务 |
| GET /api/system/{server,datasource,cache,integrant} | 监控 |
| GET /api/system/dashboard/stats | 首页统计 |
| GET /api/tool/gen/{tables,preview} | 代码生成 |
| GET /api/health | 健康检查 |

Swagger UI：http://localhost:3000/api

---

## 开发约定

- 后端分层：`route -> controller -> domain(service) -> HugSQL query -> db`；组件全部在 `system.edn` 装配。
- API 契约：响应信封 `{:code :msg :data}`，分页 `{:total :rows}`，参数 `page`/`size`，字段 snake_case（详见 C4 文档 §6.6）。
- 每个 namespace ≤ 500 行、函数 ≤ 50 行（`scripts/check_constraints.py` 检查）；超限时拆分。
- SQL 统一放 `resources/sql/*.sql`；两套迁移目录必须同步；共用 SQL 只写两库都支持的语法（如 `INSTR` 代替 `||`）。
- 前端状态统一 re-frame；组件局部状态用 Hooks，不用 `reagent/atom`；分页参数固定 `page` / `size`。
- 中文 docstring 描述职责、参数与返回值。
- 更多 antd 6 适配与坑位清单见 `AGENTS.md`。

## 许可

模板本身按仓库根目录 LICENSE（若无则视为 MIT）发布；RuoYi 相关设计参考 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)。
