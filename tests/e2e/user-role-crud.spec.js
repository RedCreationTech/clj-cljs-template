const { expect } = require('playwright/test');
const { randomUUID } = require('node:crypto');
const { login } = require('./auth-helper');
const { test, adminHeaders } = require('./user-role-helper');

const confirm = /^(确\s*定|OK)$/;
const rows = page => page.locator('table tbody tr.ant-table-row');
const userForm = (page, name) => page.getByRole('heading', { name, exact: true }).locator('../..');

async function search(page, placeholder, value, count = 1) {
  await page.getByPlaceholder(placeholder, { exact: true }).fill(value);
  const resource = placeholder === '请输入权限字符' ? 'role' : 'user';
  const [response] = await Promise.all([
    page.waitForResponse(r => new URL(r.url()).pathname === `/api/system/${resource}`
      && r.request().method() === 'GET'
      && new URL(r.url()).searchParams.get(resource === 'role' ? 'role_key' : 'user_name') === value,
    { timeout: 15000 }),
    page.getByRole('button', { name: /搜\s*索/ }).first().click(),
  ]);
  expect((await response.json()).code).toBe(200);
  await expect(rows(page).filter({ hasText: value })).toHaveCount(count);
  return rows(page).filter({ hasText: value });
}

async function openRoles(page, row, username) {
  await row.getByRole('button', { name: '更多' }).hover();
  const [readRoles] = await Promise.all([
    page.waitForResponse(r => /\/api\/system\/user\/\d+\/authRole$/.test(new URL(r.url()).pathname)
      && r.request().method() === 'GET', { timeout: 15000 }),
    page.getByRole('menuitem', { name: '分配角色', exact: true }).click(),
  ]);
  const modal = userForm(page, `分配角色 - ${username}`);
  await expect(modal).toBeVisible();
  expect((await readRoles.json()).code).toBe(200);
  return modal;
}

