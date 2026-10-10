const { expect } = require('playwright/test');
const { login } = require('./auth-helper');

const OK = /^(确 ?定|OK)$/;
const rows = (page) => page.locator('table tbody tr.ant-table-row');
const rowById = (page, id) => page.locator(`table tbody tr.ant-table-row[data-row-key="${id}"]`);
const endpoint = (config) => '/api' + config.apiPath;

function sample(fields, stamp, edited = false) {
  return Object.fromEntries(fields.map(({ col, type }) => [col, {
    string: `${stamp}${edited ? '-edited' : ''}`,
    text: `多行备注 ${stamp}\n${edited ? '修改后的内容' : '初始内容'}`,
    int: edited ? 23 : 7,
    decimal: edited ? 27.75 : 12.5,
    date: edited ? '2027-02-18' : '2026-01-09',
    bool: edited ? '0' : '1',
  }[type]]));
}

async function api(page, config, method, suffix = '', data) {
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  const response = await page.request.fetch(endpoint(config) + suffix, {
    method, headers: { Authorization: `Bearer ${token}` }, data,
  });
  return { status: response.status(), body: await response.json() };
}

async function success(page, config, method, suffix = '', data) {
  const result = await api(page, config, method, suffix, data);
  expect(result.status).toBe(200);
  expect(result.body.code, JSON.stringify(result.body)).toBe(200);
  return result.body.data;
}

async function openPage(page, config) {
  await login(page);
  const response = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config)
    && r.request().method() === 'GET');
  await page.goto('/' + config.menuPath);
  expect((await response).status()).toBe(200);
  await expect(page.locator('table')).toBeVisible();
}

async function fillForm(dialog, fields, values) {
  for (const field of fields) {
    const input = dialog.getByLabel(field.label, { exact: true });
    if (field.type === 'bool') {
      const checked = values[field.col] === '1';
      if ((await input.getAttribute('aria-checked')) !== String(checked)) await input.click();
      await expect(input).toHaveAttribute('aria-checked', String(checked));
    } else await input.fill(String(values[field.col]));
  }
}

async function assertForm(dialog, fields, values) {
  for (const field of fields) {
    const input = dialog.getByLabel(field.label, { exact: true });
    if (field.type === 'bool') await expect(input).toHaveAttribute('aria-checked', String(values[field.col] === '1'));
    else if (field.type === 'decimal') expect(Number(await input.inputValue())).toBe(values[field.col]);
    else await expect(input).toHaveValue(String(values[field.col]));
  }
}

async function save(page, config, dialog, method, suffix = '') {
  const pending = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config) + suffix
    && r.request().method() === method);
  await dialog.getByRole('button', { name: OK }).click();
  const response = await pending;
  expect(response.status()).toBe(200);
  const body = await response.json();
  expect(body.code, JSON.stringify(body)).toBe(200);
  await expect(dialog).toBeHidden();
  return body.data;
}

async function createInBrowser(page, config, values) {
  await page.getByRole('button', { name: /新增/ }).first().click();
  const dialog = page.getByRole('dialog', { name: '新增' + config.label, exact: true });
  await expect(dialog).toBeVisible();
  await fillForm(dialog, config.fields, values);
  return (await save(page, config, dialog, 'POST')).id;
}

async function search(page, config, value) {
  const key = config.fields.find((f) => f.type === 'string' && config.searchCols.includes(f.col));
  if (!key) return;
  await page.getByPlaceholder('请输入' + key.label, { exact: true }).fill(value);
  const pending = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config)
    && r.request().method() === 'GET' && new URL(r.url()).searchParams.get(key.col) === value);
  await page.getByRole('button', { name: /搜\s*索/ }).first().click();
  const response = await pending;
  expect(response.status()).toBe(200);
  expect((await response.json()).code).toBe(200);
}

async function readInBrowser(page, config, id, values) {
  await page.reload();
  const stringField = config.fields.find((f) => f.type === 'string' && config.searchCols.includes(f.col));
  if (stringField) await search(page, config, values[stringField.col]);
  const row = rowById(page, id);
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: /编辑/ }).click();
  const dialog = page.getByRole('dialog', { name: '修改' + config.label, exact: true });
  await expect(dialog).toBeVisible();
  await assertForm(dialog, config.fields, values);
  expect(await success(page, config, 'GET', '/' + id)).toMatchObject(values);
  return dialog;
}

async function crud(page, config) {
  await openPage(page, config);
  const initial = sample(config.fields, `e2e-${Date.now()}`);
  const edited = sample(config.fields, initial[config.fields[0].col] + '-v2', true);
  const id = await createInBrowser(page, config, initial);
  try {
    let dialog = await readInBrowser(page, config, id, initial);
    await fillForm(dialog, config.fields, edited);
    await save(page, config, dialog, 'PUT', '/' + id);
    dialog = await readInBrowser(page, config, id, edited);
    await dialog.getByRole('button', { name: /取\s*消|Cancel/ }).click();
    const row = rowById(page, id);
    await row.getByRole('button', { name: /删除/ }).click();
    const pending = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config) + '/' + id
      && r.request().method() === 'DELETE');
    await page.getByRole('tooltip').getByRole('button', { name: OK }).click();
    const response = await pending;
    expect(response.status()).toBe(200);
    expect((await response.json()).code).toBe(200);
    await expect(row).toHaveCount(0);
    expect((await api(page, config, 'GET', '/' + id)).body.code).toBe(500);
    return config.fields.length;
  } finally {
    await success(page, config, 'DELETE', '/' + id);
  }
}

