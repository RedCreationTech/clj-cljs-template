// 移动端导览录像的「分镜 + 台词」引擎(配套 tests/e2e/mobile/stage.html)。
//
// 与网页端的 tour-helper 同一套思路:台词画在页面上,由 Playwright 录进视频,
// 不做后期字幕压制;每段结束把 {标题, 要点, 台词与时间点} 落到
// target/mobile-tour/storyboard/NN.json,供 bb video:mobile 汇总章节与分镜脚本。
//
// Flutter 内容通过语义树定位;假光标按目标在舞台上的实际边界摆放,不依赖布局坐标。

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { installFonts } = require('../mobile-fonts');
const { expect } = require('@playwright/test');
const semantics = require('../mobile-semantics');

const SPEED = Number(process.env.TOUR_SPEED || 1);
const OUT_DIR = process.env.TOUR_OUT_DIR || path.join('target', 'mobile-tour', 'storyboard');
const TOTAL = Number(process.env.TOUR_CHAPTERS || 4);
const APP_W = Number(process.env.MOBILE_APP_WIDTH || 390);
const APP_H = Number(process.env.MOBILE_APP_HEIGHT || 844);
const BASE = process.env.MOBILE_BASE_URL || "http://localhost:3210";
const STAGE = BASE + (process.env.MOBILE_STAGE_PATH || "/mobile/stage.html");

let current = null;

const scaled = (ms) => Math.max(40, Math.round(ms * SPEED));

async function wait(page, ms) {
  await page.waitForTimeout(scaled(ms));
}

function dump() {
  if (!current) return;
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const file = `${String(current.n).padStart(2, '0')}.json`;
  fs.writeFileSync(path.join(OUT_DIR, file), JSON.stringify(current, null, 2));
}

async function call(page, fn, arg) {
  return page.evaluate(({ fn, arg }) => window.__stage[fn](arg), { fn, arg });
}

function app(page) {
  return page.frameLocator('#app');
}

async function appReady(page) {
  const surface = app(page);
  await surface.locator('flt-semantics-placeholder, flt-semantics').first()
    .waitFor({ state: 'attached', timeout: 60000 });
  await semantics.enableSemantics(surface);
  await expect(surface.getByRole('textbox', { name: '用户名', exact: true })).toBeVisible({ timeout: 60000 });
}

/** 打开舞台页,启用 Flutter 语义树,等待真正的登录输入框就绪。 */
async function open(page, { boot = 4200 } = {}) {
  await installFonts(page);
  await page.goto(STAGE);
  await page.waitForFunction(() => window.__stage && window.__stage.ready(), null, { timeout: 15000 });
  await wait(page, boot);
  await appReady(page);
  await call(page, 'say', '');
}

/** 手机框在舞台上的实际渲染矩形,供截图裁剪使用。 */
async function rect(page) {
  const r = await call(page, 'rect');
  return { ...r, k: r.w / APP_W };
}

/**
 * 分镜卡:标题页出现的同时念一句「第 N 段:…」,不然卡片那三秒加上下一句要等代码打完,
 * 章节边界会出现十几句连着的空白(实测 16 秒没人说话)。
 */
async function chapter(page, { n, title, subtitle, points, hold = 3200 }) {
  current = { n, title, subtitle, points, lines: [], startedAt: Date.now() };
  dump();
  await call(page, 'chapter', n);
  await call(page, 'notes', { title, subtitle, points });
  await call(page, 'chip', `分镜 ${String(n).padStart(2, '0')} / ${TOTAL} · ${title}`);
  await call(page, 'card', await page.evaluate(
    (a) => window.__stage.cardHtml(a),
    { n, title, subtitle, points },
  ));
  const intro = `第 ${n} 段:${title}。${subtitle}。`;
  const duration = Math.max(hold, speakable(intro));
  await call(page, 'say', intro);
  if (current) { current.lines.push({ text: intro, at: 0, for: duration }); dump(); }
  await wait(page, duration);
  await call(page, 'card', '');
  await call(page, 'say', '');
}

/** 一句台词(不带动作);没给时长就按字数估,保证旁白念得完。 */
async function say(page, text, ms) {
  const duration = ms || speakable(text);
  await call(page, 'say', text);
  if (current) { current.lines.push({ text, at: Date.now() - current.startedAt, for: duration }); dump(); }
  await wait(page, duration);
}

