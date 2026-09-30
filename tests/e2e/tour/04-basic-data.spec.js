// 分镜 07 基础数据：岗位管理 · 字典管理 · 参数管理
// 分镜 08 通知公告：富文本编辑与顶部铃铛已读
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

const SUFFIX = String(Date.now()).slice(-6);
const TOUR_POST = `导览岗位${SUFFIX}`;
const TOUR_DICT = `导览字典${SUFFIX}`;
const TOUR_DICT_TYPE = `tour_dict_${SUFFIX}`;
const TOUR_DICT_LABEL = `导览标签${SUFFIX}`;
const TOUR_CONFIG = `导览参数${SUFFIX}`;
const TOUR_NOTICE = `导览公告${SUFFIX}`;

const rows = (page, text) => page.locator('.ant-table-tbody tr.ant-table-row', { hasText: text });

/** 删掉历史失败运行残留的演示数据（按名称前缀）。 */
async function cleanLeftovers(page) {
  const lists = [
    ['/api/system/post', 'post_name', 'post_id', '/api/system/post/'],
    ['/api/system/config', 'config_name', 'config_id', '/api/system/config/'],
    ['/api/system/dict/type', 'dict_name', 'dict_id', '/api/system/dict/type/'],
  ];
  for (const [url, nameKey, idKey, delPrefix] of lists) {
    const body = await t.api(page, 'GET', `${url}?page=1&size=200`);
    const items = (body && (body.data.rows || body.data)) || [];
    for (const it of items) {
      if (String(it[nameKey] || '').startsWith('导览')) {
        // eslint-disable-next-line no-await-in-loop
        await t.api(page, 'DELETE', `${delPrefix}${it[idKey]}`);
      }
    }
  }
  const notices = await t.api(page, 'GET', '/api/system/notice?page=1&size=200');
  for (const n of (notices && notices.data && notices.data.rows) || []) {
    if (String(n.notice_name || '').startsWith('导览')) {
      // eslint-disable-next-line no-await-in-loop
      await t.api(page, 'DELETE', `/api/system/notice/${n.notice_id}`);
    }
  }
}

/** 自定义弹窗（非 antd Form）没有字段 id，按顺序取输入框。
 *  antd 6 的弹窗容器是 .ant-modal-container；用 :visible 排掉上一次留下的隐藏弹窗。 */
const modal = (page) => page.locator('.ant-modal-container:visible');
const modalInput = (page, n) => modal(page).locator('input').nth(n);
const modalTextarea = (page) => modal(page).locator('textarea');
const confirmDialog = (page) => modal(page).getByRole('button', { name: /确\s*定/ });
const confirmPop = (page) => page.locator('.ant-popover button', { hasText: /确\s*认|确\s*定/ }).last();

