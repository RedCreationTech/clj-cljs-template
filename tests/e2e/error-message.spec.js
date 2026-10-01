const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

// 业务错误的端到端契约:领域层 errors/fail! 抛的中文原因要原样弹给用户,
// 响应仍是 HTTP 200 + {:code 500 :msg}(RuoYi 前端契约),不能带异常类名/SQL 之类的内部信息。
// 用「参数键名重复」做例子:种子数据里已有 sys.index.skinName,干净库也能跑。

test('业务失败:中文原因回到前端,响应不带内部信息', async ({ page }) => {
  await login(page);
  await page.goto('/system/config');
  await expect(page.getByText('参数名称').first()).toBeVisible();
  const totalBefore = await page.getByText(/共 \d+ 条/).first().innerText();

  await page.getByRole('button', { name: /新\s*增/ }).first().click();
  const modal = page.getByRole('dialog').last();
  const inputs = modal.locator('input.ant-input');
  await inputs.nth(0).fill('重复键名测试');
  await inputs.nth(1).fill('sys.index.skinName');
  await inputs.nth(2).fill('x');

  const saved = page.waitForResponse((r) => r.url().includes('/api/system/config') && r.request().method() === 'POST');
  await modal.getByRole('button', { name: /确\s*定/ }).click();
  const response = await saved;
  const body = await response.json();

  expect(response.status()).toBe(200);
  expect(body.code).toBe(500);
  expect(body.msg).toBe('参数键名已存在');
  expect(JSON.stringify(body)).not.toMatch(/Exception|SQLException|near "|SQLITE/i);

  await expect(page.getByText('参数键名已存在')).toBeVisible();
  // 弹窗提交后自己收起;列表条数不变,说明重复键名那条没写进去
  await expect(modal).toBeHidden();
  await expect(page.getByText(totalBefore).first()).toBeVisible();
});
