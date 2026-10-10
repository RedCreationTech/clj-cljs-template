const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');
const { unique, rowFor, readRows, readRecord, save, search, remove, cleanup } = require('./builtin-crud-helper');

test.setTimeout(90000);

test.beforeEach(async ({ page }) => { await login(page); });

test('部门：UI 新增、回显、修改、删除及持久化读回', async ({ page }, testInfo) => {
  const name = unique('dept');
  const updated = `${name}_updated`;
  const api = '/api/system/dept';
  try {
    await page.goto('/system/dept');
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    let modal = page.getByRole('dialog', { name: '新增部门' });
    await modal.getByPlaceholder('请输入部门名称').fill(name);
    await modal.getByPlaceholder('请输入显示排序').fill('7');
    await modal.getByPlaceholder('请输入负责人').fill('E2E负责人');
    await modal.getByPlaceholder('请输入联系电话').fill('13800000001');
    await modal.getByPlaceholder('请输入邮箱').fill('e2e@example.invalid');
    await save(page, testInfo, modal, api, 'POST');
    const created = (await readRows(page, api, { dept_name: name })).find((r) => r.dept_name === name);
    expect(created).toBeTruthy();
    await readRecord(page, api, created.dept_id, { dept_name: name, parent_id: 0, order_num: 7,
      leader: 'E2E负责人', phone: '13800000001', email: 'e2e@example.invalid', status: '0' });
    await page.reload();
    await rowFor(page, name).getByRole('button', { name: '修改' }).click();
    modal = page.getByRole('dialog', { name: '修改部门' });
    await expect(modal.getByPlaceholder('请输入负责人')).toHaveValue('E2E负责人');
    await expect(modal.getByPlaceholder('请输入邮箱')).toHaveValue('e2e@example.invalid');
    await modal.getByPlaceholder('请输入部门名称').fill(updated);
    await modal.getByPlaceholder('请输入负责人').fill('E2E修改负责人');
    await modal.getByRole('radio', { name: '停用' }).check();
    await save(page, testInfo, modal, `${api}/${created.dept_id}`, 'PUT');
    await readRecord(page, api, created.dept_id, { dept_name: updated, leader: 'E2E修改负责人', status: '1', order_num: 7 });
    await expect(rowFor(page, updated)).toContainText('停用');
    await remove(page, testInfo, rowFor(page, updated), api, created.dept_id);
  } finally { await cleanup(page, api, 'dept_id', 'dept_name', [name, updated], { dept_name: name }); }
});

test('菜单：UI 新增目录、回显、修改、删除及路由字段读回', async ({ page }, testInfo) => {
  const name = unique('menu');
  const updated = `${name}_updated`;
  const api = '/api/system/menu';
  try {
    await page.goto('/system/menu');
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    let modal = page.getByRole('dialog', { name: '新增菜单' });
    await modal.getByPlaceholder('请输入菜单名称').fill(name);
    await modal.getByPlaceholder('请输入显示排序').fill('8');
    await modal.getByPlaceholder('请输入路由地址').fill(name);
    await modal.getByPlaceholder('请输入路由名称').fill(name);
    await modal.getByRole('radio', { name: '隐藏', exact: true }).check();
    await save(page, testInfo, modal, api, 'POST');
    const created = (await readRows(page, api, { menu_name: name })).find((r) => r.menu_name === name);
    expect(created).toBeTruthy();
    await readRecord(page, api, created.menu_id, { menu_name: name, menu_type: 'M', parent_id: 0,
      path: name, route_name: name, order_num: 8, visible: '1', status: '0' });
    await page.reload();
    await rowFor(page, name).getByRole('button', { name: '修改' }).click();
    modal = page.getByRole('dialog', { name: '修改菜单' });
    await expect(modal.getByPlaceholder('请输入路由地址')).toHaveValue(name);
    await expect(modal.getByRole('radio', { name: '隐藏', exact: true })).toBeChecked();
    await modal.getByPlaceholder('请输入菜单名称').fill(updated);
    await modal.getByPlaceholder('请输入路由地址').fill(updated);
    await modal.getByRole('radio', { name: '停用', exact: true }).check();
    await save(page, testInfo, modal, `${api}/${created.menu_id}`, 'PUT');
    await readRecord(page, api, created.menu_id, { menu_name: updated, path: updated, status: '1', visible: '1' });
    await expect(rowFor(page, updated)).toContainText('停用');
    await remove(page, testInfo, rowFor(page, updated), api, created.menu_id);
  } finally { await cleanup(page, api, 'menu_id', 'menu_name', [name, updated], { menu_name: name }); }
});

