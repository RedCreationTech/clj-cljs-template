const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

// 用户导入的回归用例。
// 后端 /system/user/import 与 /importTemplate 一直是真实实现的,但前端只把 [:users :import-visible?]
// 置真、没有任何组件订阅它,所以「导入」按钮点了没反应(弹窗压根不存在)。这条用例把弹窗钉住:
// 打开 → 下载模板 → 选 CSV → 提交 → 列表出现导入的用户。

const SUFFIX = String(Date.now()).slice(-6);
const PREFIX = `e2eimp${SUFFIX}`;

const rows = (page) => page.locator('.ant-table-tbody tr.ant-table-row');

async function deleteByApi(page, keyword) {
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  const res = await page.request.fetch(`/api/system/user?user_name=${keyword}&page=1&size=50`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  for (const u of (body.data && body.data.rows) || []) {
    // eslint-disable-next-line no-await-in-loop
    await page.request.fetch(`/api/system/user/${u.user_id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
  }
}

test('用户导入：弹窗、模板下载与 CSV 导入', async ({ page }) => {
  await login(page);
  await page.goto('/system/user');
  await expect(rows(page).first()).toBeVisible();

  await page.getByRole('button', { name: /导\s*入/ }).click();
  const modal = page.locator('#user-import-modal');
  await expect(modal.getByRole('heading', { name: '用户导入' })).toBeVisible();

  const [template] = await Promise.all([
    page.waitForEvent('download', { timeout: 20000 }),
    modal.getByRole('button', { name: /下载模板/ }).click(),
  ]);
  expect(template.suggestedFilename()).toContain('user_import_template');

  const lines = ['user_name,nick_name,email,phonenumber,sex,status,dept_id,remark']
    .concat([1, 2].map((i) => `${PREFIX}${i},导入测试${i},${PREFIX}${i}@example.com,1390000800${i},0,0,4,`))
    .join('\n');
  await modal.locator('input[type=file]').setInputFiles({
    name: 'e2e-import.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`${lines}\n`),
  });
  await expect(modal.getByText('e2e-import.csv')).toBeVisible();

  await modal.getByRole('button', { name: /确\s*定/ }).click();
  await expect(page.getByText(/导入完成：成功 2 条/)).toBeVisible({ timeout: 20000 });
  await expect(modal).toHaveCount(0);

  await page.getByPlaceholder('请输入用户名称').first().fill(PREFIX);
  await page.getByRole('button', { name: /搜\s*索/ }).first().click();
  await expect(rows(page).filter({ hasText: `${PREFIX}1` })).toHaveCount(1);
  await expect(rows(page).filter({ hasText: `${PREFIX}2` })).toHaveCount(1);

  await deleteByApi(page, PREFIX);
});