/**
 * 一句台词该占多少毫秒。
 * 实测 MiMo「语速偏快」在这批台词上是 100~180 毫秒一个字(标识符多的句子念得快,纯中文的念得慢),
 * 按最慢的 160 毫秒一个字 + 600 毫秒起落留时间。留少了旁白装不进字幕停留的窗口,
 * 合成时会被整片顶到最快速度(实测上限 1.3 倍,念得喘不过气)。
 */
function speakable(text) {
  return Math.max(1800, Math.round(String(text).length * 160) + 600);
}

/**
 * 一步操作:先给台词,再执行动作,最后留余韵让观众看清结果。
 * 余韵至少要撑到这句念完:动作常常两秒就完了,而旁白要四五秒,
 * 下一条字幕抢在语音结束之前出现,合成时这一句就会被顶到最快速度(念得喘不过气)。
 */
async function step(page, text, action, { lead = 400, after = 1200 } = {}) {
  const budget = speakable(text);
  const started = Date.now();
  await call(page, 'say', text);
  if (current) { current.lines.push({ text, at: started - current.startedAt, for: budget }); dump(); }
  await wait(page, lead);
  if (action) await action();
  await wait(page, Math.max(after, budget - (Date.now() - started)));
}

/** 清掉字幕,让观众专心看画面(切主题、看列表刷新时用)。 */
async function quiet(page, ms = 800) {
  await call(page, 'say', '');
  await wait(page, ms);
}

/** 语义目标的 boundingBox 已是舞台坐标,可直接驱动假光标。 */
async function cursorAt(page, target) {
  await expect(target).toBeVisible();
  await target.scrollIntoViewIfNeeded();
  const box = await target.boundingBox();
  expect(box).not.toBeNull();
  await page.evaluate(({ x, y }) => { window.__stage.moveTo(x, y); window.__stage.press(); },
    { x: box.x + box.width / 2, y: box.y + box.height / 2 });
  await wait(page, 160);
}

async function click(page, target, { ms = 900 } = {}) {
  await expect(target).toBeEnabled();
  await cursorAt(page, target);
  await target.click();
  await wait(page, ms);
}

async function button(page, name, options = {}) {
  return click(page, app(page).getByRole('button', { name, exact: true }), options);
}

async function enter(page, name, value) {
  await cursorAt(page, app(page).getByRole('textbox', { name, exact: true }));
  await semantics.enter(app(page), name, value);
  await wait(page, 300);
}

async function submit(page, options = { ms: 200 }) {
  return button(page, '登录', options);
}

async function toggleTheme(page, options = {}) {
  return click(page, app(page).getByRole('button', { name: /切换为(?:深|浅)色主题/ }), options);
}

/** 登录动作与常规移动端回归共用语义输入和焦点断言。 */
async function login(page, { user = 'admin', password = 'admin123', ms = 900 } = {}) {
  await semantics.enableSemantics(app(page));
  await enter(page, '用户名', user);
  await enter(page, '密码', password);
  await wait(page, ms);
  await submit(page);
}

/**
 * 登录并等身份、列表都取回来。
 * 监听必须在点提交**之前**挂好:登录响应回来之后几百毫秒,后两条请求就发完了,
 * 事后再 waitForResponse 只会等到超时(录屏里画面明明已经刷出来了)。
 */
async function signIn(page, opts = {}) {
  const onLogin = expectRequest(page, '/auth/login');
  const onInfo = expectRequest(page, '/auth/getInfo');
  const onPosts = expectRequest(page, '/system/post');
  await login(page, opts);
  return { login: await onLogin, info: await onInfo, posts: await onPosts };
}

/**
 * 右侧代码卡:逐行打出命令/输出,**高亮** 变黄。rows 是字符串数组。
 * 卡上的每一行都标成 code —— 旁白只念 voice 那句人话,不把 Clojure/JSON 逐字念出来。
 */
