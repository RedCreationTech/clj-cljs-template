const { test: base, expect, request: playwrightRequest } = require('playwright/test');
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
  const [response] = await Promise.all([
    page.waitForResponse((response) =>
      new URL(response.url()).pathname === api && response.request().method() === method,
    { timeout: 15000 }),
    action(),
  ]);
  const body = await response.json();
  await testInfo.attach(`${method}-${api.replaceAll('/', '-')}`, {
    body: JSON.stringify({ status: response.status(), body }, null, 2), contentType: 'application/json',
  });
  expect(response.status()).toBe(200);
  expect(body.code, JSON.stringify(body)).toBe(200);
  return body;
}

async function save(page, testInfo, modal, api, method) {
  await mutation(page, testInfo, api, method, () => modal.getByRole('button', { name: confirm }).click({ timeout: 15000 }));
  await expect(modal).toBeHidden();
}

async function search(page, placeholder, value) {
  await page.getByPlaceholder(placeholder, { exact: true }).fill(value);
  await page.getByRole('button', { name: /搜\s*索/ }).first().click();
}

async function remove(page, testInfo, row, api, id, hasConfirmation = true) {
  await mutation(page, testInfo, `${api}/${id}`, 'DELETE', async () => {
    await row.getByRole('button', { name: /删\s*除$/ }).click({ timeout: 15000 });
    if (hasConfirmation) await page.getByRole('tooltip').getByRole('button', { name: confirm }).click({ timeout: 15000 });
  });
  await expect(row).toHaveCount(0);
  const response = await page.request.get(`${api}/${id}`, { headers: await credentials(page) });
  expect((await response.json()).code).toBe(500);
}

// Exact test-owned keys only; rediscover even if POST succeeded before a UI failure.
async function cleanup(request, headers, api, idKey, nameKey, names, params) {
  const getOwned = async () => {
    const response = await request.get(api, { headers, params: { page: 1, size: 100, ...params } });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.code, JSON.stringify(body)).toBe(200);
    const rows = Array.isArray(body.data) ? body.data : body.data.rows;
    return rows.filter((item) => names.includes(item[nameKey]));
  };
  for (const row of await getOwned()) {
    const response = await request.delete(`${api}/${row[idKey]}`, { headers });
    expect(response.ok()).toBeTruthy();
    expect((await response.json()).code, `cleanup ${api}/${row[idKey]}`).toBe(200);
  }
  expect(await getOwned(), `remaining test-owned records: ${api}`).toEqual([]);
}

// Separate API lifecycle and teardown budget survive UI/test timeout and page closure.
const test = base.extend({
  builtinCleanup: [async ({ baseURL }, use, testInfo) => {
    const records = [];
    await use(async (page, api, idKey, nameKey, names, params = {}) => {
      records.push({ headers: await credentials(page), api, idKey, nameKey, names, params });
    });
    if (!records.length) return;
    const errors = [];
    let request;
    try {
      request = await playwrightRequest.newContext({ baseURL, timeout: 10000 });
      // Registration order is child before parent for dictionary records.
      for (const { headers, api, idKey, nameKey, names, params } of records) {
        try { await cleanup(request, headers, api, idKey, nameKey, names, params); }
        catch (error) { errors.push(error); }
      }
    } catch (error) { errors.push(error); }
    finally {
      if (request) {
        try { await request.dispose(); }
        catch (error) { errors.push(error); }
      }
    }
    if (errors.length) {
      await testInfo.attach('builtin-cleanup-errors', {
        body: errors.map(error => error.stack || String(error)).join('\n\n'),
        contentType: 'text/plain',
      });
      // Cleanup errors remain visible without replacing the original UI diagnosis.
      if (testInfo.status === testInfo.expectedStatus) {
        throw new AggregateError(errors, 'Built-in CRUD cleanup failed');
      }
    }
  }, { timeout: 60000 }],
});

module.exports = { test, unique, rowFor, readRows, readRecord, save, search, remove };
