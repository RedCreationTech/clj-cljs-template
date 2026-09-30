// 分镜 09 审计与会话：操作日志 · 登录日志 · 在线用户
// 分镜 10 定时任务：Cron 调度、立即执行与执行日志
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

const SUFFIX = String(Date.now()).slice(-6);
const TOUR_JOB = `导览任务${SUFFIX}`;

const rows = (page, text) => page.locator('.ant-table-tbody tr.ant-table-row', { hasText: text });

const modal = (page) => page.locator('.ant-modal-container:visible');
const modalInput = (page, n) => modal(page).locator('input').nth(n);
const confirmDialog = (page) => modal(page).getByRole('button', { name: /确\s*定/ });
const confirmPop = (page) =>
  page.locator('.ant-popconfirm:visible, .ant-popover:visible').getByRole('button', { name: /确\s*定/ });

/** 删掉历史失败运行残留的演示任务与其日志。 */
async function cleanLeftovers(page) {
  const body = await t.api(page, 'GET', '/api/system/job?page=1&size=200');
  for (const it of (body && (body.data.rows || body.data)) || []) {
    if (String(it.job_name || '').startsWith('导览')) {
      // eslint-disable-next-line no-await-in-loop
      await t.api(page, 'DELETE', `/api/system/job/${it.job_id}`);
    }
  }
}

test('09｜操作日志 · 登录日志 · 在线用户', async ({ page }) => {
  await t.open(page);
  await t.gotoMenu(page, '系统监控', '操作日志');

  await t.chapter(page, {
    n: 9,
    title: '审计日志 · 在线会话',
    subtitle: '写操作由中间件自动留痕，会话以服务端为准',
    points: [
      'operlog 中间件环绕路由，记录模块/参数/返回值/耗时',
      '登录日志区分成功与失败，可解锁被锁定的账号',
      '在线用户即 sys_online 会话表，强退后令牌立刻失效',
      '所有查询都走数据权限，非管理员只看得见自己的记录',
    ],
  });

  await t.say(page, '前面几段的新增、修改、删除，一行记录代码都没写，全部由中间件自动落库。');
  await t.step(page, '按系统模块搜出通知相关的操作。', async () => {
    await page.getByPlaceholder('请输入系统模块').fill('notice');
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 1000);
    await expect(rows(page, 'notice').first()).toBeVisible();
  });
  await t.say(page, '操作列里的“系统模块”存的是 HTTP 方法加接口路径，直接就能定位到后端路由。');
  await t.step(page, '打开一条日志的详情。', async () => {
    await rows(page, 'notice').first().getByText('详细').click();
    await expect(page.getByText('操作日志详情')).toBeVisible();
  });
  await t.step(page, '详情里请求参数与返回参数都完整留存。', async () => {
    const text = await modal(page).innerText();
    expect(text).toContain('请求URL');
    expect(text).toContain('请求参数');
    expect(text).toContain('消耗时间');
  });
  await t.say(page, '弹窗没有底部按钮，看完按 Esc 关闭。');
  await t.step(page, '关闭详情弹窗。', async () => {
    await page.keyboard.press('Escape');
    await t.wait(page, 700);
  });
  await t.step(page, '再按操作状态 = 成功筛一次。', async () => {
    await page.getByPlaceholder('请输入系统模块').fill('notice');
    await page.locator('.ant-select', { hasText: '操作状态' }).first().click();
    await t.pickOption(page, '成功');
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 1000);
    await expect(rows(page, '成功').first()).toBeVisible();
    await page.getByRole('button', { name: /重\s*置/ }).first().click();
  });
  await t.settle(page, 900);

  await t.gotoMenu(page, '系统监控', '登录日志');
  await t.say(page, '登录日志记录了每一次登录尝试：IP、地点、浏览器、系统与结果。');
  await t.step(page, '按登录状态筛出成功的会话。', async () => {
    await page.locator('.ant-select', { hasText: '登录状态' }).first().click();
    await t.pickOption(page, '成功');
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 1000);
    await expect(rows(page, '成功').first()).toBeVisible();
  });
  await t.say(page, '工具栏上的「解锁」配合后端登录限流：连续失败会被临时锁定，管理员可以手工解锁。');
  await t.step(page, '列出全部登录日志。', async () => {
    await page.getByRole('button', { name: /重\s*置/ }).first().click();
    await t.settle(page, 900);
  });

  await t.gotoMenu(page, '系统监控', '在线用户');
  await t.say(page, '在线用户不是从令牌里猜的，而是直接读服务端的会话表。');
  await t.step(page, '当前 admin 的会话在列，含登录时间与最后访问。', async () => {
    await expect(rows(page, 'admin').first()).toBeVisible();
  });
  await t.say(page, '每次请求都会刷新心跳；空闲超过 30 分钟由后台清理，强退则立刻删除会话行。');
  await t.step(page, '「强退」按钮会立即失效对应令牌——导览在这里不点自己的会话。', async () => {
    await expect(rows(page, 'admin').first().getByText('强退')).toBeVisible();
  });
});

