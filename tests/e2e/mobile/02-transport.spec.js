// 分镜 02｜通讯方式:一个出口、一种信封、四类失败
const { test, expect } = require('@playwright/test');
const m = require('./mobile-helper');

test('02｜通讯方式:一个出口、一种信封、四类失败', async ({ page }) => {
  await m.open(page);

  await m.chapter(page, {
    n: 2,
    title: '和后端怎么说话',
    subtitle: '一个出口、一种信封、四类失败',
    points: [
      '全 App 只有一处在打接口:ruoyi/api.cljd',
      '信封固定 {:code :msg :data},成功失败都是 HTTP 200',
      '失败归四类:business / forbidden / unauthorized / network',
      '走 JSON 不走 transit:实测体积只差 6%,后端只协商 JSON',
    ],
  });

  await m.code(page, [
    '$ curl -s localhost:3210/api/system/post?page=1&size=20',
    '{ "code": 200, "msg": "操作成功",',
    '  "data": { "rows": [ … ], "total": 24 } }',
    '业务失败:HTTP 还是 200,code 不是 200,原因在 msg',
  ], { label: 'http', voice: '网页端、移动端打的是同一套接口:分页只发 page 和 size,数据在 data 的 rows 和 total 里。' });
  await m.codeOff(page);

  await m.step(page, '故意把密码多打一位,看看失败怎么走完这条链。', async () => {
    const res = m.expectRequest(page, '/auth/login');
    await m.enter(page, '用户名', 'admin');
    await m.enter(page, '密码', 'admin1234');
    await m.submit(page, { ms: 300 });
    const body = await (await res).json();
    expect(body.code).not.toBe(200);
    expect(body.msg).toBeTruthy();
  });
  await m.say(page, '红条里那行字是后端给的 :msg,App 一个字都不改:业务码不是 200 就归成 :business,只负责显示。')
  await m.quiet(page, 1800);

  await m.step(page, '重载后填入正确密码:输入框通过语义名称定位并替换内容。这次令牌、身份、列表三条请求连着回来。', async () => {
    await m.reload(page);
    const info = m.expectRequest(page, '/auth/getInfo');
    const posts = m.expectRequest(page, '/system/post');
    await m.login(page);
    const body = await (await (await info)).json();
    expect(body.data.user.user_name).toBe('admin');
    // 令牌由 api.cljd 统一加在请求头上,页面层碰不到它
    expect((await info).request().headers().authorization).toMatch(/^Bearer /);
    expect((await posts).status()).toBe(200);
  });
  await m.say(page, '每个请求都自动带着 authorization: Bearer …,包括登录之后紧跟着的这两条。');
  await m.quiet(page, 1200);

  await m.breakApi(page);
  await m.step(page, '再把网络掐掉(录制时直接拦掉请求):同一页翻不过去,弹出的是「无法连接后端」。', async () => {
    const req = page.waitForRequest((r) => r.url().includes('/system/post'));
    await m.button(page, '下一页', { ms: 300 });
    expect((await req).url()).toContain('page=2');
  });
  await m.say(page, '连不上、超时都归成 :network,api/call 从不抛异常,所以界面不会永远转圈。');
  await m.quiet(page, 1600);

  await m.healApi(page);
  await m.step(page, '恢复之后点一下上一页就取回来了:失败只留下一条提示,列表数据没被弄脏。', async () => {
    const res = m.expectRequest(page, 'page=1');
    await m.button(page, '上一页', { ms: 300 });
    const body = await (await res).json();
    expect(body.data.rows[0].post_id).toBe(1);
  });

  await m.code(page, [
    'api/call 的归类:',
    '  :business  HTTP 200 + code 非 200 → 显示后端 msg',
    '  :forbidden 403 → 提示 + 复位 loading',
    '  :unauthorized 401 → :auth/session-expired',
    '  :network   连不上 / 超时 → 统一文案',
  ], { label: 'ruoyi/api.cljd', voice: '还有一类是 401:令牌过期、会话被强退都算,效果层不交给页面,直接派发 session-expired 回登录页。' });
  await m.codeOff(page);
});
