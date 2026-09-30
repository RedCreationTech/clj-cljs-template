// 分镜 03 用户管理 · 查询与表格
// 分镜 04 用户管理 · 新增、详情、重置密码与删除
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

const SUFFIX = String(Date.now()).slice(-6);
const TOUR_USER = `tour${SUFFIX}`;
const TOUR_NICK = `导览用户${SUFFIX}`;
const SEED_PREFIX = 'tourdemo';

const rowOf = (page, name) => page.locator('.ant-table-tbody tr.ant-table-row', { hasText: name }).first();
const circle = (page, icon) => page.locator(`button:has(.anticon-${icon})`);

/** 用接口造一批演示用户，让列表、分页、状态开关在镜头里有内容；本段结束会清掉。 */
async function seedUsers(page, count = 12) {
  for (let i = 1; i <= count; i += 1) {
    const n = String(i).padStart(2, '0');
    // eslint-disable-next-line no-await-in-loop
    await t.api(page, 'POST', '/api/system/user', {
      user_name: `${SEED_PREFIX}${n}`,
      nick_name: `演示用户${n}`,
      dept_id: [4, 5, 6][i % 3],
      password: '123456',
      phonenumber: `1390000${n}00`,
      email: `${SEED_PREFIX}${n}@example.com`,
      sex: i % 2 ? '0' : '1',
      status: '0',
      remark: '导览录像临时数据',
    });
  }
}

async function cleanSeeded(page) {
  const body = await t.api(page, 'GET', `/api/system/user?user_name=${SEED_PREFIX}&page=1&size=50`);
  for (const u of (body && body.data && body.data.rows) || []) {
    // eslint-disable-next-line no-await-in-loop
    await t.api(page, 'DELETE', `/api/system/user/${u.user_id}`);
  }
}

async function findUserId(page, userName) {
  const body = await t.api(page, 'GET', `/api/system/user?user_name=${userName}&page=1&size=10`);
  const hit = ((body && body.data && body.data.rows) || []).find((u) => u.user_name === userName);
  return hit && hit.user_id;
}

async function deleteUserByName(page, userName) {
  const id = await findUserId(page, userName);
  if (id) await t.api(page, 'DELETE', `/api/system/user/${id}`);
  return !!id;
}

/** 清掉历史失败运行残留的导览用户（前缀 tour）。 */
async function cleanTourUsers(page) {
  const body = await t.api(page, 'GET', '/api/system/user?user_name=tour&page=1&size=100');
  for (const u of (body && body.data && body.data.rows) || []) {
    if (String(u.user_name).startsWith('tour')) {
      // eslint-disable-next-line no-await-in-loop
      await t.api(page, 'DELETE', `/api/system/user/${u.user_id}`);
    }
  }
}

