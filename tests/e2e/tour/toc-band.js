// 导览录像的左侧章节目录（常驻显示）。
//
// 读取 target/tour/storyboard/NN.json（录制时由 tour-helper.js 落盘），用 Chromium 渲染出
// 每个分镜一张 300x900 的目录条图片到 target/tour/toc/NN.png；`bb video:tour` 的合成阶段
// 把应用画面右移 300px，再按当前分镜把对应图片贴到左边，于是目录全程可见且高亮在播的段落。
//
// 之所以在后期贴图而不是画在页面里：页面左侧是应用自己的权限菜单，演示时要点它、要看得见。

const fs = require('fs');
const path = require('path');
const { chromium } = require('@playwright/test');

const BOARD = path.join('target', 'tour', 'storyboard');
const OUT = path.join('target', 'tour', 'toc');
const WIDTH = Number(process.env.TOUR_TOC_WIDTH || 300);
const HEIGHT = Number(process.env.TOUR_HEIGHT || 900);

function chapters() {
  if (!fs.existsSync(BOARD)) throw new Error(`没有分镜目录 ${BOARD}，先跑 bb video:tour`);
  return fs
    .readdirSync(BOARD)
    .filter((f) => /^\d+\.json$/.test(f))
    .map((f) => JSON.parse(fs.readFileSync(path.join(BOARD, f), 'utf8')))
    .sort((a, b) => a.n - b.n);
}

function item(ch, active) {
  const no = String(ch.n).padStart(2, '0');
  const sub = active && ch.subtitle ? `<div class="sub">${ch.subtitle}</div>` : '';
  return (
    `<li class="${active ? 'active' : ''}"><div class="row">` +
    `<span class="no">${no}</span><span class="ttl">${ch.title}</span></div>${sub}</li>`
  );
}

function html(list, activeNo) {
  return `<!doctype html><meta charset="utf-8"><style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { width: ${WIDTH}px; height: ${HEIGHT}px; overflow: hidden; color: #fff;
           font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif;
           background: linear-gradient(170deg, #0d1424 0%, #111a2c 60%, #16213a 100%); }
    .wrap { padding: 34px 18px 0; }
    h1 { font-size: 19px; letter-spacing: 3px; color: #eaf2ff; }
    .meta { font-size: 12px; color: #6f819c; letter-spacing: .5px; margin: 8px 0 20px; line-height: 1.7; }
    ol { list-style: none; }
    li { padding: 9px 10px; border-radius: 8px; font-size: 14.5px; line-height: 1.45;
         color: #93a3ba; border-left: 3px solid transparent; }
    li .row { display: flex; align-items: baseline; gap: 9px; }
    li .no { font-size: 12px; color: #5c6f8a; font-variant-numeric: tabular-nums; }
    li .ttl { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    li.active { background: rgba(64, 158, 255, .16); border-left-color: #409eff; color: #fff; }
    li.active .no { color: #7fb2ff; }
    li.active .ttl { white-space: normal; font-weight: 600; }
    li.active .sub { font-size: 12px; color: #9dc0f0; margin-top: 5px; }
    .foot { position: absolute; left: 18px; right: 18px; bottom: 26px; font-size: 11.5px;
            line-height: 1.6; color: #56687f; letter-spacing: .5px; }
  </style>
  <div class="wrap">
    <h1>功能导览</h1>
    <div class="meta">共 ${list.length} 段<br>Clojure / ClojureScript 全栈模板</div>
    <ol>${list.map((c) => item(c, c.n === activeNo)).join('')}</ol>
  </div>
  <div class="foot">Kit · Integrant · Reitit · HugSQL · shadow-cljs · Reagent · re-frame · Ant Design 6</div>`;
}

(async () => {
  const list = chapters();
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: WIDTH, height: HEIGHT } });
  for (const ch of list) {
    await page.setContent(html(list, ch.n), { waitUntil: 'load' });
    const file = path.join(OUT, `${String(ch.n).padStart(2, '0')}.png`);
    await page.screenshot({ path: file, clip: { x: 0, y: 0, width: WIDTH, height: HEIGHT } });
    console.log(`目录条 ${file}`);
  }
  await browser.close();
})();
