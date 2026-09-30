// 分镜 05 角色管理 · 数据权限与只读视角
// 分镜 06 菜单管理 · 部门管理
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

const SUFFIX = String(Date.now()).slice(-6);
const TOUR_ROLE = `导览角色${SUFFIX}`;
const TOUR_ROLE_KEY = `tourrole${SUFFIX}`;
const TOUR_VIEWER = `tourview${SUFFIX}`;
const TOUR_DEPT = `导览部门${SUFFIX}`;

const rowOf = (page, sel, text) => page.locator(sel + ' tr', { hasText: text }).first();
const roleRow = (page, text) => rowOf(page, '.ant-table-tbody', text);

async function adminHeaders(page) {
  const token = await page.evaluate(() => localStorage.getItem('ruoyi_token'));
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function createRoleWithMenus(page, roleName, roleKey, menuIds) {
  const res = await page.request.post('/api/system/role', {
    headers: await adminHeaders(page),
    data: { role_name: roleName, role_key: roleKey, role_sort: 9, status: '0', 'menu-ids': menuIds },
  });
  const body = await res.json();
  return Number(String(body.data).match(/\d+/)[0]);
}

test('05｜角色管理：权限、数据范围与只读视角', async ({ page }) => {
  await t.open(page);
  await t.gotoMenu(page, '系统管理', '角色管理');

  await t.chapter(page, {
    n: 5,
    title: '角色 · 菜单权限 · 数据权限',
    subtitle: '按钮级权限在前端只做显隐，拦截在后端',
    points: [
      '角色表单：名称 / 权限字符 / 顺序 / 状态 / 备注',
      '分配权限：菜单树勾选，保存后整页重载以重建菜单',
      '数据权限：全部 / 自定义 / 本部门 / 本部门及以下 / 仅本人',
      '前端按 getInfo 的 permissions 渲染按钮，admin 是 *:*:*',
    ],
  });

  await t.step(page, '按角色名称搜索，条件同样是 query 参数交给后端。', async () => {
    await page.getByPlaceholder('请输入角色名称').fill('普通');
    await page.getByRole('button', { name: /搜\s*索/ }).click();
  });
  await t.settle(page, 900);
  await t.step(page, '列表里状态是标签，角色名称可以直接点进编辑。', () =>
    expect(roleRow(page, '普通角色')).toBeVisible()
  );
  await t.step(page, '清空条件。', () => page.getByRole('button', { name: /重\s*置/ }).click());
  await t.settle(page, 800);

  await t.step(page, '新增一个导览角色。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增角色' })).toBeVisible();
    await page.locator('#role_name').fill(TOUR_ROLE);
    await page.locator('#role_key').fill(TOUR_ROLE_KEY);
    await page.locator('#role_sort').fill('9');
    await page.getByRole('button', { name: /确\s*定/ }).click();
  });
  await t.settle(page, 1100);
  await t.step(page, '角色已建好。', () => expect(roleRow(page, TOUR_ROLE)).toBeVisible());

  await t.say(page, '把鼠标移到行尾的「更多」：数据权限、分配用户、分配权限都在这里面。');
  await t.step(page, '打开数据权限，选「自定义数据」。', async () => {
    await t.dropdownItem(page, roleRow(page, TOUR_ROLE).getByText('更多'), '数据权限');
    await expect(page.getByRole('dialog', { name: `数据权限 - ${TOUR_ROLE}` })).toBeVisible();
    await page.getByRole("dialog").getByText('自定义数据').click();
  });
  await t.step(page, '选部门：这里只给它「研发部门」的可见范围。', async () => {
    await page.locator('.ant-tree-treenode', { hasText: '研发部门' }).first()
      .locator('.ant-tree-checkbox').click();
  });
  await t.step(page, '保存。', () => page.getByRole('button', { name: /确\s*定/ }).click());
  await t.settle(page, 1000);
  await t.say(page, '后端在列表 SQL 里拼 dept_id 范围，多角色取并集，越权的数据根本查不出来。');

  await t.step(page, '再打开「分配权限」：一棵菜单树，目录 / 菜单 / 按钮三层。', async () => {
    await t.dropdownItem(page, roleRow(page, TOUR_ROLE).getByText('更多'), '分配权限');
    await expect(page.getByRole('dialog', { name: `分配权限 - ${TOUR_ROLE}` })).toBeVisible();
  });
  await t.say(page, '勾一个菜单，保存后会重建左侧菜单；这里先取消，不留改动。');
  await t.step(page, '取消。', () => page.getByRole('button', { name: /取\s*消/ }).click());

  await t.step(page, '删除导览角色，行内删除带二次确认。', async () => {
    await roleRow(page, TOUR_ROLE).getByText('删除').click();
    await expect(page.getByText('确认删除该角色？')).toBeVisible();
    await page.getByRole('button', { name: /确\s*定/ }).click();
  });
  await t.settle(page, 1100);

  const roleId = await createRoleWithMenus(page, `导览只读${SUFFIX}`, `tourview${SUFFIX}`, [1, 3, 100, 4]);
  await t.api(page, 'POST', '/api/system/user', {
    user_name: TOUR_VIEWER,
    nick_name: '导览只读用户',
    password: 'viewer123',
    roles: [roleId],
  });

  await t.say(page, '换一个视角：用刚建的只读账号登录，看看同一套页面长什么样。');
  await t.signOut(page);
  await t.login(page, TOUR_VIEWER, 'viewer123');
  await t.settle(page, 1200);
  await t.step(page, '侧边栏只剩授权过的菜单，系统管理以外都看不到。', () =>
    expect(page.getByText('系统监控')).toHaveCount(0)
  );
  await t.gotoMenu(page, '系统管理', '用户管理');
  await t.step(page, '列表能看，但新增 / 导入 / 导出按钮一个都没有。', async () => {
    await expect(page.getByRole('button', { name: /新\s*增/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /导\s*入/ })).toHaveCount(0);
  });
  await t.step(page, '行内操作列也是空的，状态开关是禁用状态。', async () => {
    const row = page.locator('.ant-table-tbody tr.ant-table-row', { hasText: 'admin' }).first();
    await expect(row.getByRole('button')).toHaveCount(0);
    await expect(row.locator('.ant-switch')).toBeDisabled();
  });
  await t.say(page, '未授权的接口直接 403，前端统一弹「没有操作权限」。');
  await t.step(page, '访问岗位管理，接口拒绝。', async () => {
    await page.goto('/system/post');
    await expect(page.getByText('没有操作权限')).toBeVisible({ timeout: 15000 });
  });

  await t.signOut(page);
  await t.login(page);
  await t.settle(page, 1000);
  await t.api(page, 'DELETE', `/api/system/role/${roleId}`);
  const body = await t.api(page, 'GET', `/api/system/user?user_name=${TOUR_VIEWER}&page=1&size=5`);
  for (const u of (body && body.data && body.data.rows) || []) {
    await t.api(page, 'DELETE', `/api/system/user/${u.user_id}`);
  }
});

