// @ts-check
// 功能导览录像专用配置：与 playwright.config.js 分开，避免把视频录制拖进常规 CI 门禁。
// 用法：bb video:tour（见 bb.edn），产出 target/tour/tour.mp4。
const { defineConfig, devices } = require('@playwright/test');

// SPEED=0.2 之类的小数值用于调试脚本时压缩等待，正式录制用默认 1。
const speed = Number(process.env.TOUR_SPEED || 1);
const width = Number(process.env.TOUR_WIDTH || 1440);
const height = Number(process.env.TOUR_HEIGHT || 900);

module.exports = defineConfig({
  testDir: './tests/e2e/tour',

  // 登录一次并保存会话，后续分镜直接从已登录状态开始录制。
  globalSetup: require.resolve('./tests/e2e/tour/auth-setup.js'),

  fullyParallel: false,
  workers: 1,
  retries: 0,
  // 一段演示可能几十步，单段上限放宽到 5 分钟
  timeout: 5 * 60 * 1000,

  reporter: [['list'], ['json', { outputFile: 'target/tour/report.json' }]],

  outputDir: 'target/tour/results',

  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:3000',
    viewport: { width, height },
    video: { mode: 'on', size: { width, height } },
    screenshot: 'off',
    trace: 'off',
    // 单个动作最多等 20 秒，选择器写错时快速失败而不是把整段录满超时
    actionTimeout: 20000,
    launchOptions: { slowMo: speed < 1 ? 0 : 120 },
  },

  projects: [
    // devices 的 use 会覆盖上面的 viewport，所以必须在展开之后再写一次，
    // 否则页面只有 1280x720，录出来的画面缩在左上角。
    {
      name: 'tour',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width, height },
        storageState: 'target/tour/state.json',
      },
    },
  ],
});
