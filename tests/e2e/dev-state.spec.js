const { test, expect } = require('playwright/test');

// The main CI E2E job serves bb release; local E2E may intentionally use watch.
test('production build excludes the development snapshot entry', async ({ page }) => {
  test.skip(!process.env.CI, 'Requires the CI production build');
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