test('10｜定时任务：Cron 调度与执行日志', async ({ page }) => {
  await t.open(page);
  await cleanLeftovers(page);
  await t.gotoMenu(page, '系统监控', '定时任务');

  await t.chapter(page, {
    n: 10,
    title: '定时任务 · Cron 调度',
    subtitle: '新增 → 立即执行 → 执行日志 → 暂停恢复',
    points: [
      '任务表 sys_job 存调用目标与 Cron 表达式',
      '内置两个示例任务，每 10 / 15 秒跑一次',
      '「执行一次」不等下个周期，便于验证',
      '执行日志独立成表，抽屉里按任务名查看',
    ],
  });

  await t.say(page, '两条内置任务正在按计划运行，调用目标是命名空间里的普通函数。');
  await t.step(page, '确认内置任务已存在。', () =>
    expect(rows(page, '系统默认（无参）').first()).toBeVisible()
  );

  await t.step(page, '新增一个演示任务：名称、分组、调用目标、Cron。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增任务' })).toBeVisible();
    await modalInput(page, 0).fill(TOUR_JOB);
    await modal(page).locator('.ant-select').first().click();
    await t.pickOption(page, '默认');
    await modalInput(page, 2).fill('com.ruoyi.task/ry-no-params');
    await modalInput(page, 3).fill('0 0 3 * * ?');
    await confirmDialog(page).click();
  });
  await t.settle(page, 1300);
  await t.step(page, '按名称搜出它：Cron 设成每天 3 点，避免录像期间自己触发。', async () => {
    await page.getByPlaceholder('请输入任务名称').fill(TOUR_JOB);
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 1000);
    const row = rows(page, TOUR_JOB).first();
    await expect(row).toBeVisible();
    await expect(row.getByText('0 0 3 * * ?')).toBeVisible();
    await expect(row.locator('.ant-tag', { hasText: '正常' })).toBeVisible();
  });

  await t.say(page, '新任务默认状态是「正常」，调度器已经把它挂上；「执行一次」可以立刻验证逻辑。');
  await t.step(page, '立即执行一次。', async () => {
    await rows(page, TOUR_JOB).first().getByRole('button', { name: '执行' }).click();
    await expect(page.getByText('确认立即执行一次该任务？')).toBeVisible();
    await confirmPop(page).click();
    await t.settle(page, 2200);
  });

  await t.step(page, '打开执行日志抽屉。', async () => {
    await rows(page, TOUR_JOB).first().getByRole('button', { name: '日志' }).click();
    await expect(page.getByText(`任务日志 - ${TOUR_JOB}`)).toBeVisible();
    await t.settle(page, 1200);
  });
  await t.say(page, '这次手动执行已经写进日志：状态成功，还带上了耗时。');
  await t.step(page, '抽屉里有一条成功记录。', async () => {
    const drawer = page.locator('.ant-drawer-content-wrapper:visible');
    await expect(drawer.locator('.ant-table-tbody tr.ant-table-row', { hasText: '成功' }).first())
      .toBeVisible();
  });
  await t.step(page, '关闭抽屉。', async () => {
    await page.locator('.ant-drawer-close').first().click();
    await t.wait(page, 800);
  });

  await t.step(page, '暂停任务，状态标签转红。', async () => {
    // 行内按钮与状态标签都叫「暂停」，点击用 role，断言用 tag
    await rows(page, TOUR_JOB).first().getByRole('button', { name: '暂停' }).click();
    await t.settle(page, 1200);
    await expect(rows(page, TOUR_JOB).first().locator('.ant-tag', { hasText: '暂停' })).toBeVisible();
  });
  await t.say(page, '暂停后调度器会把它摘掉，恢复则重新按 Cron 排期。');
  await t.step(page, '恢复任务。', async () => {
    await rows(page, TOUR_JOB).first().getByRole('button', { name: '恢复' }).click();
    await t.settle(page, 1200);
    await expect(rows(page, TOUR_JOB).first().locator('.ant-tag', { hasText: '正常' })).toBeVisible();
  });
  await t.step(page, '编辑：把备注补上，验证回显与更新。', async () => {
    await rows(page, TOUR_JOB).first().getByRole('button', { name: '编辑' }).click();
    await expect(page.getByRole('dialog', { name: '编辑任务' })).toBeVisible();
    await expect(modalInput(page, 0)).toHaveValue(TOUR_JOB);
    await modal(page).locator('textarea').fill('导览演示任务');
    await confirmDialog(page).click();
    await t.settle(page, 1200);
  });
  await t.step(page, '删除演示任务。', async () => {
    await rows(page, TOUR_JOB).first().getByRole('button', { name: '删除' }).click();
    await expect(page.getByText('确认删除该任务？')).toBeVisible();
    await confirmPop(page).click();
    await t.settle(page, 1200);
    await expect(rows(page, TOUR_JOB)).toHaveCount(0);
  });
});
