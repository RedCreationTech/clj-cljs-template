# Project Instructions for AI Agents

本文件是 AI 编码助手的入口说明。**权威、详细的项目约定见同目录下的 [`AGENTS.md`](./AGENTS.md)**;本文件仅作指针与快速上手,避免两处重复维护。

## 权威指令
- 全部项目约定、前端组件规范、antd 常见坑、数据库双库兼容、热重载与构建流程:见 [AGENTS.md](./AGENTS.md)。
- 项目背景、技术栈、模块与 API 概览、新增业务模块步骤:见 [README.md](./README.md)。
- 架构(C4 模型:Context / Container / Component / Code + 动态与部署视图):见 [docs/architecture/c4-model.org](./docs/architecture/c4-model.org)。新增模块或改动组件装配后,同步更新该文档对应小节。

## Build & Test(快速命令)

所有任务走 babashka,`bb tasks` 查看全部。

```bash
bb dev                    # 后端 3000 / nREPL 7000 + 前端 watch(--reset-db 清空本地库)
bb test                   # 后端测试(独立 test.db);单个命名空间:bb test -n com.ruoyi.web.handler-test
bb test:mysql             # MySQL 上跑测试(docker compose 或 JDBC_URL)
bb e2e                    # Playwright(需后端已在 3000 运行)
bb ci                     # 提交前:lint(clj-kondo + 迁移 + 规模约束)+ fmt:check + test
bb fmt                    # cljfmt 自动格式化
bb new-module <名称> --label 中文名 --fields "title:string:required:标题,..."   # 生成 CRUD 模块
```

## Clojure 编辑约定
- 编辑任意 `.clj/.cljs/.cljc/.cljd/.edn` 时保证括号平衡;若环境提供 `clojure-bracket-guard` 之类的 `safe-edit` / `validate` 工具,优先使用。
- 提交前 `bb fmt` 统一格式、`bb lint` 保持 clj-kondo 零 warning。
- 源码中的 `;; [new-module] <tag>` 注释是 `bb new-module` 的登记点,不要删除。

## Git 策略
- 未获明确指示不擅自 commit / push;收到指示后按常规流程提交并推送(不强制推送到 main)。
