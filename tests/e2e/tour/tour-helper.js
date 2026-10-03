// 功能导览录像的「分镜 + 台词」引擎。
//
// 台词直接画在浏览器页面上（字幕条 + 分镜卡），所以会被 Playwright 的视频一起录进去，
// 不需要事后用 ffmpeg 叠字幕（中文渲染交给 Chromium，字体/换行/暗色背景都更可控）。
//
// 同时把每段的分镜与台词按时间落到 target/tour/storyboard/NN.json，
// 供 `bb video:tour` 汇总成 storyboard.md 与 MP4 章节标记。

const fs = require('fs');
const path = require('path');

const SPEED = Number(process.env.TOUR_SPEED || 1);
const OUT_DIR = process.env.TOUR_OUT_DIR || path.join('target', 'tour', 'storyboard');
const TOTAL = Number(process.env.TOUR_CHAPTERS || 14);

let current = null; // 正在录制的分镜，用于累积台词

const scaled = (ms) => Math.max(40, Math.round(ms * SPEED));

async function wait(page, ms) {
  await page.waitForTimeout(scaled(ms));
}

async function ensureOverlay(page) {
  await page.evaluate(
    ({ total }) => {
      if (document.getElementById('tour-root')) return;
      const style = document.createElement('style');
      style.id = 'tour-style';
      style.textContent = `
        #tour-root { position: fixed; inset: 0; z-index: 2147483000; pointer-events: none;
                     font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif; }
        #tour-chip { position: absolute; top: 66px; left: 18px; padding: 6px 14px; border-radius: 999px;
                     background: rgba(17,20,24,.82); color: #fff; font-size: 14px; letter-spacing: .5px;
                     box-shadow: 0 2px 10px rgba(0,0,0,.25); }
        #tour-rec { position: absolute; top: 66px; right: 18px; display: flex; align-items: center; gap: 7px;
                    padding: 6px 12px; border-radius: 999px; background: rgba(17,20,24,.82);
                    color: #fff; font-size: 13px; }
        #tour-rec i { width: 9px; height: 9px; border-radius: 50%; background: #f5222d; display: block;
                      animation: tour-blink 1.2s steps(2, start) infinite; }
        @keyframes tour-blink { 50% { opacity: .15; } }
        #tour-caption { position: absolute; left: 50%; bottom: 28px; transform: translateX(-50%);
                        max-width: 78%; padding: 13px 26px; border-radius: 12px;
                        background: rgba(17,20,24,.88); color: #fff; font-size: 21px; line-height: 1.5;
                        text-align: center; box-shadow: 0 6px 24px rgba(0,0,0,.35);
                        white-space: pre-wrap; display: none; }
        #tour-card { position: absolute; inset: 0; display: none; flex-direction: column;
                     justify-content: center; align-items: center;
                     background: linear-gradient(140deg, #0f1626 0%, #172033 55%, #1d2b45 100%); color: #fff; }
        #tour-card .idx { font-size: 20px; letter-spacing: 6px; color: #7fb2ff; }
        #tour-card .ttl { font-size: 54px; font-weight: 600; margin: 18px 0 8px; text-align: center; }
        #tour-card .sub { font-size: 22px; color: #b9c6da; margin-bottom: 34px; text-align: center; }
        #tour-card ul { list-style: none; padding: 0; margin: 0; font-size: 21px; color: #e6efff; }
        #tour-card li { margin: 10px 0; }
        #tour-card li::before { content: "·"; color: #409eff; margin-right: 12px; font-weight: 700; }
        #tour-card .foot { position: absolute; bottom: 40px; font-size: 16px; color: #7d8ca3; letter-spacing: 2px; }
        #tour-term { position: absolute; inset: 0; display: none; flex-direction: column;
                     justify-content: center; align-items: center; background: rgba(8,11,18,.97); }
        #tour-term .win { width: 74%; max-width: 980px; border-radius: 12px; overflow: hidden;
                          background: #0e1420; border: 1px solid #223050;
                          box-shadow: 0 24px 60px rgba(0,0,0,.55); }
        #tour-term .bar { display: flex; align-items: center; gap: 8px; padding: 11px 16px;
                          background: #162032; border-bottom: 1px solid #223050; }
        #tour-term .bar i { width: 11px; height: 11px; border-radius: 50%; display: block; }
        #tour-term .bar .r { background: #ff5f57; }
        #tour-term .bar .y { background: #febc2e; }
        #tour-term .bar .g { background: #28c840; }
        #tour-term .bar span { color: #8b9ab0; font-size: 13px; margin-left: 10px;
                               font-family: ui-monospace, Menlo, Consolas, monospace; }
        #tour-term pre { margin: 0; padding: 20px 24px 26px; min-height: 320px; max-height: 62vh;
                         overflow: auto;
                         font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 17px;
                         line-height: 1.7; color: #c9d6e8; white-space: pre-wrap; }
        #tour-term b { color: #7ee787; font-weight: 600; }
        #tour-term u { color: #7c8ea8; text-decoration: none; }
        #tour-term em { color: #ffd166; font-style: normal; font-weight: 600; }
        /* 假鼠标：Playwright 录的是页面本身，系统光标在浏览器进程外，不进画面。
           所以把真实 mousemove 坐标画成一个箭头叠在页面上，一起被录进视频。 */
        #tour-cursor { position: absolute; left: 0; top: 0; display: none;
                       transition: transform .13s cubic-bezier(.22,.7,.3,1); will-change: transform; }
        #tour-cursor svg { display: block; filter: drop-shadow(0 1px 2px rgba(0,0,0,.6)); }
        #tour-cursor .ring { position: absolute; left: -16px; top: -16px; width: 38px; height: 38px;
                             border-radius: 50%; border: 2px solid rgba(255,255,255,.95);
                             background: rgba(64,158,255,.32); opacity: 0; }
        #tour-cursor.press .ring { animation: tour-click .5s ease-out; }
        @keyframes tour-click { 0% { opacity: .95; transform: scale(.3); }
                                100% { opacity: 0; transform: scale(1.3); } }`;
      const root = document.createElement('div');
      root.id = 'tour-root';
      root.innerHTML =
        '<div id="tour-card"></div>' +
        '<div id="tour-term"><div class="win">' +
        '<div class="bar"><i class="r"></i><i class="y"></i><i class="g"></i><span></span></div>' +
        '<pre></pre></div></div>' +
        '<div id="tour-chip"></div>' +
        '<div id="tour-rec"><i></i><span>REC</span></div>' +
        '<div id="tour-caption"></div>' +
        '<div id="tour-cursor"><span class="ring"></span>' +
        '<svg width="22" height="29" viewBox="0 0 22 29">' +
        '<path d="M2 1 L2 22 L7.6 17.2 L11 25.4 L15 23.7 L11.6 15.7 L19.6 15.2 Z" ' +
        'fill="#fff" stroke="#111418" stroke-width="1.7" stroke-linejoin="round"/></svg></div>';
      const mount = () => {
        if (!document.getElementById('tour-style')) document.head.appendChild(style);
        document.body.appendChild(root);
      };
      if (document.body) mount();
      else document.addEventListener('DOMContentLoaded', mount);
      // 指针跟着真实鼠标事件走：Playwright 的 click 会先往页面派 mousemove / mousedown，
      // 所以不用额外驱动鼠标，画面里的箭头就落在真正被点的那个位置上。
      const cursor = root.querySelector('#tour-cursor');
      const moveTo = (x, y) => {
        cursor.style.display = 'block';
        cursor.style.transform = `translate(${x}px, ${y}px)`;
      };
      document.addEventListener('mousemove', (e) => moveTo(e.clientX, e.clientY), true);
      // fill() 只 focus 不移动鼠标，所以「用键盘填的输入框」拿到焦点时把指针挪过去，
      // 否则登录整段都在打字却看不到手在哪。两种情况不挪：
      // 按钮 / 菜单项 / 下拉只能靠点，mousedown 已经落在真正的点击点上；
      // 以及焦点就来自刚才那一下点击（同一个元素）——按时间间隔判断会被 slowMo 打乱，比对象身份不可靠。
      let clickTarget = null;
      const isTypable = (el) =>
        el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable === true;
      document.addEventListener('focusin', (e) => {
        const el = e.target;
        if (!el || el === document.body || el === clickTarget) return;
        if (!isTypable(el)) return;
        const r = el.getBoundingClientRect();
        if (!r.width || !r.height || r.width > window.innerWidth * 0.8) return;
        moveTo(r.left + Math.min(46, r.width / 2), r.top + r.height / 2);
      }, true);
      document.addEventListener('mousedown', (e) => {
        clickTarget = e.target;
        moveTo(e.clientX, e.clientY);
        cursor.classList.remove('press');
        void cursor.offsetWidth; // 重启动画：连点两次要看到两圈涟漪
        cursor.classList.add('press');
      }, true);
      document.addEventListener('mouseleave', () => {
        cursor.style.display = 'none';
      }, true);
      window.__tour = {
        set(text) {
          const el = document.getElementById('tour-caption');
          el.textContent = text;
          el.style.display = text ? 'block' : 'none';
        },
        chip(text) {
          document.getElementById('tour-chip').textContent = text;
        },
        card(html) {
          const el = document.getElementById('tour-card');
          el.innerHTML = html || '';
          el.style.display = html ? 'flex' : 'none';
        },
        term(html, label) {
          const el = document.getElementById('tour-term');
          if (html) {
            el.querySelector('.bar span').textContent = label || '';
            const pre = el.querySelector('pre');
            pre.innerHTML = html;
            pre.scrollTop = pre.scrollHeight;
            el.style.display = 'flex';
          } else {
            el.style.display = 'none';
          }
        },
      };
    },
    { total: TOTAL }
  );
}

