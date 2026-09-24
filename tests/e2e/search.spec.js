const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

/** 在当前页的搜索表单里填一个条件并查询,返回表格数据行。 */
async function searchBy(page, placeholder, value) {
  await page.getByPlaceholder(placeholder).fill(value);
  await page.getByRole('button', { name: /搜\s*索/ }).first().click();
  return page.locator('table tbody tr.ant-table-row');
}

test.describe('列表搜索条件生效', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('角色:按权限字符', async ({ page }) => {
    await page.goto('/system/role');
    const rows = await searchBy(page, '请输入权限字符', 'admin');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('超级管理员');
  });

  test('岗位:按岗位编码', async ({ page }) => {
    await page.goto('/system/post');
    const rows = await searchBy(page, '请输入岗位编码', 'ceo');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('董事长');
  });

  test('参数:按参数名称', async ({ page }) => {
    await page.goto('/system/config');
    const rows = await searchBy(page, '请输入参数名称', '初始密码');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('sys.user.initPassword');
  });
});
