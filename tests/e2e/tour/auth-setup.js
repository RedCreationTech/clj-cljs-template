// 录像前的全局登录：产出 target/tour/state.json，各分镜复用会话，避免每段重复录登录过程。
const fs = require('fs');
const path = require('path');
const { chromium, request } = require('@playwright/test');
const { login } = require('../auth-helper');

const STATE = path.join('target', 'tour', 'state.json');

/** 令牌里的 jti 就是会话 ID（sys_online.session_id），强退时要认出自己那一条。 */
function sessionIdOf(token) {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).jti;
}

/**
 * 清掉历史在线会话。每次 Playwright 登录都会在 sys_online 登记一条，空闲清理要 30 分钟才跑一轮，
 * 所以连着录几段之后，仪表盘第一帧会显示「在线用户 40」——和这场演示没有任何关系。
 * 自己的那条留到最后再退：先退掉它就会让后面的强退全部 401，剩下的会话反而清不干净。
 */
async function clearOnlineSessions(baseURL) {
  const api = await request.newContext({ baseURL });
  const res = await api.post('/api/auth/login', { data: { username: 'admin', password: 'admin123' } });
  if (!res.ok()) {
    await api.dispose();
    return;
  }
  const token = (await res.json()).data.token;
  const headers = { Authorization: `Bearer ${token}` };
  const list = await (await api.get('/api/system/online?page=1&size=200', { headers })).json();
  const mine = sessionIdOf(token);
  const rows = (list.data && list.data.rows) || [];
  const others = rows.map((r) => r['token-id']).filter((id) => id && id !== mine);
  for (const id of others.concat(mine)) {
    await api.delete(`/api/system/online/${id}`, { headers });
  }
  await api.dispose();
}

module.exports = async () => {
  const baseURL = process.env.BASE_URL || 'http://localhost:3000';
  const width = Number(process.env.TOUR_WIDTH || 1440);
  const height = Number(process.env.TOUR_HEIGHT || 900);

  await clearOnlineSessions(baseURL);

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL, viewport: { width, height } });
  const page = await context.newPage();
  await login(page);
  fs.mkdirSync(path.dirname(STATE), { recursive: true });
  await context.storageState({ path: STATE });
  await browser.close();
};
