// 分镜 04｜主题设置:一条事件翻一个布尔
const { test, expect } = require('@playwright/test');
const m = require('./mobile-helper');

// 登录页只有一个 AppBar 按钮(主题),列表页有两个(主题在左、退出在右)。
const X = { themeLogin: 366, themeList: 329, logout: 366, next: 271 };
const Y = { bar: 24, pager: 822 };

test('04｜主题设置:亮暗两套配色从同一个布尔派生', async ({ page }) => {
  await m.open(page);

  await m.chapter(page, {
    n: 4,
    title: '主题设置',
    subtitle: '一条事件翻一个布尔,配色与 ThemeData 都从它派生',
    points: [
      'theme.cljd 里两套配色,键完全一样:换主题不换字段',
      '订阅 :ui/palette / :ui/theme 把布尔变成颜色与 ThemeData',
      ' MaterialApp 的 theme 挂在根组件的 :watch 上,整屏一起换',
      '主题跟会话无关:登录前能换,退出后还留着',
    ],
  });

  const light = await m.tone(page);
  await m.step(page, '先从登录页开始:右上角这个月亮就是主题开关,点一下整屏换暗。', async () => {
    await m.tap(page, X.themeLogin, Y.bar, { ms: 400 });
    await m.wait(page, 900);
    expect(await m.tone(page)).toBeLessThan(light - 60);
  });
  await m.say(page, '画面没有一处写死的颜色:输入框、按钮、背景全跟着走,因为它们是同一份配色的键。');
  await m.quiet(page, 1400);

  await m.code(page, [
    'ruoyi/theme.cljd',
    '(def light {:primary … :danger … :danger-bg …',
    '            :text-main … :text-regular … :border … :bg …})',
    '(def dark  { … 同样七个键 … })',
    '(defn palette-of [dark?] (if dark? dark light))',
    '(defn theme-for  [dark?] (if dark? dark-data data))',
  ], { label: '配色', voice: '两套配色七个键一模一样,palette-of 和 theme-for 只是按那个布尔二选一。' });
  await m.codeOff(page);

  await m.step(page, '带着暗色登录:正文一换,列表直接是暗的。', async () => {
    const r = await m.signIn(page);
    expect((await r.posts.json()).code).toBe(200);
    expect(await m.tone(page)).toBeLessThan(120);
  });

  await m.step(page, '在暗色里翻到第 2 页:主题不影响取数,页码照旧。', async () => {
    const res = m.expectRequest(page, 'page=2');
    await m.tap(page, X.next, Y.pager, { ms: 300 });
    expect((await (await res).json()).data.rows[0].post_id).toBe(21);
    expect(await m.tone(page)).toBeLessThan(120);
  });
  await m.say(page, '换主题不发请求、不清列表:它只动 app-db 里的一个布尔,别的键没人碰。');
  await m.quiet(page, 1400);

  await m.step(page, '右上角再点一次回到亮色。', async () => {
    await m.tap(page, X.themeList, Y.bar, { ms: 400 });
    await m.wait(page, 900);
    expect(await m.tone(page)).toBeGreaterThan(150);
  });

  await m.step(page, '退出登录:令牌、身份、列表一次清干净,但主题不是会话的一部分,它留着。', async () => {
    const res = m.expectRequest(page, '/auth/logout');
    await m.tap(page, X.logout, Y.bar, { ms: 300 });
    expect((await res).status()).toBe(200);
    await m.wait(page, 1200);
    expect(await m.tone(page)).toBeGreaterThan(150);
  });

  await m.step(page, '在登录页再切一次:暗色的登录页,和开头那张形成对照。', async () => {
    await m.tap(page, X.themeLogin, Y.bar, { ms: 400 });
    await m.wait(page, 900);
    expect(await m.tone(page)).toBeLessThan(120);
  });
  await m.say(page, '整段代码里跟主题有关的只有三处:一个布尔、一套事件、一份配色表——没有第四处需要改。');
  await m.quiet(page, 1600);
});
