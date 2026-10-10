const { test, expect } = require('playwright/test');

// The main CI E2E job serves bb release; local E2E may intentionally use watch.
test('production build excludes the development snapshot entry', async ({ page }) => {
  test.skip(!process.env.CI || process.env.SNAPSHOT_BUILD === 'dev', 'Requires the CI production build');
  await page.goto('/');
  await expect(page.getByPlaceholder('用户名')).toBeVisible();
  expect(await page.evaluate(() =>
    typeof window.com?.ruoyi?.frontend?.dev_snapshot?.snapshot_edn)).toBe('undefined');
  const bundle = await page.request.get('/js/app.js');
  expect(bundle.ok()).toBeTruthy();
  const code = await bundle.text();
  expect(code).not.toContain('snapshot_edn');
  expect(code).not.toContain('dev_snapshot');
});

const readSnapshot = (page) => page.evaluate(() =>
  window.com.ruoyi.frontend.dev_snapshot.snapshot_edn());
const runtimeId = (snapshot) => snapshot.match(/:runtime-id "([a-f0-9-]{36})"/)?.[1];

test('development snapshot distinguishes tabs and page reloads without form data', async ({ page, context }) => {
  test.skip(process.env.SNAPSHOT_BUILD !== 'dev', 'Requires the development preload build');
  await page.goto('/');
  await expect(page.getByPlaceholder('用户名')).toBeVisible();
  await page.getByPlaceholder('用户名').fill('SNAPSHOT_PRIVATE_FORM_MARKER');
  const first = await readSnapshot(page);
  expect(first).toContain(':status :available');
  expect(first).not.toContain('SNAPSHOT_PRIVATE_FORM_MARKER');
  expect(first.length).toBeLessThan(4000);
  const id = runtimeId(first);
  expect(id).toMatch(/^[a-f0-9-]{36}$/);
  expect(runtimeId(await readSnapshot(page))).toBe(id);
  const secondTab = await context.newPage();
  await secondTab.goto('/');
  await expect(secondTab.getByPlaceholder('用户名')).toBeVisible();
  const secondId = runtimeId(await readSnapshot(secondTab));
  expect(secondId).toMatch(/^[a-f0-9-]{36}$/);
  expect(secondId).not.toBe(id);
  await page.reload();
  await expect(page.getByPlaceholder('用户名')).toBeVisible();
  const reloadedId = runtimeId(await readSnapshot(page));
  expect(reloadedId).toMatch(/^[a-f0-9-]{36}$/);
  expect(reloadedId).not.toBe(id);
  await secondTab.close();
});