test('用户和角色浏览器 CRUD、双向关联与持久化读回', async ({ page, request, userRoleCleanup }) => {
  test.setTimeout(120000);
  page.setDefaultTimeout(15000);
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
  const username = `e2eu${suffix}`;
  const roleKey = `e2er${suffix}`;
  const roleName = `测试角色${suffix}`;
  const updatedRole = `${roleName}修改`;
  const nickname = `测试用户${suffix}`;
  const headers = await adminHeaders(request);
  userRoleCleanup(headers, username, roleKey);
  await login(page);
  await page.goto('/system/role');
  await page.getByRole('button', { name: /新\s*增$/ }).click();
  const roleModal = page.getByRole('dialog', { name: '新增角色', exact: true });
  await roleModal.getByPlaceholder('请输入角色名称').fill(roleName);
  await roleModal.getByPlaceholder('请输入权限字符').fill(roleKey);
  await roleModal.getByPlaceholder('请输入角色顺序').fill('7');
  await roleModal.getByRole('button', { name: confirm }).click();
  await expect(roleModal).toBeHidden();
  let roleRow = await search(page, '请输入权限字符', roleKey);
  await expect(roleRow).toContainText(roleName);
  await roleRow.getByRole('button', { name: /修\s*改$/ }).click();
  const editRole = page.getByRole('dialog', { name: '修改角色', exact: true });
  await expect(editRole.getByPlaceholder('请输入权限字符')).toHaveValue(roleKey);
  await editRole.getByPlaceholder('请输入角色名称').fill(updatedRole);
  await editRole.getByPlaceholder('请输入备注').fill('浏览器角色更新');
  await editRole.getByRole('button', { name: confirm }).click();
  await expect(editRole).toBeHidden();
  await page.reload();
  roleRow = await search(page, '请输入权限字符', roleKey);
  await expect(roleRow).toContainText(updatedRole);

  await page.goto('/system/user');
  await page.getByRole('button', { name: /新\s*增$/ }).click();
  const addUser = userForm(page, '添加用户');
  await addUser.getByPlaceholder('请输入用户昵称').fill(nickname);
  await addUser.getByPlaceholder('请输入用户名称').fill(username);
  await addUser.getByPlaceholder('请输入用户密码').fill('UserE2e123!');
  await addUser.getByPlaceholder('请输入邮箱').fill(`${username}@example.test`);
  await addUser.getByRole('button', { name: confirm }).click();
  await expect(addUser).toBeHidden();
  let userRow = await search(page, '请输入用户名称', username);
  await expect(userRow).toContainText(nickname);

  let assign = await openRoles(page, userRow, username);
  await assign.getByRole('combobox').fill(updatedRole);
  await page.locator('.ant-select-dropdown:visible').getByText(updatedRole, { exact: true }).click();
  await expect(assign.locator('.ant-select-selection-item')).toHaveText(updatedRole);
  await assign.getByRole('combobox').press('Escape');
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
  await assign.getByRole('button', { name: confirm }).click();
  await expect(assign).toBeHidden();
  await page.reload();
  userRow = await search(page, '请输入用户名称', username);
  assign = await openRoles(page, userRow, username);
  await expect(assign.locator('.ant-select-selection-item')).toHaveText(updatedRole);
  await assign.getByRole('button', { name: /取\s*消/ }).click();

  await userRow.getByRole('button', { name: /修\s*改$/ }).click();
  const editUser = userForm(page, '修改用户');
  await expect(editUser.getByPlaceholder('请输入邮箱')).toHaveValue(`${username}@example.test`);
  await expect(editUser.locator('.ant-select-selection-item').filter({ hasText: updatedRole })).toHaveCount(1);
  await editUser.getByPlaceholder('请输入用户昵称').fill(`${nickname}修改`);
  await editUser.getByPlaceholder('请输入内容').fill('浏览器用户更新');
  await editUser.getByRole('button', { name: confirm }).click();
  await expect(editUser).toBeHidden();
  await page.reload();
  userRow = await search(page, '请输入用户名称', username);
  await expect(userRow).toContainText(`${nickname}修改`);
  await userRow.getByText(username, { exact: true }).click();
  const detail = page.getByRole('dialog', { name: '用户详情' });
  await expect(detail.getByText(updatedRole, { exact: true })).toBeVisible();
  await expect(detail.getByText('浏览器用户更新', { exact: true })).toBeVisible();
  await detail.getByRole('button', { name: /^(关闭|Close)$/ }).click();

  await page.goto('/system/role');
  roleRow = await search(page, '请输入权限字符', roleKey);
  await roleRow.getByRole('button', { name: '更多' }).hover();
  await page.getByRole('menuitem', { name: '分配用户', exact: true }).click();
  const allocated = page.getByRole('dialog', { name: `分配用户 - ${updatedRole}`, exact: true });
  await allocated.getByPlaceholder('用户名称', { exact: true }).fill(username);
  await allocated.getByRole('button', { name: /搜\s*索/ }).click();
  const assignedRow = allocated.locator('tr.ant-table-row').filter({ hasText: username });
  await expect(assignedRow).toContainText(`${nickname}修改`);
  await assignedRow.getByRole('button', { name: '取消授权', exact: true }).click();
  await expect(assignedRow).toHaveCount(0);
  await allocated.getByRole('button', { name: /^(关闭|Close)$/ }).click();

  await page.goto('/system/user');
  userRow = await search(page, '请输入用户名称', username);
  assign = await openRoles(page, userRow, username);
  await expect(assign.locator('.ant-select-selection-item')).toHaveCount(0);
  await assign.getByRole('button', { name: /取\s*消/ }).click();
  await userRow.getByRole('button', { name: /删\s*除$/ }).click();
  await expect(userRow).toHaveCount(0);
  await page.reload();
  await search(page, '请输入用户名称', username, 0);

  await page.goto('/system/role');
  roleRow = await search(page, '请输入权限字符', roleKey);
  await roleRow.getByRole('button', { name: /删\s*除$/ }).click();
  await page.getByRole('tooltip').getByRole('button', { name: confirm }).click();
  await expect(roleRow).toHaveCount(0);
  await page.reload();
  await search(page, '请输入权限字符', roleKey, 0);
});