async function code(page, rows, { label = 'clojuredart', lead = 500, gap = 700, hold = 1600, voice = null } = {}) {
  await call(page, 'say', '');
  const lines = rows.map((r) => (typeof r === 'string' ? { html: windowless(r), text: r } : r));
  const typed = lead + gap * Math.max(0, lines.length - 1);
  const started = Date.now();
  // 台词与逐行打字同时开始:旁白边讲代码边长出来,不用等打完再开口(那段空白会被观众感觉到)
  const duration = Math.max(hold, voice ? Math.max(typed, speakable(voice)) : typed);
  if (voice) {
    await call(page, 'say', voice);
    if (current) { current.lines.push({ text: voice, at: started - current.startedAt, for: duration }); dump(); }
  }
  for (let i = 0; i < lines.length; i += 1) {
    const html = lines.slice(0, i + 1).map((l) => l.html).join('\n');
    await page.evaluate(({ html: h, label: l }) => window.__stage.code(h, l), { html, label });
    if (current && lines[i].text) {
      current.lines.push({ text: lines[i].text, at: Date.now() - current.startedAt, for: gap * 3, code: true });
      dump();
    }
    // eslint-disable-next-line no-await-in-loop
    await wait(page, i === 0 ? lead : gap);
  }
  await wait(page, Math.max(0, duration - (Date.now() - started)));
}

function windowless(s) {
  if (s.startsWith('$ ')) return `<b>$ ${esc(s.slice(2))}</b>`;
  return `<u>${esc(s)}</u>`;
}

const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));

/** 收起代码卡,回到只有要点的画面。 */
async function codeOff(page, ms = 400) {
  await call(page, 'code', '');
  await wait(page, ms);
}

/** 重载 App(回到登录页):讲「令牌只存一份」「换主题不丢状态」这类对比时用。 */
/**
 * 只刷 App 那层 iframe。整页 reload 会把舞台自己的状态一起抹掉:
 * 实测刷完目录高亮跳回 01、右上角分镜号变成「准备中」、右侧要点整块消失,
 * 观众以为换了分镜,而我们要演示的只是「App 的状态只在内存里」。
 */
async function reload(page, { boot = 4200 } = {}) {
  await page.evaluate(() => document.getElementById('app').contentWindow.location.reload());
  await wait(page, boot);
  await appReady(page);
}

/** 等某个接口真的发生(录屏里看不到 DOM,断言就落在网络层)。 */
function expectRequest(page, urlPart, { timeout = 20000 } = {}) {
  return page.waitForResponse((res) => res.url().includes(urlPart), { timeout });
}

/** 数某个路径被请求了几次:证明「切主题不会重新取数」这类断言。 */
function counter(page, urlPart) {
  const state = { n: 0 };
  page.on('request', (req) => { if (req.url().includes(urlPart)) state.n += 1; });
  return state;
}

/**
 * 手机屏幕的平均亮度(0-255)。
 * 语义树验证操作对象,屏幕像素验证实际主题:截下屏幕 → 缩到 1x1 → 读那三个字节。
 * 用 ffmpeg 而不是浏览器内 canvas,是因为 CanvasKit 的画布是 WebGL 背书的,
 * drawImage 抄过去常常是一张黑图,亮暗就分不出来了。
 */
async function tone(page) {
  const r = await rect(page);
  const png = await page.screenshot({
    clip: { x: r.x + 2, y: r.y + 2, width: r.w - 4, height: r.h - 4 },
  });
  const args = ['-hide_banner', '-loglevel', 'error', '-f', 'image2pipe', '-vcodec', 'png',
    '-i', 'pipe:0', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'];
  const raw = execFileSync('ffmpeg', args, { input: png, maxBuffer: 1 << 20 });
  return Math.round(0.2126 * raw[0] + 0.7152 * raw[1] + 0.0722 * raw[2]);
}

/** 让之后打给接口的请求直接失败,用来演示「连不上后端」这一类失败怎么归类。 */
async function breakApi(page, pattern = '**/api/**') {
  await page.route(pattern, (route) => route.abort());
}

async function healApi(page, pattern = '**/api/**') {
  await page.unroute(pattern);
}

module.exports = {
  SPEED, scaled, wait, open, chapter, say, step, quiet,
  app, click, button, enter, submit, toggleTheme, login, signIn, code, codeOff, reload,
  expectRequest, counter, tone, breakApi, healApi, rect, dump, APP_W, APP_H,
};
