// @ts-check
// 移动端(ClojureDart + Flutter)导览录像专用配置:与常规 E2E 分开,不把录屏拖进 CI 门禁。
// 用法:bb video:mobile(见 bb.edn),产出 target/mobile-tour/mobile.mp4。
//
// 画面是 1440x900 的「舞台页」(tests/e2e/mobile/stage.html),App 以真机尺寸
// 390x844 嵌在同域 iframe 里,所以 baseURL 指向托管 resources/public/mobile 的那个后端。
const { defineConfig } = require('@playwright/test');

const speed = Number(process.env.TOUR_SPEED || 1);
const width = Number(process.env.MOBILE_STAGE_WIDTH || 1440);
const height = Number(process.env.MOBILE_STAGE_HEIGHT || 900);

module.exports = defineConfig({
  testDir: './tests/e2e/mobile',

  fullyParallel: false,
  workers: 1,
  retries: 0,
  // 一段演示要等 Flutter 起画布 + 念台词,单段上限放宽到 6 分钟
  timeout: 6 * 60 * 1000,

  reporter: [['list'], ['json', { outputFile: 'target/mobile-tour/report.json' }]],
  outputDir: 'target/mobile-tour/results',

  use: {
    baseURL: process.env.MOBILE_BASE_URL || 'http://localhost:3210',
    viewport: { width, height },
    // 2 倍像素密度:Flutter 的 canvas 按 devicePixelRatio 出图,录出来字才不糊
    deviceScaleFactor: 2,
    video: { mode: 'on', size: { width, height } },
    screenshot: 'off',
    trace: 'off',
    actionTimeout: 20000,
    launchOptions: { slowMo: speed < 1 ? 0 : 120 },
  },

  projects: [{ name: 'mobile' }],
});
