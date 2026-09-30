// 录像前的全局登录：产出 target/tour/state.json，各分镜复用会话，避免每段重复录登录过程。
const fs = require('fs');
const path = require('path');
const { chromium } = require('@playwright/test');
const { login } = require('../auth-helper');

const STATE = path.join('target', 'tour', 'state.json');

module.exports = async () => {
  const baseURL = process.env.BASE_URL || 'http://localhost:3000';
  const width = Number(process.env.TOUR_WIDTH || 1440);
  const height = Number(process.env.TOUR_HEIGHT || 900);

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL, viewport: { width, height } });
  const page = await context.newPage();
  await login(page);
  fs.mkdirSync(path.dirname(STATE), { recursive: true });
  await context.storageState({ path: STATE });
  await browser.close();
};
