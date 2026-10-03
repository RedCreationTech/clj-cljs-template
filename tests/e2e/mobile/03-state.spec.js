// 分镜 03｜状态管理:整个 App 只有一份数据
const { test, expect } = require('@playwright/test');
const m = require('./mobile-helper');

const X = { next: 271, theme: 329 };
const Y = { pager: 822, bar: 24 };

test('03｜状态管理:app-db、纯事件与订阅', async ({ page }) => {
  await m.open(page);

  await m.chapter(page, {
    n: 3,
    title: '状态只有一份',
    subtitle: 'app-db + 纯事件 + 订阅,界面不存数据',
    points: [
      're-dash:app-db 是一个不可变的 map,只此一份',
      '事件只描述「要发哪个请求、成功回哪个事件」,IO 在效果层',
      '视图用 :watch 订阅派生值,不自己持有数据',
      '令牌只存在 app-db 里,没有落进任何本地存储',
    ],
  });

  await m.code(page, [
    '$ bb mobile:test   # 事件是纯函数,可以直接单测',
    'ruoyi/model.cljd  initial-db',
    '{ :route  :login',
    '  :token  nil        :profile  nil',
    '  :posts  {:page 1 :size 20 :rows [] :total 0 :pending? false}',
    '  :notice {:msg nil} :theme {:dark? false} :permissions #{} }',
  ], { label: 're-dash', voice: '初始状态长这样:路由、令牌、身份、列表、提示条、主题开关,全在同一个 map 里。' });
  await m.codeOff(page);

  await m.step(page, '登录是一条链:存令牌之后派发 :app/session-opened,取回身份再派发 :posts/load。', async () => {
    const r = await m.signIn(page);
    expect((await r.posts.json()).code).toBe(200);
  });
  await m.say(page, '每一步都是一条事件,事件返回的是「改数据的 delta」加「下一步派发什么」,没有一处直接调 HTTP。');
  await m.quiet(page, 1200);

  await m.step(page, '翻到第 2 页:页码并进 app-db 的 :posts 里再发请求。', async () => {
    const res = m.expectRequest(page, 'page=2');
    await m.tap(page, X.next, Y.pager, { ms: 300 });
    const body = await (await res).json();
    expect(body.data.rows[0].post_id).toBe(21);
  });

  const posts = m.counter(page, '/system/post');
  await m.step(page, '现在换一次主题:数据没变,所以一条请求都不该再发。', async () => {
    const before = posts.n;
    await m.tap(page, X.theme, Y.bar, { ms: 300 });
    await m.wait(page, 2000);
    expect(posts.n).toBe(before);
    expect(await m.tone(page)).toBeLessThan(120);
  });
  await m.say(page, '页码、列表、身份都还在那份 app-db 里:主题只是另一个布尔,换它不会把别的东西冲掉。');
  await m.quiet(page, 1400);

  await m.step(page, '再点一次换回亮色,同样不发请求。', async () => {
    const before = posts.n;
    await m.tap(page, X.theme, Y.bar, { ms: 300 });
    await m.wait(page, 1800);
    expect(posts.n).toBe(before);
    expect(await m.tone(page)).toBeGreaterThan(150);
  });

  await m.say(page, '最后是它的边界:这份状态只在内存里。刷新一下,画面回到登录页。');
  await m.reload(page);
  await m.wait(page, 3000);
  expect(posts.n).toBe(0);
  await m.say(page, '令牌没有写进本地存储,所以刷新之后不会偷偷续上会话——要不要记住登录,是产品决定,不是顺手写个 storage。');
  await m.step(page, '重新登录一次,同一条链再走一遍。', async () => {
    const r = await m.signIn(page);
    expect((await r.posts.json()).data.rows.length).toBeGreaterThan(0);
  });
  await m.quiet(page, 1200);
});
