const fs = require('fs');
const path = require('path');
const { test, expect } = require('playwright/test');
const generated = require('../generated-module-helper');

const config = {
  label: '冒烟模块', menuPath: 'biz/ci-demo', apiPath: '/biz/ci-demo',
  fields: [
    { col: 'title', type: 'string', label: '标题', required: true },
    { col: 'qty', type: 'int', label: '数量' },
    { col: 'price', type: 'decimal', label: '单价' },
    { col: 'due_on', type: 'date', label: '到期日' },
    { col: 'active', type: 'bool', label: '启用' },
    { col: 'remark', type: 'text', label: '备注' },
  ],
  searchCols: ['title', 'qty', 'due_on'],
};

if (process.env.E2E_GENERATED_MODULE === 'ci-demo') test('generated six-field record survives a real backend process restart', async ({ page }) => {
  // The matrix explicitly enables this test; a missing generated module is a failure.
  expect(fs.existsSync(path.join(__dirname, '..', 'ci-demo.spec.js'))).toBe(true);
  expect(process.env.E2E_STATE_FILE).toBeTruthy();
  expect(['create', 'verify']).toContain(process.env.E2E_RESTART_PHASE);
  const file = process.env.E2E_STATE_FILE + '.generated';
  await generated.openPage(page, config);
  if (process.env.E2E_RESTART_PHASE === 'create') {
    const values = generated.sample(config.fields, `restart-six-${Date.now()}`);
    const id = await generated.createInBrowser(page, config, values);
    expect(id).toBeGreaterThan(0);
    await generated.readInBrowser(page, config, id, values);
    fs.writeFileSync(file, JSON.stringify({ id, values }));
  } else {
    const { id, values } = JSON.parse(fs.readFileSync(file, 'utf8'));
    expect(Object.keys(values).sort()).toEqual(config.fields.map((f) => f.col).sort());
    const dialog = await generated.readInBrowser(page, config, id, values);
    await dialog.getByRole('button', { name: /取\s*消|Cancel/ }).click();
    const row = generated.rowById(page, id);
    await row.getByRole('button', { name: /删除/ }).click();
    await page.getByRole('tooltip').getByRole('button', { name: /^(确 ?定|OK)$/ }).click();
    await expect(row).toHaveCount(0);
    expect((await generated.api(page, config, 'GET', '/' + id)).body.code).toBe(500);
    fs.unlinkSync(file);
  }
});
