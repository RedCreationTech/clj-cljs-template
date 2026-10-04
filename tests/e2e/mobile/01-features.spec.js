// 分镜 01｜功能:一份 ClojureDart 代码,跑真后端的那套接口
const { test, expect } = require('@playwright/test');
const m = require('./mobile-helper');

test('01｜功能:登录、列表、服务端分页、退出', async ({ page }) => {
  await m.open(page);

  await m.chapter(page, {
    n: 1,
    title: '移动端能做什么',
    subtitle: '登录 → 岗位列表 → 翻页 → 退出,全程真接口',
    points: [
      'ClojureDart 写的 Flutter App,不是网页套壳',
      '打的是后台同一套 RuoYi 接口:JWT + sys_online 会话',
      '列表是服务端分页:page / size,和网页端一个口径',
      '亮色 / 暗色两套主题,靠一条事件切换',
    ],
  });

  await m.say(page, '屏幕中间这个手机框里跑的就是编译后的 App:同一份 .cljd 源码,能出 macOS、Android、iOS。');
  await m.code(page, [
    '$ bb mobile:run            # 编译并起 App',
    '$ bb mobile:compile        # 只编译,当静态检查用',
    '$ bb mobile:test           # re-dash 事件的单元测试',
  ], { label: 'babashka' });
  await m.codeOff(page);

  await m.say(page, '首屏是登录页。App 由 Flutter 画在 canvas 上,同时提供辅助功能语义树,演示按输入框和按钮的名称操作。');
  await m.step(page, '提交之后连着三个请求:签令牌、取身份、取列表,一条事件链串起来。', async () => {
    // 监听必须在点提交之前挂好:登录响应回来之后几百毫秒列表就取完了,
    // 事后再 waitForResponse 会永远等不到已经发生的那一次。
    const login = m.expectRequest(page, '/auth/login');
    const info = m.expectRequest(page, '/auth/getInfo');
    const posts = m.expectRequest(page, '/system/post');
    await m.login(page);
    await loginOk(login);
    const infoBody = await (await info).json();
    expect(infoBody.data.user.user_name).toBe('admin');
    const body = await (await posts).json();
    expect(body.code).toBe(200);
    expect(body.data.total).toBeGreaterThan(0);
    expect(body.data.rows.length).toBeGreaterThan(0);
  });

  await m.say(page, '每张卡片一个岗位:名称、编号、编码、状态,后端给什么就显示什么,前端不加工。');
  await m.quiet(page, 1400);

  await m.step(page, '底部分页条写的是「1 / 2 页 · 共 24 条」,点右箭头翻到第 2 页。', async () => {
    const res = m.expectRequest(page, 'page=2');
    await m.button(page, '下一页', { ms: 200 });
    const body = await (await res).json();
    expect(body.data.rows.length).toBeGreaterThan(0);
    expect(body.data.rows[0].post_id).not.toBe(1);
  });

  await m.say(page, '翻回来的请求带的是 page=1:页码存在 app-db 里,不是控件的私有状态,所以刷新、切主题都不会把它弄丢。');
  await m.step(page, '再点左箭头回第一页。', async () => {
    const res = m.expectRequest(page, 'page=1');
    await m.button(page, '上一页', { ms: 200 });
    expect((await (await res).json()).data.rows[0].post_id).toBe(1);
  });

  await m.say(page, '列表还能下拉刷新:手指往下拽一下,重新派发 :posts/refresh,页码保持不变。');
  await m.quiet(page, 900);

  await m.step(page, '右上角提供账号信息、主题切换和退出登录,这里演示退出。', async () => {
    const res = m.expectRequest(page, '/auth/logout');
    await m.button(page, '退出登录', { ms: 200 });
    expect((await res).status()).toBe(200);
  });
  await m.say(page, '退出之后令牌、身份、列表数据一次清干净,画面回到登录页。');
  await m.quiet(page, 1200);
});

async function loginOk(resPromise) {
  const res = await resPromise;
  expect(res.status()).toBe(200);
  const body = await res.json();
  expect(body.code).toBe(200);
  expect(typeof body.data.token).toBe('string');
}