test('参数：UI 新增非内置参数、修改、删除与全部字段读回', async ({ page }, testInfo) => {
  const name = unique('config');
  const updated = `${name}_updated`;
  const api = '/api/system/config';
  try {
    await page.goto('/system/config');
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    let modal = page.getByRole('dialog', { name: '新增参数' });
    let inputs = modal.locator('input.ant-input');
    await inputs.nth(0).fill(name);
    await inputs.nth(1).fill(name);
    await inputs.nth(2).fill('初始值');
    await modal.getByRole('radio', { name: '否', exact: true }).check();
    await modal.locator('textarea').fill('E2E备注');
    await save(page, testInfo, modal, api, 'POST');
    const created = (await readRows(page, api, { config_name: name })).find((r) => r.config_name === name);
    expect(created).toBeTruthy();
    await readRecord(page, api, created.config_id, { config_name: name, config_key: name,
      config_value: '初始值', config_type: 'N', remark: 'E2E备注' });
    await page.reload();
    await search(page, '请输入参数名称', name);
    await rowFor(page, name).getByRole('button', { name: '编辑' }).click();
    modal = page.getByRole('dialog', { name: '编辑参数' });
    inputs = modal.locator('input.ant-input');
    await expect(inputs.nth(2)).toHaveValue('初始值');
    await expect(modal.getByRole('radio', { name: '否', exact: true })).toBeChecked();
    await inputs.nth(0).fill(updated);
    await inputs.nth(2).fill('修改值');
    await modal.locator('textarea').fill('修改备注');
    await save(page, testInfo, modal, `${api}/${created.config_id}`, 'PUT');
    await readRecord(page, api, created.config_id, { config_name: updated, config_key: name,
      config_value: '修改值', config_type: 'N', remark: '修改备注' });
    await expect(rowFor(page, updated)).toContainText('修改值');
    await remove(page, testInfo, rowFor(page, updated), api, created.config_id);
  } finally { await cleanup(page, api, 'config_id', 'config_name', [name, updated], { config_name: name }); }
});

test('公告：UI 富文本新增、回显、修改、删除与内容持久化', async ({ page }, testInfo) => {
  const name = unique('notice');
  const updated = `${name}_updated`;
  const api = '/api/system/notice';
  try {
    await page.goto('/system/notice');
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    let modal = page.getByRole('dialog', { name: '新增通知公告' });
    await modal.getByPlaceholder('请输入公告标题').fill(name);
    await modal.locator('.ql-editor').fill('E2E富文本正文');
    await modal.getByPlaceholder('请输入备注').fill('E2E公告备注');
    await save(page, testInfo, modal, api, 'POST');
    const created = (await readRows(page, api, { notice_name: name })).find((r) => r.notice_name === name);
    expect(created).toBeTruthy();
    await readRecord(page, api, created.notice_id, { notice_name: name, notice_type: '1', status: '0',
      notice_content: '<p>E2E富文本正文</p>', remark: 'E2E公告备注' });
    await page.reload();
    await search(page, '请输入公告标题', name);
    await rowFor(page, name).getByRole('button', { name: '编辑' }).click();
    modal = page.getByRole('dialog', { name: '编辑通知公告' });
    await expect(modal.locator('.ql-editor')).toHaveText('E2E富文本正文');
    await modal.getByPlaceholder('请输入公告标题').fill(updated);
    await modal.locator('.ql-editor').fill('E2E修改后的正文');
    await modal.getByRole('combobox').click();
    await page.locator('.ant-select-dropdown:visible').getByText('公告', { exact: true }).click();
    await modal.getByRole('radio', { name: '关闭', exact: true }).check();
    await save(page, testInfo, modal, `${api}/${created.notice_id}`, 'PUT');
    await readRecord(page, api, created.notice_id, { notice_name: updated, notice_type: '2', status: '1',
      notice_content: '<p>E2E修改后的正文</p>', remark: 'E2E公告备注' });
    await expect(rowFor(page, updated)).toContainText('关闭');
    await remove(page, testInfo, rowFor(page, updated), api, created.notice_id, false);
  } finally { await cleanup(page, api, 'notice_id', 'notice_name', [name, updated], { notice_name: name }); }
});
