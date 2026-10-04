// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * @see https://playwright.dev/docs/test-configuration
 */
module.exports = defineConfig({
  testDir: './tests/e2e',

  /* 网页/移动导览录像由各自的 tour/mobile 配置运行，
     常规门禁只收业务验收和根目录 mobile-shell.spec.js。 */
  testIgnore: '**/{tour,mobile}/**',

  /* Run tests in files in serial because they share the local SQLite DB */
  fullyParallel: false,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Opt out of parallel tests on CI */
  workers: 1,

  /* Reporter to use */
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'playwright-report/report.json' }],
  ],

  /* Shared settings for all the projects below */
  use: {
    /* Base URL to use in actions like `await page.goto('/')` */
    baseURL: process.env.BASE_URL || 'http://localhost:3000',

    /* Collect trace when retrying the failed test */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'only-on-failure',

    /* Record video on retry */
    video: 'on-first-retry',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // 使用 Playwright 自带的 chromium(通过 `npx playwright install chromium` 安装)。
        // 不要再写死某个开发者机器上的 executablePath,否则会因路径/架构不符而启动失败。
      },
    },
  ],

  /* Run local dev server before starting the tests */
  // webServer: {
  //   command: 'clojure -M:dev -m com.ruoyi.core',
  //   url: 'http://localhost:3000/api/health',
  //   reuseExistingServer: true,
  //   timeout: 120 * 1000,
  // },
});
