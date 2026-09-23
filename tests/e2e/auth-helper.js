// 登录辅助函数
// 后端在 uuid 存在但 captcha 为空时跳过验证码校验，因此直接提交即可登录。

const LOGIN_URL = '/';

/**
 * 执行登录操作。
 * @param {import('playwright/test').Page} page
 * @param {string} [username]
 * @param {string} [password]
 */
async function login(page, username = 'admin', password = 'admin123') {
  await page.goto(LOGIN_URL);

  // 等待登录页渲染
  await page.getByPlaceholder('用户名').waitFor();

  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  // 验证码留空即可通过后端校验

  await page.getByRole('button', { name: /登\s*录/ }).click();

  // 等待进入主布局。不依赖侧边栏品牌文案(项目已从“若依管理系统”改名),
  // 改用与品牌无关的稳定信号: 登录表单消失 + 管理端侧边栏渲染出来。
  await page.getByPlaceholder('密码').waitFor({ state: 'hidden', timeout: 15000 });
  await page.locator('.ant-layout-sider').first().waitFor({ timeout: 15000 });
}

/**
 * 执行登出操作。
 * @param {import('playwright/test').Page} page
 */
async function logout(page) {
  // 点击头像下拉
  await page.locator('.ant-layout-header').getByText('管理员').click();
  await page.getByText('退出登录').click();
  await page.getByRole('button', { name: /登\s*录/ }).waitFor();
}

module.exports = { login, logout };
