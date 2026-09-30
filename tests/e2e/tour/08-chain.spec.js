// 分镜 14 工程链：bb tasks · bb new-module · bb ci · 双库 · 前端编译 · Uberjar · 生产密钥体检
//
// 这一段不点页面，用终端卡回放录制前真跑过的命令与输出（数字全部来自本机这轮 `bb ci`
// / `bb test:mysql` / `bb db:roundtrip` / `bb new-module --dry-run` / `bb uberjar`）。
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

test('14｜工程链：从 bb new-module 到 Uberjar', async ({ page }) => {
  await t.open(page);

  await t.chapter(page, {
    n: 14,
    title: '工程链：babashka 到底层门禁',
    subtitle: 'bb tasks · new-module · ci · 双库测试 · release · uberjar · 密钥体检',
    points: [
      '所有任务收在 bb.edn，一条 bb tasks 列全，Windows 也能跑',
      'bb new-module 一次生成前后端、迁移、路由、菜单与 E2E 用例',
      'bb ci = clj-kondo + 迁移 + 规模 + 分页 + 格式 + 双端测试',
      'bb test:mysql 与 bb db:roundtrip 把 MySQL 8.4 与迁移往返也钉住',
      'bb release 把编译 warning 当错误；prod 缺密钥直接拒绝启动',
    ],
  });

  await t.say(page, '最后一段不讲页面，讲这套代码怎么长出来：任务入口只有 babashka 一个，没有 Makefile，也没有 shell 脚本。');
  await t.terminal(page, {
    label: 'babashka — bb tasks',
    rows: [
      ['bb tasks',
       'dev          一键启动后端(3000 / nREPL 7000)+ 前端 watch',
       'new-module   生成一个完整 CRUD 模块并自动登记',
       'test / test:cljs / test:mysql / db:roundtrip',
       'lint / check / fmt:check / lint:pagination',
       'release / uberjar / e2e / video:tour / **ci**',
       '**共 28 个任务，CI 只调用同一批任务**'],
    ],
  });

  await t.say(page, '新增业务模块不是复制粘贴：一条命令把两库迁移、HugSQL、领域、控制器、路由、前端页面与事件、测试和 E2E 全生成，并在各处登记点插好注册代码。');
  await t.terminal(page, {
    label: 'babashka — bb new-module contract --dry-run',
    rows: [
      ['bb new-module contract --label 合同 \\',
       '  --fields "code:string:required:合同号,amount:decimal:金额,due_on:date:到期日,paid:bool:已支付" --dry-run',
       '【试运行,未写入】模块 contract(合同)',
       '  新建:',
       '    resources/migrations{,-sqlite}/…-create-biz-contract.{up,down}.sql',
       '    resources/sql/contract.sql',
       '    src/clj/com/ruoyi/**domain**/contract.clj',
       '    src/clj/com/ruoyi/web/**controllers**/contract.clj · routes/contract.clj',
       '    src/cljs/com/ruoyi/frontend/{api,events,pages}/contract.cljs',
       '    test/clj/…/contract_test.clj · **tests/e2e/contract.spec.js**',
       '  登记:',
       '    resources/system.edn · web/routes/api.clj · frontend/router.cljs',
       '    menu_data.cljs · page_view.cljs · events.cljs …',
       '**缺标记或有冲突时一个文件都不写；--dry-run 只在内存里算一遍**'],
    ],
  });

  await t.say(page, '提交前的门禁是一条命令：静态检查、迁移方言、规模约束、分页约定、格式，再到后端与前端测试。');
  await t.terminal(page, {
    label: 'babashka — bb ci',
    rows: [
      ['bb ci',
       'linting took 2363ms, **errors: 0, warnings: 0**',
       '✔ 迁移检查通过: 23 组迁移,SQLite/MySQL 成对且语法干净;共用查询无单库写法',
       '检查 254 个文件、1251 个函数;最大文件 430 行,最长函数 50 行',
       '✔ 规模约束通过',
       '✔ 分页约定通过: 34 个页面表格的分页属性都来自 components/pagination',
       'All source files formatted correctly',
       'Ran **376** tests containing **1179** assertions.',
       '**0 failures, 0 errors.**  ; cljs **25** tests **90** assertions → 0 failures'],
    ],
  });

  await t.say(page, '同一套业务 SQL 要在两种数据库上验证：bb test:mysql 自动拉起 MySQL 8.4 并把库清空重跑；bb db:roundtrip 把每条迁移执行 up → down → up。');
  await t.terminal(page, {
    label: 'babashka — 双库与迁移往返',
    rows: [
      ['bb test:mysql',
       'Container ruoyi-mysql  Running   (MySQL 8.4, 127.0.0.1:3308)',
       'Ran **376** tests containing **1179** assertions.',
       '**0 failures, 0 errors.**'],
      ['bb db:roundtrip',
       'migrations-sqlite: up 后 **21** 张 sys_ 表,down+up 后 **21** 张,待执行迁移 0',
       'JDBC_URL=jdbc:mysql://… bb db:roundtrip   # 同一套检查走 migrations 目录'],
    ],
  });

  await t.say(page, '前端编译把 warning 当错误，所以 Closure 的输出永远是 0 warnings；bb uberjar 打出一个 jar，接口和页面共用 3000 端口。');
  await t.terminal(page, {
    label: 'babashka — bb release / bb uberjar',
    rows: [
      ['bb release',
       '[:app] Build completed. (4049 files, 34 compiled, **0 warnings**, 20.35s)'],
      ['bb uberjar',
       'target/**ruoyi-standalone.jar**   # 71 MB，前端产物已经打进 jar',
       'java -jar target/ruoyi-standalone.jar   # 一条命令起整套系统'],
    ],
  });

  await t.say(page, '最后是生产体检：prod profile 下密钥缺失或还是模板默认值，进程直接拒绝启动，不会带着弱密钥上线。');
  await t.terminal(page, {
    label: 'ruoyi-standalone.jar — env=prod',
    rows: [
      ['java -jar target/ruoyi-standalone.jar   # 不给密钥',
       'ERROR com.ruoyi.core - 启动失败',
       '**生产环境密钥配置不安全,拒绝启动:**',
       '  - 未设置 JWT_SECRET',
       '  - 未设置 COOKIE_SECRET',
       '可以这样生成:',
       '  export JWT_SECRET=$(openssl rand -hex 32)',
       '  export COOKIE_SECRET=$(openssl rand -hex 8)'],
      ['JWT_SECRET=$(openssl rand -hex 32) COOKIE_SECRET=$(openssl rand -hex 8) java -jar target/ruoyi-standalone.jar',
       'WARN com.ruoyi.core - 生产环境仍在用 SQLite 文件库:写操作全部串行,多实例部署会互相锁住。请设置 JDBC_URL 连到 MySQL。',
       'INFO kit.edge.server.undertow - **server started on port 3210**',
       '**=-[ruoyi started successfully]=-**'],
    ],
  });

  await t.say(page, '十四段讲完了：22 个页面、两套数据库、一条 bb ci。把它当自己项目的起点，改名之后就开工。');
  await t.step(page, '回到仪表盘，导览结束。', async () => {
    await t.open(page);
    await expect(page.getByText('在线用户').first()).toBeVisible();
  });
});
