const { expect } = require('playwright/test');
const { login } = require('./auth-helper');
const { randomUUID } = require('node:crypto');
const { test, checked, adminHeaders } = require('./user-role-helper');

/** 用 admin 建一个只读角色(系统管理 / 用户管理 + 用户查询 / 角色管理)和一个用户。 */
async function createViewer(request, headers, username) {
  const data = await checked(await request.post('/api/system/role', {
    headers,
    data: { role_name: `只读${username}`, role_key: username, role_sort: 9, status: '0', 'menu-ids': [1, 3, 100, 4] },
  }));
  const roleId = Number(data.match(/\d+/)[0]);
  await checked(await request.post('/api/system/user', {
    headers,
    data: { user_name: username, nick_name: '只读用户', password: 'viewer123', roles: [roleId] },
  }));
}

test.describe('按钮级权限', () => {
  test('只读用户:列表可见,增删改按钮隐藏;越权接口统一提示', async ({ page, request, userRoleCleanup }) => {
    const username = `viewer${randomUUID().replaceAll('-', '').slice(0, 12)}`;
    const headers = await adminHeaders(request);
    page.setDefaultTimeout(15000);
    userRoleCleanup(headers, username, username);
    {
      await createViewer(request, headers, username);
      await login(page, username, 'viewer123');
      await expect(page.locator('.ant-layout-header').getByText('只读用户')).toBeVisible();

      // 侧边栏只有被授权的菜单
      await expect(page.getByText('用户管理').first()).toBeVisible();
      await expect(page.getByText('系统监控')).toHaveCount(0);

      // 用户页:能看到数据,看不到新增/导入/导出与行内修改/删除
      await page.goto('/system/user');
      await expect(page.locator('table tbody tr.ant-table-row', { hasText: 'admin' }).first()).toBeVisible({ timeout: 10000 });
      for (const name of [/新增/, /导入/, /导出/]) {
        await expect(page.getByRole('button', { name })).toHaveCount(0);
      }
      const adminRow = page.locator('table tbody tr.ant-table-row', { hasText: 'admin' }).first();
      await expect(adminRow.getByRole('button', { name: /修改|删除|更多/ })).toHaveCount(0);
      await expect(adminRow.getByRole('switch')).toBeDisabled();

      // 角色页:有列表权限,没有新增/导出
      await page.goto('/system/role');
      await expect(page.locator('table tbody tr.ant-table-row', { hasText: '超级管理员' })).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole('button', { name: /新增|导出/ })).toHaveCount(0);
      await expect(page.locator('table tbody tr.ant-table-row').first().getByRole('button')).toHaveCount(0);

      // 未授权的页面:接口 403,统一提示
      await page.goto('/system/post');
      await expect(page.getByText('没有操作权限')).toBeVisible({ timeout: 10000 });
    }
  });

  test('管理员看得到全部按钮', async ({ page }) => {
    await login(page);
    await page.goto('/system/user');
    for (const name of [/新增/, /导入/, /导出/]) {
      await expect(page.getByRole('button', { name })).toBeVisible();
    }
    const adminRow = page.locator('table tbody tr.ant-table-row', { hasText: 'admin' }).first();
    await expect(adminRow.getByRole('button', { name: /修改/ })).toBeVisible();
  });

  test('角色数据权限:自定义部门可以勾选并保存', async ({ page, request, userRoleCleanup }) => {
    await login(page);
    // 用新建的角色,保证可重复运行
    const headers = await adminHeaders(request);
    const key = `scope${randomUUID().replaceAll('-', '').slice(0, 12)}`;
    page.setDefaultTimeout(15000);
    userRoleCleanup(headers, null, key);
    {
      await checked(await request.post('/api/system/role', {
        headers,
        data: { role_name: `范围${key}`, role_key: key, role_sort: 99, status: '0', 'menu-ids': [] },
      }));
      await page.goto('/system/role');
      await page.getByPlaceholder('请输入权限字符').fill(key);
      await page.getByRole('button', { name: /搜\s*索/ }).first().click();
      const row = page.locator('table tbody tr.ant-table-row', { hasText: key });
      await row.getByRole('button', { name: /更多/ }).click();
      await page.getByRole('menuitem', { name: '数据权限' }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByText('自定义数据').click();
      await dialog.locator('.ant-tree-treenode', { hasText: '财务部门' }).locator('.ant-tree-checkbox').click();
      await dialog.getByRole('button', { name: /^(确 ?定|OK)$/ }).click();
      await expect(page.getByText('数据权限设置成功')).toBeVisible();

      // 重新打开:仍是自定义数据,财务部门已勾选
      await row.getByRole('button', { name: /更多/ }).click();
      await page.getByRole('menuitem', { name: '数据权限' }).click();
      await expect(dialog.getByRole('radio', { name: '自定义数据' })).toBeChecked();
      await expect(dialog.locator('.ant-tree-treenode-checkbox-checked', { hasText: '财务部门' })).toBeVisible();
      await dialog.getByRole('button', { name: /^(取 ?消|Cancel)$/ }).click();
    }
  });
});