const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));

function dump() {
  if (!current) return;
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(path.join(OUT_DIR, `${String(current.n).padStart(2, '0')}.json`), JSON.stringify(current, null, 2));
}

/** 开一段：整屏分镜卡（标题 + 本段要点），随后角标常驻显示分镜号。 */
async function chapter(page, { n, title, subtitle, points, hold = 3200 }) {
  current = { n, title, subtitle, points, lines: [], startedAt: Date.now() };
  dump();
  await ensureOverlay(page);
  await page.evaluate(
    (html) => window.__tour.card(html),
    `<div class="idx">分镜 ${String(n).padStart(2, '0')} / ${TOTAL}</div>` +
      `<div class="ttl">${esc(title)}</div>` +
      (subtitle ? `<div class="sub">${esc(subtitle)}</div>` : '') +
      `<ul>${points.map((p) => `<li>${esc(p)}</li>`).join('')}</ul>` +
      `<div class="foot">CLOJURE / CLOJURESCRIPT 全栈管理后台模板</div>`
  );
  await wait(page, hold);
  await page.evaluate(() => window.__tour.card(''));
  await page.evaluate(
    (info) => window.__tour.chip(`分镜 ${String(info.n).padStart(2, '0')} / ${info.total} · ${info.title}`),
    { n, total: TOTAL, title }
  );
}