test('03｜用户管理：部门树、搜索与表格', async ({ page }) => {
  await t.open(page);
  await cleanSeeded(page);
  await t.gotoMenu(page, '系统管理', '用户管理');

  await t.chapter(page, {
    n: 3,
    title: '用户管理 · 查询与表格',
    subtitle: '部门树 · 搜索表单 · 工具栏 · 服务端分页',
    points: [
      '左侧部门树由接口下发，点节点即按 dept_id 过滤',
      '搜索表单：用户名称 / 手机号码 / 状态 / 创建时间，折叠带高度动画',
      '工具栏：新增 修改 删除 导入 导出 + 显示搜索 刷新 显隐列',
      '分页用 page / size，导出带令牌下载 CSV',
    ],
  });

  await t.step(page, '先让接口造十二个演示用户，镜头里的表格就有内容了。', async () => {
    await seedUsers(page);
    await circle(page, 'reload').first().click();
  });
  await t.settle(page, 900);

  await t.step(page, '左侧部门树来自后端接口，不是写死的数据。', () =>
    expect(page.getByPlaceholder('请输入部门名称')).toBeVisible()
  );
  await t.step(page, '输入关键字可以直接过滤这棵树。', () =>
    page.getByPlaceholder('请输入部门名称').fill('研发')
  );
  await t.step(page, '点「研发部门」，右侧只剩这个部门的人。', () =>
    page.getByText('研发部门', { exact: true }).first().click()
  );
  await t.settle(page, 700);
  await t.say(page, '能看到哪些部门由后端的数据权限算：角色的 data_scope 决定范围，前端只负责展示。');
  await t.step(page, '清空过滤，回到全量列表。', async () => {
    await page.getByPlaceholder('请输入部门名称').fill('');
    await page.getByText('若依科技', { exact: true }).first().click();
  });
  await t.settle(page, 700);

  await t.step(page, '搜索栏支持四个条件，这里按用户名称查。', async () => {
    // 搜索栏的输入框在 DOM 里排在（隐藏的）新增弹窗之前
    await page.getByPlaceholder('请输入用户名称').first().fill(SEED_PREFIX);
    await page.getByRole('button', { name: /搜\s*索/ }).click();
  });
  await t.settle(page, 900);
  await t.step(page, '查询条件作为 query 参数进后端，Malli 先校验再拼进 SQL。', () =>
    expect(page.getByText(/共 12 条/)).toBeVisible()
  );
  await t.say(page, '状态是枚举下拉，创建时间是范围选择器，其它模块的搜索栏都是同一套写法。');
  await t.step(page, '「重置」把条件恢复默认并重新拉取。', () =>
    page.getByRole('button', { name: /重\s*置/ }).click()
  );
  await t.settle(page, 900);

  await t.say(page, '工具栏右侧三个圆钮：折叠搜索、刷新列表、显隐列。');
  await t.step(page, '折叠搜索栏，带高度过渡动画。', () => circle(page, 'search').last().click());
  await t.quiet(page, 900);
  await t.step(page, '再点一次展开。', () => circle(page, 'search').last().click());
  await t.step(page, '刷新按钮按当前条件重新请求接口。', () => circle(page, 'reload').first().click());
  await t.settle(page, 700);
  await t.step(page, '「显隐列」逐列开关，列表跟随重排。', () => circle(page, 'appstore').first().click());
  await t.step(page, '关掉「手机号码」。', () =>
    page.getByRole('menuitem', { name: /手机号码/ }).click()
  );
  await t.step(page, '表头立刻少一列。', () =>
    expect(page.locator('.ant-table-thead th', { hasText: '手机号码' })).toHaveCount(0)
  );
  await t.step(page, '再打开恢复。', async () => {
    await circle(page, 'appstore').first().click();
    await page.getByRole('menuitem', { name: /手机号码/ }).click();
    await page.keyboard.press('Escape');
  });

  await t.step(page, '状态列是开关，直接调 PUT 接口停用，不必进编辑页。', async () => {
    await rowOf(page, `${SEED_PREFIX}01`).locator('.ant-switch').click();
  });
  await t.settle(page, 800);
  await t.step(page, '再点回来。', () => rowOf(page, `${SEED_PREFIX}01`).locator('.ant-switch').click());
  await t.settle(page, 800);

  await t.say(page, '分页条在页面下方：每页条数、上下页、跳页，全部走服务端分页。');
  await t.step(page, '切到第 2 页。', () => page.locator('button', { hasText: '›' }).click());
  await t.settle(page, 800);
  await t.step(page, '回到第 1 页。', () => page.locator('button', { hasText: '‹' }).click());
  await t.settle(page, 800);

  await t.step(page, '导出：带令牌请求导出接口，浏览器直接落一个 CSV。', async () => {
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20000 }),
      page.getByRole('button', { name: /导\s*出/ }).click(),
    ]);
    expect(download.suggestedFilename()).toContain('users_export');
  });

  await cleanSeeded(page);
});

