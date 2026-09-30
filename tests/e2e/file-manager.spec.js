const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

// 文件管理与用户导出的回归用例。
// 文件管理页原来只 dispatch 了 :file/fetch / :file/upload 等事件,而 events 与 subs 里根本没注册过,
// 打开页面就被错误边界接住显示「页面加载失败」;列表的 modified 是毫秒数,渲染成 Date 对象当
// React 子节点也会抛错。用户导出按钮走 impexp 的 CSV 下载,api/users.cljs 里还留过一个只 console.log
// 的空壳(已删)。

const rows = (page) => page.locator('.ant-table-tbody > tr.ant-table-row');

/** 点一下再等下载事件;超时返回 null。 */
async function clickAndWaitDownload(page, locator) {
  const waiting = page.waitForEvent('download', { timeout: 8000 }).catch(() => null);
  await locator.click();
  return waiting;
}

test.describe('文件管理', () => {
  test('打开页面、上传、下载、删除', async ({ page }) => {
    await login(page);
    const name = `e2e-file-${Date.now()}.txt`;

    await page.goto('/system/file');
    await expect(page.getByRole('button', { name: '上传' })).toBeVisible();

    await page.setInputFiles('input[type=file]', {
      name,
      mimeType: 'text/plain',
      buffer: Buffer.from('e2e 文件管理用例\n'),
    });
    const row = page.locator('.ant-table-tbody > tr.ant-table-row', { hasText: name });
    await expect(row).toBeVisible();
    // 修改时间必须是可读文本,不能是时间戳或 [object Object]
    await expect(row).toContainText(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/);

    const download = await clickAndWaitDownload(page, row.getByRole('button', { name: '下载' }));
    expect(download).not.toBeNull();
    expect(download.suggestedFilename()).toContain(name);

    await row.getByRole('button', { name: '删除' }).click();
    await page.locator('.ant-popconfirm .ant-btn-primary, .ant-popover .ant-btn-primary').first().click();
    await expect(row).toBeHidden({ timeout: 8000 });
  });
});

test.describe('用户导出', () => {
  test('导出按钮触发 CSV 下载', async ({ page }) => {
    await login(page);
    await page.goto('/system/user');
    await expect(rows(page).first()).toBeVisible();

    const download = await clickAndWaitDownload(page, page.getByRole('button', { name: /导\s*出/ }).first());
    expect(download).not.toBeNull();
    expect(download.suggestedFilename()).toMatch(/\.csv$/);
  });
});