/** 一句台词（不带动作）。 */
async function say(page, text, ms) {
  const duration = ms || Math.max(1500, text.length * 105);
  await ensureOverlay(page);
  await page.evaluate((t) => window.__tour.set(t), text);
  if (current) current.lines.push({ text, at: Date.now() - current.startedAt, for: duration });
  dump();
  await wait(page, duration);
}

/** 一步操作：先给台词，再执行动作，最后留余韵让观众看清结果。 */
async function step(page, text, action, { lead = 350, after = 1100 } = {}) {
  await ensureOverlay(page);
  await page.evaluate((t) => window.__tour.set(t), text);
  if (current) current.lines.push({ text, at: Date.now() - current.startedAt, for: lead + after });
  dump();
  await wait(page, lead);
  if (action) await action();
  await wait(page, after);
}

/** 连续台词，用于纯展示型段落。 */
async function voiceover(page, items) {
  for (const [text, ms] of items) await say(page, text, ms);
}

/** 终端里的一行：转义后把 **高亮** 变成黄色。 */
const rich = (s) => esc(s).replace(/\*\*([^*]+)\*\*/g, '<em>$1</em>');

/**
 * 终端卡：整屏假终端，逐行打出命令与输出（工程链一段用；命令在录制前真跑过，这里只回放结果）。
 * rows 形如 [["bb ci", "…", "**检查全部通过**"], ["bb uberjar", "…"]]，第一列是命令，其余是输出行，
 * 多组之间自动空一行。
 */
async function terminal(page, { label = 'babashka', rows = [], lead = 600, gap = 700, hold = 2600 } = {}) {
  await ensureOverlay(page);
  await page.evaluate(() => window.__tour.set(''));
  const lines = [];
  rows.forEach(([cmd, ...outs], i) => {
    if (i) lines.push({ html: '', text: null });
    lines.push({ html: `<b>$ ${rich(cmd)}</b>`, text: `$ ${cmd}` });
    outs.forEach((o) => lines.push({ html: `<u>${rich(o)}</u>`, text: null }));
  });
  for (let i = 0; i < lines.length; i += 1) {
    const body = lines.slice(0, i + 1).map((l) => l.html).join('\n');
    // eslint-disable-next-line no-await-in-loop
    await page.evaluate(({ body: html, label: l }) => window.__tour.term(html, l), { body, label });
    if (lines[i].text && current) {
      current.lines.push({ text: lines[i].text, at: Date.now() - current.startedAt, for: gap * 3 });
      dump();
    }
    // eslint-disable-next-line no-await-in-loop
    await wait(page, i === 0 ? lead : gap);
  }
  await wait(page, hold);
  await page.evaluate(() => window.__tour.term(''));
}

