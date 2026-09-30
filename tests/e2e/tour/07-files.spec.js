// 分镜 13 文件管理与部署收尾：上传目录、下载令牌、错误边界、生产开关
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

const SUFFIX = String(Date.now()).slice(-6);
const FILE_NAME = `导览文件-${SUFFIX}.txt`;

const rows = (page, text) => page.locator('.ant-table-tbody tr.ant-table-row', { hasText: text });

/** 只读接口：拿当前会话的令牌直接打一次，用来展示后端的错误响应体。 */
async function rawRequest(page, path) {
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  const res = await page.request.fetch(path, { headers: { Authorization: `Bearer ${token}` } });
  return { status: res.status(), body: await res.text() };
}

test('13｜文件管理与部署收尾：上传目录、错误边界与生产开关', async ({ page }) => {
  await t.open(page);
  await t.gotoMenu(page, '系统管理', '文件管理');

  await t.chapter(page, {
    n: 13,
    title: '文件管理 · 部署与错误边界',
    subtitle: '上传目录 · 带令牌下载 · 5xx 收口 · 环境变量开关',
    points: [
      '上传目录 / 类型白名单 / 大小上限都来自 :upload-config',
      '路径一律经 resolve-in 校验，目录外的文件碰不到',
      '下载用带令牌的 <a download>，不是裸链接',
      'UPLOAD_DIR / DB_MAX_ACTIVE / SCHEDULER_ENABLED / MIGRATE_ON_INIT 全走环境变量',
      '5xx 只回通用文案，异常细节只在服务端日志里',
    ],
  });

  await t.say(page, '倒数第二段看文件管理：这一页的目录、类型白名单和大小上限，全部来自后端的一份配置。');
  await t.step(page, '上传一个文本文件。', async () => {
    await page.setInputFiles('input[type=file]', {
      name: FILE_NAME,
      mimeType: 'text/plain',
      buffer: Buffer.from('导览录像用的演示文件\n'),
    });
    await t.settle(page, 1300);
  });
  await t.step(page, '列表里的修改时间是后端统一编码的本地时间，不是毫秒数。', async () => {
    const row = rows(page, FILE_NAME).first();
    await expect(row).toBeVisible();
    await expect(row).toContainText(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/);
    await expect(row).toContainText(/B|KB|MB/);
  });
  await t.say(page, '下载按钮不能是裸链接——令牌在 header 里，transport 会把它挂在带令牌的下载请求上。');
  await t.step(page, '带令牌下载，浏览器直接落盘。', async () => {
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20000 }),
      rows(page, FILE_NAME).first().getByRole('button', { name: '下载' }).click(),
    ]);
    expect(download.suggestedFilename()).toContain(FILE_NAME);
  });
  await t.say(page, '删除同样要过路径校验：uploads 目录之外的文件，接口压根拼不出来。');
  await t.step(page, '删掉演示文件。', async () => {
    await rows(page, FILE_NAME).first().getByRole('button', { name: '删除' }).click();
    await expect(page.getByText('确认删除该文件？')).toBeVisible();
    await page.locator('.ant-popconfirm .ant-btn-primary, .ant-popover .ant-btn-primary').first().click();
    await t.settle(page, 1100);
    await expect(rows(page, FILE_NAME)).toHaveCount(0);
  });

  await t.say(page, '错误响应也做了收口：试着越界读上传目录之外的文件。');
  await t.step(page, '直接请求一个目录外的路径。', async () => {
    const { status, body } = await rawRequest(page, '/api/system/file/..%2F..%2Fetc%2Fpasswd');
    // eslint-disable-next-line no-console
    console.log('非法路径下载 →', status, body.slice(0, 160));
    expect(body).toContain('文件不存在');
    expect(body).not.toMatch(/java\.|clojure|\.clj|passwd/);
  });
  await t.say(page, '路径解析不出文件就什么都读不到；异常类名、URI、堆栈这些细节只在服务端日志里，5xx 响应体只有一条通用文案。');

  await t.gotoMenu(page, '系统管理', '用户管理');
  await t.say(page, '部署侧只有四个开关要记：UPLOAD_DIR 决定上传根目录，DB_MAX_ACTIVE 调连接池，非主实例把 SCHEDULER_ENABLED 设成 false 避免任务重复跑，MIGRATE_ON_INIT 控制启动是否迁移。');
  await t.say(page, '生产 profile 启动前会体检配置：JWT_SECRET 与 COOKIE_SECRET 缺失或还是内置默认值就直接拒绝启动，用 SQLite 或没设时区会打告警。');
  await t.say(page, '同一套代码可跑 SQLite 与 MySQL，两套迁移目录分开，SQL 里的时间一律由应用生成。');

  await t.step(page, '回到仪表盘，最后一段专门讲工程链。', async () => {
    await t.open(page);
    await expect(page.getByText('在线用户').first()).toBeVisible();
  });
});
