const { expect } = require('playwright/test');

async function enableSemantics(surface) {
  await surface.locator('flt-semantics-placeholder, flt-semantics').first()
    .waitFor({ state: 'attached', timeout: 60000 });
  const placeholder = surface.locator('flt-semantics-placeholder');
  if (await placeholder.count()) await placeholder.evaluate(element => element.click());
}

async function enter(surface, name, value) {
  const input = surface.getByRole('textbox', { name, exact: true });
  await expect(input).toBeVisible();
  await input.click({ force: true });
  await expect.poll(() => input.evaluate(el => el.ownerDocument.activeElement === el)).toBe(true);
  await input.press('ControlOrMeta+A');
  await input.pressSequentially(value, { delay: 50 });
}

function content(surface, text) {
  return surface.getByText(text, { exact: false }).or(surface.getByLabel(text, { exact: false })).first();
}

async function signIn(page, surface = page, username = 'admin', password = 'admin123') {
  await enableSemantics(surface);
  await enter(surface, '用户名', username);
  await enter(surface, '密码', password);
  const login = page.waitForResponse(r => r.url().endsWith('/api/auth/login'));
  const posts = page.waitForResponse(r => r.url().includes('/api/system/post'));
  await surface.getByRole('button', { name: '登录', exact: true }).click();
  expect((await (await login).json()).code).toBe(200);
  expect((await (await posts).json()).code).toBe(200);
}

module.exports = { enableSemantics, enter, signIn, content };
