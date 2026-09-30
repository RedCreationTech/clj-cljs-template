// 分镜 01 开场 · 全栈模板总览
// 分镜 02 登录与会话安全
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

test('01｜开场：一个进程跑完整栈', async ({ page }) => {
  await t.open(page);

  await t.chapter(page, {
    n: 1,
    title: 'Clojure / ClojureScript 全栈管理后台',
    subtitle: 'RuoYi 功能 1:1 复刻 · 单进程 · 双数据库',
    points: [
      '后端：Kit + Integrant + Reitit + next.jdbc/HugSQL + Malli + Quartz',
      '前端：shadow-cljs + Reagent 2（React Hooks）+ re-frame + Ant Design 6',
      '数据：SQLite 与 MySQL 8.4 共用一套业务 SQL，两套迁移目录',
      '工程：babashka 统一任务入口，bb ci 一次跑完 lint / 测试 / 前端 / E2E',
    ],
  });

  await t.say(page, '同一个 3000 端口，既提供 REST 接口，也直接托管编译好的前端页面。');
  await t.say(page, '这里是仪表盘：所有数字都来自后端聚合接口，不是写死的静态卡片。');

  await t.step(page, '顶部欢迎卡按小时给问候语，右侧时钟每秒刷新一次。', () =>
    expect(page.locator('text=/当前时间/')).toBeVisible()
  );

  await t.say(page, '四张统计卡：用户总量、在线用户、操作日志、定时任务，一次请求全部拿回。');
  await t.pan(page, 320);
  await t.say(page, '「快捷操作」是八个常用模块的入口，点一下就跳到对应路由。');
  await t.say(page, '「最近操作」读的是操作日志表，显示谁在什么时候做了什么。');
  await t.say(page, '右侧「系统信息」直接读运行时：操作系统、Java 版本、JVM 内存、运行时长。');

  await t.pan(page, -420);
  await t.say(page, '左侧菜单不是静态数据：它按当前登录角色的权限从接口动态加载。');

  await t.say(page, '菜单多了以后顶部这个搜索框比逐级点开快：候选项同样只来自当前用户能访问的页面。');
  await t.step(page, '点搜索图标，输入「岗位」。', async () => {
    await page.locator('.ant-layout-header .anticon-search').first().click();
    await t.wait(page, 500);
    const popover = page.locator('.ant-popover:visible').last();
    await popover.locator('input[role=combobox]').first().fill('岗位');
    await t.wait(page, 600);
  });
  await t.step(page, '按钮类菜单不进候选，所以这里只出现「岗位管理」。', async () => {
    await t.pickOption(page, '岗位管理');
  });
  await t.settle(page, 1000);
  await t.step(page, '选中即跳转，页签与面包屑一起跟上。', async () => {
    await expect(page.locator('.tab-item', { hasText: '岗位管理' }).first()).toBeVisible();
    await expect(page.getByPlaceholder('请输入岗位名称')).toBeVisible();
  });
  await t.say(page, '跳转走的是同一个路由表，越权的菜单既搜不到也进不去。');
  await t.step(page, '回到仪表盘。', async () => {
    await page.locator('.tab-item', { hasText: '首页' }).first().click();
    await t.settle(page, 800);
  });

  await t.say(page, '下面从登录与安全开始，逐段演示模板的全部能力。');
});

test('02｜登录、令牌与会话安全', async ({ page }) => {
  await t.signOut(page);

  await t.chapter(page, {
    n: 2,
    title: '登录与会话安全',
    subtitle: '验证码 · 失败限流 · JWT · 在线会话',
    points: [
      '登录：验证码开关 + 按用户名的失败限流与锁定',
      '令牌 claims：user-id / roles / jti / iat / exp，会话 ID 就是 jti',
      '会话有效性以 sys_online 为准：登出、强退、空闲 30 分钟立即失效',
      '令牌过期前自动续期，旧令牌保留 30 秒宽限',
    ],
  });

  await t.say(page, '开发环境默认关验证码，生产环境在 system.edn 里打开开关即可。');
  await t.step(page, '先故意输错一次密码，看看统一的安全提示。', () =>
    page.getByPlaceholder('密码').fill('wrong-password')
  );
  await t.step(page, '提交。', () => page.getByRole('button', { name: /登\s*录/ }).click());
  await t.step(page, '无论是用户名不存在还是密码错误，提示都是同一句话，不泄露账号是否存在。', null, {
    lead: 200,
    after: 1600,
  });
  await expect(page.getByText(/用户名或密码错误/)).toBeVisible();

  await t.step(page, '改成正确密码再登录。', async () => {
    await page.getByPlaceholder('密码').fill('admin123');
    await page.getByRole('button', { name: /登\s*录/ }).click();
  });
  await t.settle(page, 900);
  await expect(page.locator('.ant-layout-sider').first()).toBeVisible();

  await t.say(page, '登录成功后浏览器只拿到一个 JWT，密码哈希从不离开数据库。');
  await t.say(page, '个人中心从右上角头像进入。');
  await t.step(page, '头像下拉里有个人资料、布局设置和退出登录。', async () => {
    await page.locator('.ant-layout-header').getByText('管理员').click();
    await page.getByRole('menuitem', { name: '个人中心' }).click();
  });
  await t.settle(page, 900);
  await t.say(page, '这里是个人资料、改密码和头像上传。');
  await t.say(page, '头像走统一的文件命名空间存储，路径经过白名单校验，不直接使用请求里的文件名。');

  await t.quiet(page, 400);
  await t.say(page, '最后登出：服务端删掉在线会话行，手里的令牌立刻失效，下次请求自动回到登录页。');
  await t.logout(page);
  await expect(page.getByPlaceholder('用户名')).toBeVisible();
});
