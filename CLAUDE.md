# Project Instructions for AI Agents

本文件是 AI 编码助手的入口说明。**权威、详细的项目约定见同目录下的 [`AGENTS.md`](./AGENTS.md)**;本文件仅作指针与快速上手,避免两处重复维护。

## 权威指令
- 全部项目约定、前端组件规范、antd 常见坑、数据库双库兼容、热重载与构建流程:见 [AGENTS.md](./AGENTS.md)。
- 项目背景、技术栈、模块与 API 概览、新增业务模块步骤:见 [README.md](./README.md)。
- 架构(C4 模型:Context / Container / Component / Code + 动态与部署视图):见 [docs/architecture/c4-model.org](./docs/architecture/c4-model.org)。新增模块或改动组件装配后,同步更新该文档对应小节。

## Build & Test(快速命令)

```bash
# 后端(port 3000 / nREPL 7000,SQLite 自动迁移)
clojure -M:dev -m com.ruoyi.core

# 前端 watch(增量编译到 resources/public/js/)
npx shadow-cljs watch app

# 后端单元测试(cognitect test-runner;用 -n 指定命名空间,勿用 -X :ns-regexes)
clojure -M:test
clojure -M:test -n com.ruoyi.web.handler-test

# 前端 E2E(Playwright;需后端已在 3000 运行)
npx playwright test

# 规模约束(命名空间 ≤500 行 / 函数 ≤50 行)
python3 scripts/check_constraints.py
```

## Clojure 编辑约定
- 编辑任意 `.clj/.cljs/.cljc/.cljd/.edn` 时保证括号平衡;若环境提供 `clojure-bracket-guard` 之类的 `safe-edit` / `validate` 工具,优先使用。
- 提交前用 `clojure-lsp format --filenames <files>` 统一格式。

## Git 策略
- 未获明确指示不擅自 commit / push;收到指示后按常规流程提交并推送(不强制推送到 main)。
