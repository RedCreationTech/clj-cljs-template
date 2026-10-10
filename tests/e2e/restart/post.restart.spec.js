const fs = require('fs');
const { test, expect } = require('playwright/test');
const { login } = require('../auth-helper');
const OK = /^(确 ?定|OK)$/;

test('business record survives a real backend process stop and a fresh process start', async ({ page }) => {
  expect(process.env.E2E_STATE_FILE).toBeTruthy();
  expect(['create', 'verify']).toContain(process.env.E2E_RESTART_PHASE);
  const file = process.env.E2E_STATE_FILE;
  await login(page);
  await page.goto('/system/post');
  if (process.env.E2E_RESTART_PHASE === 'create') {
    const record = { code: `restart_${Date.now()}`, name: `重启保留_${Date.now()}` };
    await page.getByRole('button', { name: '新增' }).click();
    const dialog = page.getByRole('dialog', { name: '新增岗位' });
    await dialog.getByPlaceholder('请输入岗位编码').fill(record.code);
    await dialog.getByPlaceholder('请输入岗位名称').fill(record.name);
    await dialog.getByRole('button', { name: OK }).click();
    await expect(dialog).toBeHidden();
    await expect(page.locator('table tbody tr', { hasText: record.code })).toContainText(record.name);
    fs.writeFileSync(file, JSON.stringify(record));
  } else {
    const record = JSON.parse(fs.readFileSync(file, 'utf8'));
    await page.getByPlaceholder('请输入岗位编码').fill(record.code);
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    const row = page.locator('table tbody tr', { hasText: record.code });
    await expect(row).toContainText(record.name);
    await row.getByRole('button', { name: '编辑' }).click();
    const dialog = page.getByRole('dialog', { name: '修改岗位' });
    await expect(dialog.getByPlaceholder('请输入岗位编码')).toHaveValue(record.code);
    await expect(dialog.getByPlaceholder('请输入岗位名称')).toHaveValue(record.name);
    await dialog.getByRole('button', { name: /^(取 ?消|Cancel)$/ }).click();
    await row.getByRole('button', { name: '删除' }).click();
    await page.getByRole('tooltip').getByRole('button', { name: OK }).click();
    await expect(row).toBeHidden();
    fs.unlinkSync(file);
  }
});
