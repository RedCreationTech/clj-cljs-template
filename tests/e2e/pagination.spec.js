const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

// 服务端分页的回归用例:页码 / 每页条数必须真的换数据。
// 这些列表页曾经只写了 :total、没写 :current 和 :onChange:翻页时页码跳了、表格内容一动不动,
// 后端始终返回第 1 页,单测/E2E 全绿也发现不了(修好后的约定见 components/pagination.cljs)。

const PREFIX = 'e2e分页';

/** 在浏览器里带着登录令牌调后端接口。 */
async function api(page, method, url, body) {
  return page.evaluate(
    async ([method, url, body]) => {
      const token = localStorage.getItem('ruoyi_token');
      const resp = await fetch('/api' + url, {
        method,
        headers: Object.assign({ 'Content-Type': 'application/json' },
                               token ? { Authorization: `Bearer ${token}` } : {}),
        body: body ? JSON.stringify(body) : undefined,
      });
      return resp.json();
    },
    [method, url, body],
  );
}

/** 建 count 条同前缀的参数,保证列表超过默认的一页(10 条)。 */
async function seedConfigs(page, count) {
  const stamp = Date.now();
  for (let i = 1; i <= count; i += 1) {
    const result = await api(page, 'POST', '/system/config', {
      config_name: `${PREFIX}${i}`,
      config_key: `e2e.paging.${stamp}.${i}`,
      config_value: `v${i}`,
      config_type: 'N',
      remark: 'e2e 分页用例自动创建',
    });
    expect(result.code).toBe(200);
  }
  const list = await api(page, 'GET', `/system/config?page=1&size=50&config_name=${PREFIX}`);
  return list.data.rows.map((row) => row.config_id);
}

/** 按前缀删除参数:用例开头清一次(上次中断的残留会让 config_key 撞唯一索引),结尾再清一次。 */
async function cleanupConfigs(page) {
  const list = await api(page, 'GET', `/system/config?page=1&size=100&config_name=${PREFIX}`);
  for (const row of list.data.rows) {
    await api(page, 'DELETE', `/system/config/${row.config_id}`);
  }
}

function rows(page) {
  return page.locator('table tbody tr.ant-table-row');
}

async function searchConfig(page) {
  await page.goto('/system/config');
  await page.getByPlaceholder('请输入参数名称').fill(PREFIX);
  await page.getByRole('button', { name: /搜\s*索/ }).first().click();
  await expect(rows(page)).toHaveCount(10);
}

test.describe('列表服务端分页', () => {
  test('参数管理:翻到第 2 页要换数据', async ({ page }) => {
    await login(page);
    await cleanupConfigs(page);
    const ids = await seedConfigs(page, 12);
    try {
      await searchConfig(page);
      await expect(page.getByText(`共 ${ids.length} 条`).first()).toBeVisible();
      const pageOne = await rows(page).first().innerText();

      await page.locator('.ant-pagination-item-2').click();
      await expect(rows(page).first()).not.toHaveText(pageOne);
      await expect(rows(page)).toHaveCount(2);
      await expect(page.locator('.ant-pagination-item-2')).toHaveClass(/ant-pagination-item-active/);

      // 回到第 1 页:页码与筛选条件都还在
      await page.locator('.ant-pagination-item-1').click();
      await expect(rows(page)).toHaveCount(10);
      await expect(page.getByPlaceholder('请输入参数名称')).toHaveValue(PREFIX);
    } finally {
      await cleanupConfigs(page);
    }
  });

  test('参数管理:改每页条数要重新请求', async ({ page }) => {
    await login(page);
    await cleanupConfigs(page);
    await seedConfigs(page, 12);
    try {
      await searchConfig(page);
      await page.getByRole('combobox', { name: '页码' }).click();
      const dropdown = page.locator('.ant-select-dropdown:visible');
      await expect(dropdown).toBeVisible();
      await dropdown.locator('.ant-select-item-option').filter({ hasText: '20' }).first().click();
      await expect(rows(page)).toHaveCount(12);
    } finally {
      await cleanupConfigs(page);
    }
  });

  test('操作日志:第 2 页与第 1 页不同', async ({ page }) => {
    await login(page);
    const list = await api(page, 'GET', '/system/oper-log?page=1&size=1');
    test.skip(!list.data || list.data.total <= 10, '日志不足 11 条,无法验证翻页');

    await page.goto('/monitor/operlog');
    await expect(rows(page)).toHaveCount(10);
    const pageOne = await rows(page).first().innerText();

    await page.locator('.ant-pagination-item-2').click();
    await expect(rows(page).first()).not.toHaveText(pageOne);
    await expect(page.locator('.ant-pagination-item-2')).toHaveClass(/ant-pagination-item-active/);
  });
});
