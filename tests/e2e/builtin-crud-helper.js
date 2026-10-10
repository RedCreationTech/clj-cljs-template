const { expect } = require('playwright/test');
const { randomUUID } = require('node:crypto');

const confirm = /^(确\s*定|确\s*认|OK)$/;
const unique = (kind) => `e2e_${kind}_${randomUUID().replaceAll('-', '').slice(0, 16)}`;
const rowFor = (page, name) => page.locator('tbody tr').filter({ has: page.getByText(name, { exact: true }) });

async function credentials(page) {
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  expect(token).toBeTruthy();
  return { Authorization: `Bearer ${token}` };
}

async function readRows(page, api, params = {}) {
  const response = await page.request.get(api, { headers: await credentials(page), params: { page: 1, size: 100, ...params } });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.code, JSON.stringify(body)).toBe(200);
  return Array.isArray(body.data) ? body.data : body.data.rows;
}

async function readRecord(page, api, id, expected) {
  const response = await page.request.get(`${api}/${id}`, { headers: await credentials(page) });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.code, JSON.stringify(body)).toBe(200);
  expect(body.data).toMatchObject(expected);
  return body.data;
}

async function mutation(page, testInfo, api, method, action) {
  const pending = page.waitForResponse((response) =>
    new URL(response.url()).pathname === api && response.request().method() === method);
  await action();
  const response = await pending;
  const body = await response.json();
  await testInfo.attach(`${method}-${api.replaceAll('/', '-')}`, {
    body: JSON.stringify({ status: response.status(), body }, null, 2), contentType: 'application/json',
  });
  expect(response.status()).toBe(200);
  expect(body.code, JSON.stringify(body)).toBe(200);
  return body;
}

async function save(page, testInfo, modal, api, method) {
  await mutation(page, testInfo, api, method, () => modal.getByRole('button', { name: confirm }).click());
  await expect(modal).toBeHidden();
}

async function search(page, placeholder, value) {
  await page.getByPlaceholder(placeholder, { exact: true }).fill(value);
  await page.getByRole('button', { name: /搜\s*索/ }).first().click();
}

async function remove(page, testInfo, row, api, id, hasConfirmation = true) {
  await mutation(page, testInfo, `${api}/${id}`, 'DELETE', async () => {
    await row.getByRole('button', { name: '删除', exact: true }).click();
    if (hasConfirmation) await page.getByRole('tooltip').getByRole('button', { name: confirm }).click();
  });
  await expect(row).toHaveCount(0);
  const response = await page.request.get(`${api}/${id}`, { headers: await credentials(page) });
  expect((await response.json()).code).toBe(500);
}

// Exact test-owned keys only. Re-query in finally even if POST succeeded but a UI assertion failed.
async function cleanup(page, api, idKey, nameKey, names, params = {}) {
  const rows = await readRows(page, api, params);
  for (const row of rows.filter((item) => names.includes(item[nameKey]))) {
    const response = await page.request.delete(`${api}/${row[idKey]}`, { headers: await credentials(page) });
    expect((await response.json()).code, `cleanup ${api}/${row[idKey]}`).toBe(200);
  }
}

module.exports = { unique, rowFor, readRows, readRecord, save, search, remove, cleanup };
