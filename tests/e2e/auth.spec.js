const { test, expect } = require('playwright/test');
const { login, logout } = require('./auth-helper');

const token = (page) => page.evaluate(() => localStorage.getItem('ruoyi_token'));

test.describe('认证流程', () => {
  test('使用默认管理员账号登录并登出', async ({ page }) => {
    await login(page);

    // 验证进入首页(不依赖侧边栏品牌文案,改名后仍可判定已进入管理端布局)
    await expect(page).toHaveURL('/');
    await expect(page.locator('.ant-layout-sider').first()).toBeVisible();

    // 左侧菜单应包含系统管理
    await expect(page.getByText('系统管理').first()).toBeVisible();

    await logout(page);

    // 验证回到登录页
    await expect(page.getByPlaceholder('用户名')).toBeVisible();
    await expect(page.getByRole('button', { name: /登\s*录/ })).toBeVisible();
  });

  test('密码错误:提示统一文案,按钮恢复可用;开发环境不显示验证码', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('wrong-password');
    await expect(page.getByPlaceholder('验证码')).toHaveCount(0);
    await page.getByRole('button', { name: /登\s*录/ }).click();
    await expect(page.getByText(/用户名或密码错误/)).toBeVisible();
    await expect(page.getByRole('button', { name: /登\s*录/ })).toBeEnabled();
  });

  test('登出后旧令牌立即失效', async ({ page }) => {
    await login(page);
    const t = await token(page);
    expect(t).toBeTruthy();
    const before = await page.request.get('/api/auth/getInfo', { headers: { Authorization: `Bearer ${t}` } });
    expect(before.status()).toBe(200);
    await logout(page);
    const after = await page.request.get('/api/auth/getInfo', { headers: { Authorization: `Bearer ${t}` } });
    expect(after.status()).toBe(401);
  });

  test('会话在服务端失效后,下一次请求自动回到登录页', async ({ page }) => {
    await login(page);
    const t = await token(page);
    // 模拟被强退 / 空闲超时:服务端删除会话
    await page.request.post('/api/auth/logout', { headers: { Authorization: `Bearer ${t}` } });
    await page.getByText('系统管理').first().click();
    await page.getByText('用户管理').first().click();
    await expect(page.getByPlaceholder('用户名')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/登录状态已过期/)).toBeVisible();
  });
});
