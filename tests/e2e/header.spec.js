const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

test.describe('顶部工具', () => {
  test('菜单搜索:输入菜单名回车后跳转', async ({ page }) => {
    await login(page);
    await page.locator('.ant-layout-header').getByRole('button', { name: 'search' }).click();
    const search = page.getByRole('combobox');
    await search.fill('岗位');
    await search.press('Enter');
    await expect(page).toHaveURL(/\/system\/post$/);
    await expect(page.getByText('岗位名称').first()).toBeVisible();
  });

  test('通知铃铛:新发布的通知显示未读数,打开后清零', async ({ page }) => {
    await login(page);
    const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
    const title = `E2E 通知 ${Date.now()}`;
    await page.request.post('/api/system/notice', {
      headers: { Authorization: `Bearer ${token}` },
      data: { notice_name: title, notice_type: '1', status: '0', notice_content: 'hello' },
    });
    await page.reload();
    const bell = page.locator('.ant-layout-header .ant-badge');
    await expect(bell.locator('.ant-badge-count')).toBeVisible({ timeout: 10000 });
    await bell.getByRole('button').click();
    await expect(page.getByText(title)).toBeVisible();
    await expect(bell.locator('.ant-badge-count')).toHaveCount(0);
  });
});
