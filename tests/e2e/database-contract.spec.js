const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

test('database identity, version, configuration and migrated admin seed match the requested dialect', async ({ page }, testInfo) => {
  await login(page);
  const expected = process.env.EXPECTED_DB;
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  const response = await page.request.get('/api/monitor/datasource', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(200);
  const body = await response.json();
  expect(body.code).toBe(200);
  const info = body.data;
  expect(info.db_version).toMatch(/^(SQLite|MySQL|PostgreSQL) \d/);
  if (expected) {
    const product = { sqlite: 'SQLite', mysql: 'MySQL', postgresql: 'PostgreSQL' }[expected];
    expect(product).toBeTruthy();
    expect(process.env.DB_TYPE).toBe(expected);
    expect(process.env.DB_ENABLED).toBe(expected);
    expect(info.db_name.split('?')[0]).toBe(process.env.JDBC_URL.replace(/^jdbc:/, '').split('?')[0]);
    if (expected !== 'sqlite') expect(info.db_name).toContain('/' + process.env.E2E_DB_NAME);
    expect(info.db_version).toMatch(new RegExp(`^${product} \\d`));
    expect(info.db_name).toMatch(new RegExp(`^${expected}:`));
    if (expected === 'mysql') expect(info.db_version).toMatch(/^MySQL 8\.4\./);
    if (expected === 'postgresql') expect(info.db_version).toMatch(/^PostgreSQL 17\./);
  }
  await testInfo.attach('database-identity.json', { body: JSON.stringify(info, null, 2), contentType: 'application/json' });
  console.log('VERIFIED DATABASE:', info.db_version, info.db_name);
  await page.goto('/system/user');
  await expect(page.locator('table tbody tr.ant-table-row', { hasText: 'admin' }).first()).toBeVisible();
  await page.goto('/system/role');
  await expect(page.locator('table tbody tr.ant-table-row', { hasText: '超级管理员' })).toBeVisible();
});