test('06｜菜单管理与部门管理：树形结构', async ({ page }) => {
  await t.open(page);
  // 清掉历史失败运行残留的演示菜单 / 部门
  const menus = await t.api(page, 'GET', '/api/system/menu');
  for (const m of (menus && menus.data) || []) {
    if (String(m.menu_name || '').startsWith('导览部门')) {
      // eslint-disable-next-line no-await-in-loop
      await t.api(page, 'DELETE', `/api/system/menu/${m.menu_id}`);
    }
  }
  const depts = await t.api(page, 'GET', '/api/system/dept/tree');
  for (const d of (depts && depts.data) || []) {
    if (String(d.dept_name || '').startsWith('导览部门')) {
      // eslint-disable-next-line no-await-in-loop
      await t.api(page, 'DELETE', `/api/system/dept/${d.dept_id}`);
    }
  }
  await t.gotoMenu(page, '系统管理', '菜单管理');

  await t.chapter(page, {
    n: 6,
    title: '菜单 · 部门：两棵动态树',
    subtitle: '树形表格 · 图标选择器 · 菜单即权限',
    points: [
      '菜单表就是前端路由与按钮权限的数据源，列表不分页',
      '新增菜单：目录 / 菜单 / 按钮三种类型，字段随类型联动',
      '图标选择器是点击弹出的图标网格，选完写回 menu_name 前缀',
      '部门表同为树形表格，上级部门用树选择器',
    ],
  });

  await t.say(page, '菜单列表是一张默认全展开的树形表格：名称、图标、排序、权限标识、组件路径、类型。');
  await t.step(page, '类型列用标签区分目录 / 菜单 / 按钮 / 外链。', () =>
    expect(page.locator('.ant-table-tbody tr.ant-table-row').first()).toBeVisible()
  );
  await t.step(page, '行内的「新增」是以当前行为上级菜单再建一层。', null, { after: 700 });

  await t.step(page, '点工具栏「新增」打开菜单表单。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增菜单' })).toBeVisible();
  });
  await t.say(page, '菜单类型选「按钮」时，路由相关的字段会自动收起——按钮只贡献权限标识。');
  await t.step(page, '切到「按钮」类型看看字段变化。', async () => {
    await page.getByRole("dialog").getByText('按钮', { exact: true }).click();
  });
  await t.step(page, '切回「菜单」。', () =>
    page.getByRole("dialog").getByText('菜单', { exact: true }).click()
  );
  await t.step(page, '填菜单名称，并打开图标选择器。', async () => {
    await page.locator('#menu_name').fill(TOUR_DEPT);
    await page.getByRole('button', { name: /选择图标/ }).click();
  });
  await t.step(page, '图标网格里点一个。', async () => {
    await page.locator('.ant-popover .anticon').nth(3).click();
  });
  await t.say(page, '图标名会存进菜单记录，侧边栏渲染时再取出来，不写死在前端。');
  await t.step(page, '选择器按钮回显图标名。', async () => {
    await expect(page.locator('.ant-modal button', { hasText: /Outlined/ }).first()).toBeVisible();
  });
  await t.step(page, '保存菜单。', async () => {
    await page.locator('#parent_id').click();
    await page.locator('.ant-select-tree-title', { hasText: '系统管理' }).first().click();
    await page.locator('#path').fill('tour-demo');
    await t.settle(page, 400);
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1400);
  await t.say(page, '这条菜单只是演示：随后删掉，避免污染真实导航。');
  await t.step(page, '新菜单已经挂进树里。', async () => {
    const row = page.locator('.ant-table-tbody tr.ant-table-row', { hasText: TOUR_DEPT }).first();
    await t.focusOn(page, row, 500);
    await expect(row).toBeVisible();
  });
  await t.step(page, '行内「删除」带 Popconfirm 二次确认。', async () => {
    const row = page.locator('.ant-table-tbody tr.ant-table-row', { hasText: TOUR_DEPT }).first();
    await row.getByText('删除').click();
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1200);

  await t.gotoMenu(page, '系统管理', '部门管理');
  await t.say(page, '部门管理同样是树形表格，默认全部展开，列里有负责人与联系电话。');
  await t.step(page, '新增一个部门。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增部门' })).toBeVisible();
    await page.locator('#dept_name').fill(TOUR_DEPT);
    await page.locator('#order_num').fill('9');
    await page.locator('#leader').fill('导览员');
    await page.locator('#phone').fill('13800000000');
  });
  await t.step(page, '上级部门用树选择器，选到「长沙分公司」下面。', async () => {
    await t.openFieldSelect(page, '上级部门');
    await t.pickOption(page, '长沙分公司', { tree: true });
  });
  await t.step(page, '保存。', async () => {
    await t.settle(page, 400);
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1200);
  await t.step(page, '新部门挂在树上的对应位置。', () =>
    expect(page.getByText(TOUR_DEPT).first()).toBeVisible()
  );
  await t.say(page, '工具栏的「展开/折叠」一键收起整棵树，再点一下恢复全展开。');
  await t.step(page, '折叠全部。', async () => {
    await page.getByRole('button', { name: /展开\/折叠/ }).click();
    await t.settle(page, 800);
  });
  await t.step(page, '再次展开。', async () => {
    await page.getByRole('button', { name: /展开\/折叠/ }).click();
    await t.settle(page, 800);
  });
  await t.step(page, '删掉演示数据，行内删除带确认。', async () => {
    await page.locator('.ant-table-tbody tr.ant-table-row', { hasText: TOUR_DEPT }).first().getByText('删除').click();
    await expect(page.getByText('确认删除该部门？')).toBeVisible();
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1100);
});
