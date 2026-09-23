const { test, expect } = require('playwright/test');
const { login, logout } = require('./auth-helper');

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
});