/** 清掉字幕，便于看清页面本身（例如切主题）。 */
async function quiet(page, ms = 600) {
  await ensureOverlay(page);
  await page.evaluate(() => window.__tour.set(''));
  await wait(page, ms);
}

/** 从已保存的会话直接进入某个页面（globalSetup 已登录，登录过程本身只在安全分镜里演示）。 */
async function open(page, url = '/') {
  await page.goto(url);
  await ensureOverlay(page);
  await settle(page, 400);
}

/** 清掉令牌与 Cookie，回到未登录状态（讲登录与安全时用）。 */
async function signOut(page) {
  await page.goto('/'); // 先落在应用域名：about:blank 上读不到 localStorage
  await page.context().clearCookies();
  await page.evaluate(() => window.localStorage.clear());
  await page.goto('/');
  await ensureOverlay(page);
  await settle(page, 400);
}

/** 点开某个表单字段里的下拉 / 树选择器（antd 6 的 placeholder 是遮罩层，点击要落在 combobox 上）。 */
async function openFieldSelect(page, label) {
  const item = page.locator('.ant-form-item').filter({ hasText: label }).first();
  await item.locator('[role=combobox]').first().click();
  await wait(page, 300);
}

/** 在当前打开的下拉里选一项；tree=true 时按树节点选。 */
async function pickOption(page, text, { tree = false } = {}) {
  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last();
  const cls = tree ? '.ant-select-tree-node-content-wrapper' : '.ant-select-item-option';
  await dropdown.locator(cls, { hasText: text }).first().click();
  await wait(page, 200);
}

/** 平滑向下平移整页，配合台词展示长页面。 */
async function pan(page, deltaY, ms = 1100) {
  await page.evaluate((d) => {
    const top = window.scrollY;
    const steps = 14;
    for (let i = 1; i <= steps; i += 1) {
      const ease = i / steps;
      window.setTimeout(() => window.scrollTo(0, top + d * ease), i * 24);
    }
  }, deltaY);
  await wait(page, ms);
}

/** 把某个元素滚到视口中间，再讲它的台词。 */
async function focusOn(page, target, ms = 700) {
  await target.scrollIntoViewIfNeeded().catch(() => {});
  await wait(page, ms);
}

/** 等转圈消失 + 一帧渲染余量。 */
async function settle(page, ms = 700) {
  await page.locator('.ant-spin-spinning').waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {});
  await wait(page, ms);
}

/** 悬停打开 antd Dropdown（默认 trigger 是 hover），再点其中的菜单项。 */
async function dropdownItem(page, trigger, item) {
  await trigger.hover();
  await wait(page, 400);
  await page.locator('.ant-dropdown:not(.ant-dropdown-hidden)')
    .getByText(item, { exact: true }).first().click();
}

async function isGroupOpen(page, group) {
  const title = page.locator('.ant-menu-submenu-title', { hasText: group }).first();
  return (await title.getAttribute('aria-expanded').catch(() => null)) === 'true';
}

/** 展开侧边栏目录并点进子菜单（比 page.goto 更适合录制：SPA 内跳转，字幕不丢）。 */
async function gotoMenu(page, group, item) {
  if (group && !(await isGroupOpen(page, group))) {
    await page.locator('.ant-menu-submenu-title', { hasText: group }).first().click();
    await wait(page, 450);
  }
  await page.locator('.ant-menu-item', { hasText: item }).first().click();
  await settle(page);
}

/** 用当前登录态直接调后端接口（录屏里不出现，用于准备/清理数据）。 */
async function api(page, method, url, data) {
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  const res = await page.request.fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: data === undefined ? undefined : JSON.stringify(data),
  });
  return res.json().catch(() => null);
}

/** 重新加载页面并恢复字幕层（录屏里用于「刷新看后端状态」）。 */
async function reload(page, ms = 600) {
  await page.reload();
  await ensureOverlay(page);
  await settle(page, ms);
}

const login = require('../auth-helper').login;const logout = require('../auth-helper').logout;

module.exports = {
  SPEED,
  scaled,
  wait,
  chapter,
  say,
  step,
  voiceover,
  terminal,
  quiet,
  open,
  reload,
  signOut,
  openFieldSelect,
  dropdownItem,
  pickOption,
  pan,
  focusOn,
  settle,
  gotoMenu,
  isGroupOpen,
  api,
  login,
  logout,
};
