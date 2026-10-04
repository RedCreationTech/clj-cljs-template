const fs = require('fs');
const { test, expect } = require('playwright/test');
const { enableSemantics, signIn, content } = require('./mobile-semantics');
const { installFonts } = require('./mobile-fonts');

const built = fs.existsSync('resources/public/mobile/index.html');
if (!built && process.env.MOBILE_UI_REQUIRED === '1') throw new Error('请先运行 bb mobile:web');
test.skip(!built, 'Flutter Web 尚未构建；独立mobile CI job必须构建并运行本文件');
test.use({ viewport: { width: 390, height: 844 } });

test.describe('移动模板真实Flutter界面', () => {
  test.setTimeout(120000);
  let headers;
  const prefix = `MOBILE_${Date.now()}`;
  test.beforeEach(async ({ page }) => { await installFonts(page); });
  test.beforeAll(async ({ request }) => {
    const auth = await (await request.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' },
    })).json();
    expect(auth.code).toBe(200);
    headers = { authorization: `Bearer ${auth.data.token}` };
    for (let i = 1; i <= 21; i += 1) {
      const result = await (await request.post('/api/system/post', {
        headers, data: { post_code: `${prefix}_${i}`, post_name: `${prefix}岗位${i}`, post_sort: i, status: '0' },
      })).json();
      expect(result.code).toBe(200);
    }
  });
  test.afterAll(async ({ request }) => {
    if (!headers) return;
    const list = await (await request.get('/api/system/post', {
      headers, params: { page: 1, size: 100, post_code: prefix },
    })).json();
    for (const row of list.data?.rows || []) {
      if (row.post_code.startsWith(prefix)) {
        expect((await (await request.delete(`/api/system/post/${row.post_id}`, { headers })).json()).code).toBe(200);
      }
    }
    await request.post('/api/auth/logout', { headers });
  });

  test('登录、分页、刷新、明暗主题、账号和退出完整可用', async ({ page }, info) => {
    const failures = [];
    page.on('pageerror', e => failures.push(e.message));
    await page.goto('/mobile/');
    await enableSemantics(page);
    await expect(page.getByRole('textbox', { name: '用户名', exact: true })).toBeVisible();
    await page.screenshot({ path: info.outputPath('login-light.png') });
    await page.getByRole('button', { name: '切换为深色主题' }).click();
    await expect(page.getByRole('button', { name: '切换为浅色主题' })).toBeVisible();
    await page.screenshot({ path: info.outputPath('login-dark.png') });
    await signIn(page);
    await expect(content(page, '岗位信息')).toBeVisible();
    await page.screenshot({ path: info.outputPath('posts-dark.png') });
    const next = page.waitForResponse(r => r.url().includes('/api/system/post') && new URL(r.url()).searchParams.get('page') === '2');
    await page.getByRole('button', { name: '下一页' }).click();
    expect((await (await next).json()).data.rows.length).toBeGreaterThan(0);
    const refresh = page.waitForResponse(r => r.url().includes('/api/system/post') && new URL(r.url()).searchParams.get('page') === '2');
    await page.getByRole('button', { name: '刷新列表' }).click();
    expect((await (await refresh).json()).code).toBe(200);
    await page.getByRole('button', { name: '切换为浅色主题' }).click();
    await page.screenshot({ path: info.outputPath('posts-light.png') });
    await page.getByRole('button', { name: '我的账号' }).click();
    await expect(content(page, '管理员全部权限')).toBeVisible();
    await page.screenshot({ path: info.outputPath('account.png') });
    await page.getByRole('button', { name: '退出登录', exact: true }).last().click();
    await expect(page.getByRole('textbox', { name: '用户名', exact: true })).toBeVisible();
    await expect(content(page, '管理员全部权限')).toHaveCount(0);
    expect(failures).toEqual([]);
  });

  test('窄屏和宽屏布局以及空态、失败与重试', async ({ page }, info) => {
    await page.setViewportSize({ width: 320, height: 740 });
    await page.goto('/mobile/');
    await enableSemantics(page);
    await expect(page.getByRole('textbox', { name: '密码', exact: true })).toBeVisible();
    await page.screenshot({ path: info.outputPath('login-narrow.png') });
    await page.setViewportSize({ width: 1024, height: 900 });
    await page.screenshot({ path: info.outputPath('login-wide.png') });
    await page.route('**/api/system/post*', route => route.fulfill({
      json: { code: 200, msg: '操作成功', data: { rows: [], total: 0 } },
    }));
    await signIn(page);
    await expect(content(page, '暂无岗位')).toBeVisible();
    await page.screenshot({ path: info.outputPath('posts-empty.png') });
    await page.unroute('**/api/system/post*');
    await page.route('**/api/system/post*', route => route.fulfill({ status: 503, body: 'gateway error' }));
    await page.getByRole('button', { name: '刷新列表' }).first().click();
    await expect(content(page, '岗位加载失败')).toBeVisible();
    await page.screenshot({ path: info.outputPath('posts-error.png') });
    await page.unroute('**/api/system/post*');
    const recovered = page.waitForResponse(r => r.url().includes('/api/system/post'));
    await page.getByRole('button', { name: '重新加载', exact: true }).click();
    expect((await (await recovered).json()).code).toBe(200);
    await expect(content(page, '岗位加载失败')).toHaveCount(0);
    await page.getByRole('button', { name: '退出登录', exact: true }).click();
    await expect(page.getByRole('textbox', { name: '用户名', exact: true })).toBeVisible();
  });
});
