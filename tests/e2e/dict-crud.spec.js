const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');
const { unique, rowFor, readRows, readRecord, save, search, remove, cleanup } = require('./builtin-crud-helper');

test.setTimeout(90000);

test('字典：类型和数据均经 UI 新增、回显、修改、删除', async ({ page }, testInfo) => {
  await login(page);
  const name = unique('dict');
  const updated = `${name}_updated`;
  const label = `${name}_label`;
  const updatedLabel = `${label}_updated`;
  const typeApi = '/api/system/dict/type';
  const dataApi = '/api/system/dict/data';
  try {
    await page.goto('/system/dict');
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    let modal = page.getByRole('dialog', { name: '新增字典类型' });
    await modal.locator('input.ant-input').nth(0).fill(name);
    await modal.locator('input.ant-input').nth(1).fill(name);
    await expect(modal.getByRole('radio', { name: '正常', exact: true })).toBeChecked();
    await modal.locator('textarea').fill('类型备注');
    await save(page, testInfo, modal, typeApi, 'POST');
    const type = (await readRows(page, typeApi, { dict_type: name })).find((r) => r.dict_type === name);
    expect(type).toBeTruthy();
    await readRecord(page, typeApi, type.dict_id, { dict_name: name, dict_type: name, status: '0', remark: '类型备注' });
    await page.reload();
    await search(page, '请输入字典名称', name);
    await rowFor(page, name).getByRole('button', { name: '编辑', exact: true }).click();
    modal = page.getByRole('dialog', { name: '编辑字典类型' });
    await expect(modal.locator('input.ant-input').nth(1)).toHaveValue(name);
    await expect(modal.locator('textarea')).toHaveValue('类型备注');
    await modal.locator('input.ant-input').nth(0).fill(updated);
    await modal.locator('textarea').fill('类型修改备注');
    await save(page, testInfo, modal, `${typeApi}/${type.dict_id}`, 'PUT');
    await readRecord(page, typeApi, type.dict_id, { dict_name: updated, dict_type: name, status: '0', remark: '类型修改备注' });
    // Type save refreshes all types; apply the UI filter again before accessing the row.
    await search(page, '请输入字典名称', name);
    await rowFor(page, updated).getByRole('button', { name: '字典数据', exact: true }).click();
    await expect(page.getByRole('heading', { name: `字典数据 — ${updated} (${name})` })).toBeVisible();
    await page.getByRole('button', { name: /新\s*增/ }).last().click();
    modal = page.getByRole('dialog', { name: '新增字典数据' });
    await modal.locator('input.ant-input').nth(0).fill(label);
    await modal.locator('input.ant-input').nth(1).fill('value_1');
    await modal.locator('input.ant-input').nth(2).fill('3');
    await expect(modal.getByRole('radio', { name: '正常', exact: true })).toBeChecked();
    await modal.locator('textarea').fill('数据备注');
    await save(page, testInfo, modal, dataApi, 'POST');
    const data = (await readRows(page, dataApi, { dict_type: name })).find((r) => r.dict_label === label);
    expect(data).toBeTruthy();
    await readRecord(page, dataApi, data.dict_code, { dict_label: label, dict_value: 'value_1',
      dict_type: name, dict_sort: 3, status: '0', remark: '数据备注' });
    await page.reload();
    await search(page, '请输入字典名称', name);
    await rowFor(page, updated).getByRole('button', { name: '字典数据', exact: true }).click();
    await rowFor(page, label).getByRole('button', { name: '编辑', exact: true }).click();
    modal = page.getByRole('dialog', { name: '编辑字典数据' });
    await expect(modal.locator('input.ant-input').nth(1)).toHaveValue('value_1');
    await expect(modal.locator('input.ant-input').nth(2)).toHaveValue('3');
    await modal.locator('input.ant-input').nth(0).fill(updatedLabel);
    await modal.locator('input.ant-input').nth(1).fill('value_2');
    await modal.locator('input.ant-input').nth(2).fill('9');
    await modal.getByRole('radio', { name: '停用', exact: true }).check();
    await modal.locator('textarea').fill('数据修改备注');
    await save(page, testInfo, modal, `${dataApi}/${data.dict_code}`, 'PUT');
    await readRecord(page, dataApi, data.dict_code, { dict_label: updatedLabel, dict_value: 'value_2',
      dict_type: name, dict_sort: 9, status: '1', remark: '数据修改备注' });
    await expect(rowFor(page, updatedLabel)).toContainText('停用');
    await remove(page, testInfo, rowFor(page, updatedLabel), dataApi, data.dict_code);
    await page.getByRole('button', { name: '返回类型列表', exact: true }).click();
    await search(page, '请输入字典名称', name);
    await remove(page, testInfo, rowFor(page, updated), typeApi, type.dict_id);
  } finally {
    // Child records must be cleaned before their owning type, including partial failures.
    try { await cleanup(page, dataApi, 'dict_code', 'dict_label', [label, updatedLabel], { dict_type: name }); }
    finally { await cleanup(page, typeApi, 'dict_id', 'dict_type', [name], { dict_type: name }); }
  }
});