test('07｜基础数据：岗位、字典与参数', async ({ page }) => {
  await t.open(page);
  await cleanLeftovers(page);
  await t.gotoMenu(page, '系统管理', '岗位管理');

  await t.chapter(page, {
    n: 7,
    title: '岗位 · 字典 · 参数',
    subtitle: '三类基础数据共用同一套列表模板',
    points: [
      '岗位：标准分页列表 + 弹窗表单 + 导出',
      '字典：类型列表点开数据列表，两级联动',
      '参数：系统内置参数用标签区分，键名全局唯一',
      '同一套 page-search / page-toolbar 组件保证视觉一致',
    ],
  });

  // ── 岗位 ─────────────────────────────────────────────────────────
  await t.say(page, '岗位是最简单的模板：搜索栏、工具栏、分页表格，一个弹窗搞定新增和修改。');
  await t.step(page, '按岗位名称搜索。', async () => {
    await page.getByPlaceholder('请输入岗位名称').fill('董事长');
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 900);
  });
  await t.step(page, '搜索命中后重置。', async () => {
    await page.getByRole('button', { name: /重\s*置/ }).first().click();
    await t.settle(page, 900);
  });
  await t.step(page, '新增岗位：编码 / 名称 / 排序都是普通字段。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增岗位' })).toBeVisible();
    await page.locator('#post_code').fill(`tour_post_${SUFFIX}`);
    await page.locator('#post_name').fill(TOUR_POST);
    await page.locator('#post_sort').fill('9');
    await modalTextarea(page).fill('导览演示岗位');
    await confirmDialog(page).click();
  });
  await t.settle(page, 1300);
  await t.step(page, '列表出现新岗位。', async () => {
    await expect(rows(page, TOUR_POST).first()).toBeVisible();
  });
  await t.step(page, '行内「编辑」改名。', async () => {
    await rows(page, TOUR_POST).first().getByText('编辑').click();
    await expect(page.getByRole('dialog', { name: '修改岗位' })).toBeVisible();
    await page.locator('#post_name').fill(`${TOUR_POST}改`);
    await confirmDialog(page).click();
  });
  await t.settle(page, 1200);
  await t.step(page, '修改生效。', () =>
    expect(rows(page, `${TOUR_POST}改`).first()).toBeVisible()
  );
  await t.say(page, '删除走 Popconfirm 二次确认——这是全站统一的交互约定。');
  await t.step(page, '删除演示岗位。', async () => {
    await rows(page, `${TOUR_POST}改`).first().getByText('删除').click();
    await expect(page.getByText('确认删除该岗位？')).toBeVisible();
    await confirmPop(page).click();
  });
  await t.settle(page, 1100);
  await t.step(page, '列表已无该岗位。', () =>
    expect(rows(page, TOUR_POST)).toHaveCount(0)
  );

  // ── 字典 ─────────────────────────────────────────────────────────
  await t.gotoMenu(page, '系统管理', '字典管理');
  await t.say(page, '字典分两级：左边选类型，下面就是这个类型的键值对。');
  await t.step(page, '看几个内置字典类型。', async () => {
    const row = rows(page, '用户性别').first();
    await t.focusOn(page, row, 500);
    await expect(row).toBeVisible();
  });
  await t.step(page, '新增一个字典类型。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增字典类型' })).toBeVisible();
    await modalInput(page, 0).fill(TOUR_DICT);
    await modalInput(page, 1).fill(TOUR_DICT_TYPE);
    await modalTextarea(page).fill('导览演示字典');
    await confirmDialog(page).click();
  });
  await t.settle(page, 1200);
  await t.say(page, '类型列表是分页的，先按名称搜出刚建的那一条。');
  await t.step(page, '按名称搜出演示类型。', async () => {
    await page.getByPlaceholder('请输入字典名称').fill(TOUR_DICT);
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 900);
    await expect(rows(page, TOUR_DICT).first()).toBeVisible();
  });
  await t.say(page, '点行的「字典数据」，下方就地展开这个类型的明细列表。');
  await t.step(page, '打开该类型的字典数据。', async () => {
    await rows(page, TOUR_DICT).first().getByText('字典数据').click();
    await t.settle(page, 900);
    await expect(page.getByText(`字典数据 — ${TOUR_DICT}`).first()).toBeVisible();
  });
  await t.step(page, '新增一条字典数据（标签 / 键值 / 排序）。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).last().click();
    await expect(page.getByRole('dialog', { name: '新增字典数据' })).toBeVisible();
    await modalInput(page, 0).fill(TOUR_DICT_LABEL);
    await modalInput(page, 1).fill('0');
    await confirmDialog(page).click();
  });
  await t.settle(page, 1200);
  await t.step(page, '字典数据出现在明细表里。', () =>
    expect(rows(page, TOUR_DICT_LABEL).first()).toBeVisible()
  );
  await t.say(page, '字典值最终喂给前端下拉框和状态标签，业务代码里不写魔法字符串。');
  await t.step(page, '删除字典数据。', async () => {
    await rows(page, TOUR_DICT_LABEL).first().getByText('删除').click();
    await confirmPop(page).click();
  });
  await t.settle(page, 1100);
  await t.step(page, '返回类型列表并删掉演示类型。', async () => {
    await page.getByRole('button', { name: '返回类型列表' }).click();
    await t.settle(page, 800);
    await page.getByPlaceholder('请输入字典名称').fill(TOUR_DICT);
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 900);
    await rows(page, TOUR_DICT).first().getByText('删除').click();
    await confirmPop(page).click();
  });
  await t.settle(page, 1100);

  // ── 参数 ─────────────────────────────────────────────────────────
  await t.gotoMenu(page, '系统管理', '参数管理');
  await t.say(page, '参数管理是运行期可改的配置项，比如登录验证码开关、默认密码。');
  await t.step(page, '系统内置的参数带「是」标签。', () =>
    expect(page.getByText('主框架页').first()).toBeVisible()
  );
  await t.step(page, '新增一个演示参数。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增参数' })).toBeVisible();
    await modalInput(page, 0).fill(TOUR_CONFIG);
    await modalInput(page, 1).fill(`tour.config.${SUFFIX}`);
    await modalInput(page, 2).fill('true');
    await confirmDialog(page).click();
  });
  await t.settle(page, 1200);
  await t.step(page, '搜索刚建的参数：后端按名称模糊匹配。', async () => {
    await page.getByPlaceholder('请输入参数名称').fill(TOUR_CONFIG);
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 900);
    await expect(rows(page, TOUR_CONFIG).first()).toBeVisible();
  });
  await t.step(page, '删掉它。', async () => {
    await rows(page, TOUR_CONFIG).first().getByText('删除').click();
    await confirmPop(page).click();
  });
  await t.settle(page, 1100);
});