test('04｜用户管理：新增、详情、重置密码与删除', async ({ page }) => {
  await t.open(page);
  await cleanTourUsers(page);
  await t.gotoMenu(page, '系统管理', '用户管理');

  await t.chapter(page, {
    n: 4,
    title: '用户管理 · 增删改查',
    subtitle: '弹窗表单 · 详情抽屉 · 重置密码 · 批量删除',
    points: [
      '新增 / 修改：双列布局弹窗，部门树选择 + 岗位角色多选',
      '详情：点用户名称从右侧拉出抽屉，角色岗位以标签呈现',
      '重置密码、分配角色是独立接口，密码只以哈希入库',
      '删除受权限与保护账号约束，内置 admin 不可删',
    ],
  });

  await t.step(page, '点「新增」打开表单弹窗。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).click();
    await expect(page.getByRole('heading', { name: '添加用户' })).toBeVisible();
  });
  await t.say(page, '左列是基本信息，右列是账号密码，字段排布与 RuoYi 一致。');
  await t.step(page, '填用户昵称。', () => page.locator('#nick_name').first().fill(TOUR_NICK));
  await t.step(page, '归属部门用树选择器。', async () => {
    await t.openFieldSelect(page, '归属部门');
    await t.pickOption(page, '研发部门', { tree: true });
  });
  await t.step(page, '手机号与邮箱。', async () => {
    await page.locator('#phonenumber').first().fill('13900001234');
    await page.locator('#email').first().fill('tour@example.com');
  });
  await t.step(page, '登录名唯一，密码默认 123456。', () =>
    page.locator('#user_name').fill(TOUR_USER)
  );
  await t.step(page, '性别、岗位、角色都是下拉。', async () => {
    await t.openFieldSelect(page, '用户性别');
    await t.pickOption(page, '男');
    await t.openFieldSelect(page, '角色');
    await t.pickOption(page, '普通角色');
    await page.keyboard.press('Escape');
  });
  await t.step(page, '提交：后端 Malli 再校验一次，前端规则只是体验。', () =>
    page.getByRole('button', { name: /确\s*定/ }).last().click()
  );
  await t.settle(page, 1300);
  await t.step(page, '创建成功，列表刷新出现新用户。', () =>
    expect(rowOf(page, TOUR_NICK)).toBeVisible()
  );

  await t.step(page, '点用户名称打开详情抽屉。', async () => {
    await rowOf(page, TOUR_NICK).getByText(TOUR_USER).click();
    await expect(page.locator('.ant-drawer-title')).toHaveText('用户详情');
  });
  await t.say(page, '抽屉里是完整档案，角色与岗位以标签呈现，全部数据来自详情接口。');
  await t.step(page, '关闭抽屉。', () => page.locator('.ant-drawer-close').click());
  await t.quiet(page, 600);

  await t.say(page, '「更多」下拉是悬停展开的：里面有重置密码和分配角色。');
  await t.step(page, '重置密码写独立字段，输入新密码提交。', async () => {
    await t.dropdownItem(page, rowOf(page, TOUR_NICK).getByText('更多'), '重置密码');
    await page.getByPlaceholder('请输入新密码').fill('Tour@12345');
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1100);
  await t.say(page, '数据库里只存 BCrypt 哈希；查询接口返回的记录永远不带 password 字段。');

  await t.step(page, '「更多」→ 分配角色，改完点确定。', async () => {
    await t.dropdownItem(page, rowOf(page, TOUR_NICK).getByText('更多'), '分配角色');
    await expect(page.getByRole('heading', { name: `分配角色 - ${TOUR_USER}` })).toBeVisible();
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1000);

  await t.step(page, '修改：只改昵称提交，后端按给出的字段做局部更新。', async () => {
    await rowOf(page, TOUR_NICK).getByText('修改').click();
    await expect(page.getByRole('heading', { name: '修改用户' })).toBeVisible();
    await page.locator('#nick_name').first().fill(`${TOUR_NICK}改`);
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1300);
  await t.step(page, '列表里的昵称已经更新。', () =>
    expect(rowOf(page, `${TOUR_NICK}改`)).toBeVisible()
  );

  await t.say(page, '注意 admin 这一行：删除按钮是灰的，内置管理员不允许删。');
  await t.step(page, '勾选导览用户，用工具栏批量删除。', async () => {
    await rowOf(page, `${TOUR_NICK}改`).locator('.ant-checkbox').click();
    await page.getByRole('button', { name: /删\s*除/ }).first().click();
  });
  await t.settle(page, 1400);
  await t.step(page, '删除完成，列表回到干净状态。', () =>
    expect(rowOf(page, TOUR_NICK)).toHaveCount(0)
  );
  await cleanTourUsers(page);
});