async function invalid(page, config) {
  await openPage(page, config);
  const values = sample(config.fields, `invalid-${Date.now()}`);
  const required = config.fields.find((f) => f.required && f.type !== 'bool');
  if (required) {
    await page.getByRole('button', { name: /新增/ }).first().click();
    const dialog = page.getByRole('dialog', { name: '新增' + config.label, exact: true });
    await fillForm(dialog, config.fields, values);
    await dialog.getByLabel(required.label, { exact: true }).fill('');
    await dialog.getByRole('button', { name: OK }).click();
    await expect(dialog.getByText('请输入' + required.label, { exact: true })).toBeVisible();
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /取\s*消|Cancel/ }).click();
  }
  const id = (await success(page, config, 'POST', '', values)).id;
  const before = await success(page, config, 'GET', '?page=1&size=1');
  let assertions = 0;
  try {
    for (const field of config.fields) {
      // Native date/number controls sanitize malformed text; send malformed JSON to the real route too.
      const bad = { string: { invalid: true }, text: { invalid: true }, int: 'not-an-integer', decimal: 'not-a-number',
        date: 'not-a-date', bool: 'yes' }[field.type];
      for (const method of ['POST', 'PUT']) {
        const result = await api(page, config, method, method === 'PUT' ? '/' + id : '',
          { ...values, [field.col]: bad });
        // No dialect exceptions: PostgreSQL must reject invalid dates without a 500 response.
        expect(result.status, `${method} invalid ${field.type}`).toBe(400);
        assertions += 1;
      }
    }
    expect(await success(page, config, 'GET', '/' + id)).toMatchObject(values);
    expect((await success(page, config, 'GET', '?page=1&size=1')).total).toBe(before.total);
    const badQueries = ['?page=0&size=10', '?page=1&size=0'];
    for (const field of config.fields.filter((f) => config.searchCols.includes(f.col) && f.type !== 'string')) {
      badQueries.push(`?page=1&size=10&${field.col}=invalid-value`);
    }
    for (const suffix of badQueries) {
      expect((await api(page, config, 'GET', suffix)).status).toBe(400);
    }
    return assertions;
  } finally {
    await success(page, config, 'DELETE', '/' + id);
  }
}

async function typedFilters(page, config, expectedCount) {
  for (const field of config.fields.filter((f) => config.searchCols.includes(f.col)
    && ['int', 'date'].includes(f.type))) {
    const input = page.getByPlaceholder(field.type === 'int' ? field.label : '请输入' + field.label, { exact: true });
    for (const [value, count] of [[field.type === 'int' ? '7' : '2026-01-09', expectedCount],
      [field.type === 'int' ? '8' : '2026-01-10', 0], ['', expectedCount]]) {
      await input.fill(value);
      const pending = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config)
        && r.request().method() === 'GET'
        && (new URL(r.url()).searchParams.get(field.col) || '') === value);
      await page.getByRole('button', { name: /搜\s*索/ }).first().click();
      const response = await pending;
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.code, JSON.stringify(body)).toBe(200);
      expect(body.data.total).toBe(count);
      expect(new URL(response.url()).searchParams.get('page')).toBe('1');
      await expect(rows(page)).toHaveCount(count);
    }
  }
}

async function paging(page, config) {
  await openPage(page, config);
  const key = config.fields.find((f) => f.type === 'string' && config.searchCols.includes(f.col));
  // Modules without a searchable string can still exercise unfiltered server paging.
  const stamp = `paging-${Date.now()}`;
  const ids = [];
  try {
    for (let i = 0; i < 12; i += 1) {
      const values = sample(config.fields, `${stamp}-${i}`);
      ids.push((await success(page, config, 'POST', '', values)).id);
    }
    await page.reload();
    if (key) await search(page, config, stamp);
    await expect(rows(page)).toHaveCount(10);
    if (key) await expect(rows(page)).toHaveText(Array(10).fill(new RegExp(stamp)));
    const firstIds = await rows(page).locator('td:first-child').allTextContents();
    const pending = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config)
      && new URL(r.url()).searchParams.get('page') === '2');
    await page.locator('.ant-pagination-item-2').click();
    const response = await pending;
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.code).toBe(200);
    expect(new URL(response.url()).searchParams.get('size')).toBe('10');
    if (key) {
      expect(new URL(response.url()).searchParams.get(key.col)).toBe(stamp);
      expect(body.data.total).toBe(12);
      await expect(rows(page)).toHaveCount(2);
    }
    await expect(rows(page).first().locator('td').first()).toHaveText(String(body.data.rows[0].id));
    const secondIds = await rows(page).locator('td:first-child').allTextContents();
    expect(secondIds.every((id) => !firstIds.includes(id))).toBe(true);
    await page.locator('.ant-pagination-item-1').click();
    await expect(rows(page)).toHaveCount(10);
    await page.locator('.ant-pagination-options .ant-select').click();
    const sizeResponse = page.waitForResponse((r) => new URL(r.url()).pathname === endpoint(config)
      && new URL(r.url()).searchParams.get('size') === '20');
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: '20' }).first().click();
    expect((await sizeResponse).status()).toBe(200);
    if (key) {
      await expect(rows(page)).toHaveCount(12);
      expect((await rows(page).locator('td:first-child').allTextContents()).map(Number).sort((a, b) => a - b))
        .toEqual([...ids].sort((a, b) => a - b));
      await typedFilters(page, config, 12);
      await search(page, config, `${stamp}-missing`);
      await expect(rows(page)).toHaveCount(0);
      await search(page, config, stamp);
      await expect(rows(page)).toHaveCount(12);
    }
    return ids.length;
  } finally {
    for (const id of ids) await success(page, config, 'DELETE', '/' + id);
  }
}

module.exports = { rowById, sample, api, success, openPage, createInBrowser, readInBrowser, crud, invalid, paging };
