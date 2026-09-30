const { test, expect } = require('playwright/test');
const { login } = require('./auth-helper');

// 个人中心与缓存监控的回归用例。
// 两处都是「点了没反应」型的空壳:
//   1) 头像上传的 onChange 里 dispatch 的是 [:api/upload-avatar …],而 :api/upload-avatar 只注册成
//      fx(re-frame 的 fx 名不能当 id 直接 dispatch),高级编译里断言被裁掉,请求压根没发出去;
//      现在补了 :profile/upload-avatar 事件,页面 dispatch 事件、fx 才跑起来。
//   2) 缓存「清空」原来 DELETE /system/cache,后端路由是 /system/cache/clear,直接 405。

// 1x1 透明 PNG,仓库里不放二进制测试文件
const PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

test('个人中心：头像上传后档案与顶栏同步换图', async ({ page }) => {
  await login(page);
  await page.goto('/system/user/profile');
  await expect(page.getByText('个人信息').first()).toBeVisible();

  const circle = page.locator('#avatar-upload + div');
  const before = await circle.locator('img').getAttribute('src');

  await page.evaluate(async (b64) => {
    const res = await fetch(`data:image/png;base64,${b64}`);
    const blob = await res.blob();
    const file = new File([blob], 'e2e-avatar.png', { type: 'image/png' });
    const dt = new DataTransfer();
    dt.items.add(file);
    const el = document.getElementById('avatar-upload');
    el.files = dt.files;
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, PNG_BASE64);

  const upload = await page.waitForResponse(
    (r) => r.url().includes('/api/system/profile/avatar'),
    { timeout: 20000 }
  );
  expect(upload.status()).toBe(200);
  expect((await upload.json()).code).toBe(200);

  const img = circle.locator('img');
  await expect(img).toBeVisible({ timeout: 20000 });
  await expect(img).not.toHaveAttribute('src', before ?? '');

  await expect(page.locator('.ant-layout-header .ant-avatar img, .ant-layout-header .ant-avatar-image').first())
    .toBeVisible({ timeout: 20000 });
});

test('缓存监控：清空按钮命中 /system/cache/clear', async ({ page }) => {
  await login(page);
  await page.goto('/monitor/cache');
  await expect(page.getByText('基本信息').first()).toBeVisible();

  await page.getByRole('button', { name: /清\s*空/ }).first().click();
  await page.locator('.ant-popconfirm .ant-btn-primary, .ant-popover .ant-btn-primary').first().click();

  const cleared = await page.waitForResponse(
    (r) => r.url().includes('/api/system/cache/clear'),
    { timeout: 20000 }
  );
  expect(cleared.status()).toBe(200);
  await expect(page.getByText('缓存已清空', { exact: true })).toBeVisible({ timeout: 20000 });
});