test('08｜通知公告：富文本与顶部铃铛', async ({ page }) => {
  await t.open(page);
  await cleanLeftovers(page);
  await t.gotoMenu(page, '系统管理', '通知公告');

  await t.chapter(page, {
    n: 8,
    title: '通知公告 · 顶部铃铛',
    subtitle: 'Quill 富文本 · 已读状态记在服务端',
    points: [
      '公告正文用 react-quill 编辑，富文本原样入库',
      '类型分「通知 / 公告」，状态可直接关闭',
      '顶部铃铛按当前用户统计未读，看过即清零',
      '已读记录存在后端，换浏览器登录同样不再提示',
    ],
  });

  await t.step(page, '新增公告：标题 + 类型 + 富文本正文。', async () => {
    await page.getByRole('button', { name: /新\s*增/ }).first().click();
    await expect(page.getByRole('dialog', { name: '新增通知公告' })).toBeVisible();
    await page.locator('#notice_name').fill(TOUR_NOTICE);
    await page.locator('#notice_type').click();
    await t.pickOption(page, '公告');
    // 富文本编辑器靠 Quill/Parchment 的 static super，编译坏时弹窗渲染不出来（见 bb/tasks/vendor.clj）
    const editor = page.locator('.ql-editor');
    await expect(editor).toBeVisible();
    await editor.click();
    await page.keyboard.type('这是一条由导览录像创建的演示公告。');
    await expect(editor).toContainText('导览录像创建的演示公告');
    await page.locator('#remark').fill('导览演示');
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1400);
  await t.say(page, '保存后列表刷新，类型列用蓝色标签显示「公告」。');
  await t.step(page, '列表里能看到，并可按标题搜索。', async () => {
    await expect(rows(page, TOUR_NOTICE).first()).toBeVisible();
    // 关闭的弹窗仍留在 DOM 里，搜索框要用 .first() 取页面顶部那一个
    await page.getByPlaceholder('请输入公告标题').first().fill(TOUR_NOTICE);
    await page.getByRole('button', { name: /搜\s*索/ }).first().click();
    await t.settle(page, 900);
    await expect(rows(page, TOUR_NOTICE).first()).toBeVisible();
    await page.getByRole('button', { name: /重\s*置/ }).first().click();
  });
  await t.settle(page, 900);

  await t.say(page, '新公告会进入顶部铃铛的未读数——注意右上角的红点。');
  await t.step(page, '刷新后铃铛出现未读数。', async () => {
    await t.reload(page, 900);
    const bell = page.locator('.ant-layout-header .ant-badge');
    await expect(bell.locator('.ant-badge-count')).toBeVisible({ timeout: 15000 });
    await bell.getByRole('button').click();
    await t.settle(page, 700);
  });
  await t.step(page, '弹层里就是刚发布的公告。', () =>
    expect(page.getByText(TOUR_NOTICE).last()).toBeVisible()
  );
  await t.step(page, '收起弹层即记为已读，红点消失。', async () => {
    await page.locator('.ant-layout-header .ant-badge').getByRole('button').click();
    await t.settle(page, 900);
    await expect(page.locator('.ant-layout-header .ant-badge .ant-badge-count')).toHaveCount(0);
  });
  await t.say(page, '已读状态按用户存在后端，刷新或换浏览器登录都不会再次提醒。');

  await t.step(page, '编辑公告，把状态改成「关闭」。', async () => {
    await rows(page, TOUR_NOTICE).first().getByText('编辑').click();
    await expect(page.getByRole('dialog', { name: '编辑通知公告' })).toBeVisible();
    await page.locator('#notice_name').fill(`${TOUR_NOTICE}改`);
    await page.getByRole('dialog').getByText('关闭', { exact: true }).click();
    await page.getByRole('button', { name: /确\s*定/ }).last().click();
  });
  await t.settle(page, 1300);
  await t.step(page, '状态标签变为「关闭」。', async () => {
    const row = rows(page, `${TOUR_NOTICE}改`).first();
    await expect(row).toBeVisible();
    await expect(row.getByText('关闭')).toBeVisible();
  });
  await t.step(page, '删除演示公告。', async () => {
    await rows(page, `${TOUR_NOTICE}改`).first().getByText('删除').click();
    await t.settle(page, 1200);
    await expect(rows(page, TOUR_NOTICE)).toHaveCount(0);
  });
});
