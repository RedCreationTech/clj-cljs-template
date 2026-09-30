// 分镜 11 监控与工具：服务监控 · 缓存监控 · 连接池 · Integrant 视图 · Swagger · 表单构建
// 分镜 12 个性化：多标签页 · 中英文 · 密度字号 · 暗色主题 · 个人中心
const { test, expect } = require('@playwright/test');
const t = require('./tour-helper');

const rows = (page, text) => page.locator('.ant-table-tbody tr.ant-table-row', { hasText: text });

/** 顶栏图标按钮按 anticon 类名定位（title 属性会随语言变化）。 */
const headerIcon = (page, icon) => page.locator(`.ant-layout-header .anticon-${icon}`);

/** 属性面板里的输入框：Form.Item 只有 label 没有 name，按表单项文案定位。 */
const propField = (page, label) =>
  page.locator('.ant-form-item', { hasText: label }).locator('input').first();

/** 打开「显示设置」浮层里的某个分段选项。浮层用 .last() 取当前这一个，Esc 收起。 */
async function pickSegment(page, label) {
  await headerIcon(page, 'font-size').click();
  await t.wait(page, 400);
  const popover = page.locator('.ant-popover:visible').last();
  await popover.locator('.ant-segmented-item', { hasText: label }).click();
  await t.wait(page, 600);
  await page.keyboard.press('Escape');
  await t.wait(page, 400);
}

/** 头像下拉菜单。 */
async function avatarMenu(page, label) {
  await page.locator('.ant-layout-header .ant-avatar').click();
  await t.wait(page, 400);
  await page.locator('.ant-dropdown:not(.ant-dropdown-hidden)').getByText(label, { exact: true }).first().click();
  await t.settle(page, 900);
}

/** 现造一张 200x200 的渐变 PNG（在空白页里渲染后截图），头像上传用它，仓库里不留二进制文件。 */
async function makeAvatarPng(context) {
  const tab = await context.newPage();
  await tab.setContent(
    '<body style="margin:0;width:200px;height:200px;background:linear-gradient(135deg,#409eff,#67c23a)"></body>'
  );
  const buffer = await tab.screenshot({ type: 'png', clip: { x: 0, y: 0, width: 200, height: 200 } });
  await tab.close();
  return buffer;
}

test('11｜监控与工具：从连接池到依赖图', async ({ page }) => {
  await t.open(page);
  await t.gotoMenu(page, '系统监控', '服务监控');

  await t.chapter(page, {
    n: 11,
    title: '监控与开发者工具',
    subtitle: '服务监控 · 缓存监控 · 连接池 · 依赖图 · OpenAPI',
    points: [
      '服务监控读 JMX 与系统命令，磁盘按分区列出',
      '缓存监控统计内存缓存 Key 数量，可按键清除',
      '连接池监视暴露 HikariCP 实时指标',
      'Integrant 依赖图把后端的系统配置画成节点连线',
      '接口文档由路由元数据自动生成，表单构建产出模板',
    ],
  });

  await t.say(page, '服务监控不装 Agent：CPU、内存、JVM、磁盘全部在 Clojure 里现采。');
  await t.step(page, 'CPU 与内存卡片有真实读数。', async () => {
    await expect(page.getByText('CPU').first()).toBeVisible();
    await expect(page.getByText('服务器信息').first()).toBeVisible();
    await expect(page.getByText('Java虚拟机信息').first()).toBeVisible();
    await expect(page.getByText('磁盘状态').first()).toBeVisible();
    await t.focusOn(page, page.getByText('磁盘状态').first(), 600);
  });

  await t.gotoMenu(page, '系统监控', '缓存监控');
  await t.say(page, '缓存监控对应 RuoYi 的 Redis 面板：这里换成进程内缓存，Key 数量与命令统计照样能看。');
  await t.step(page, '缓存面板渲染出统计卡。', async () => {
    await expect(page.getByText('Memory Cache').first()).toBeVisible();
    await t.pan(page, 260, 900);
  });
  await t.say(page, '右上角两个按钮：刷新重新拉统计；清空是独立权限 monitor:cache:remove，而且要二次确认。');
  await t.step(page, '点「清空」，先弹确认框。', async () => {
    await page.getByRole('button', { name: /清\s*空/ }).click();
    await expect(page.getByText('确认清空全部缓存？')).toBeVisible();
    await t.quiet(page, 700);
  });
  await t.step(page, '确认后接口回「缓存已清空」，命令统计的 clear 计数加一。', async () => {
    await page.locator('.ant-popconfirm .ant-btn-primary, .ant-popover .ant-btn-primary').first().click();
    await expect(page.getByText('缓存已清空', { exact: true })).toBeVisible({ timeout: 10000 });
    await t.settle(page, 1400);
  });
  await t.say(page, '这一页是 RuoYi Redis 面板的对应物：四个演示缓存空间，键值可以逐条查看；真正要跨实例共享的临时状态——验证码、登录失败计数、令牌续期宽限——存在 sys_kv 表里，由 infra.kv 统一管理。');

  await t.gotoMenu(page, '系统监控', '数据监控');
  await t.say(page, '连接池监视直接读 HikariCP 的注册指标，活跃 / 空闲 / 等待线程一目了然。');
  await t.step(page, '连接池表格里有活跃连接数。', async () => {
    // 指标接口比列表慢一点，多给一次轮询的时间
    await expect(page.getByText('活跃连接数').first()).toBeVisible({ timeout: 15000 });
  });

  await t.gotoMenu(page, '系统监控', 'Integrant 依赖');
  await t.say(page, '这页是模板特有的：把 Integrant 的系统配置展开成节点图，谁依赖谁、谁先启动一目了然。');
  await t.step(page, '依赖图渲染出组件节点。', async () => {
    await expect(page.getByText('Integrant 依赖视图')).toBeVisible();
    await expect(page.getByText('依赖图 (Dependency Graph)').first()).toBeVisible();
    await expect(page.locator('.integrant-dep-graph text', { hasText: 'server/http' })).toBeVisible();
    await t.pan(page, 300, 1000);
  });
  await t.say(page, '点选节点能看它的配置摘要；函数型组件还有一个开关，打开后能在右侧看调用追踪。');
  await t.step(page, '选中一个节点，看右侧详情。', async () => {
    await page.locator('.integrant-dep-graph text', { hasText: 'server/http' }).click();
    await t.wait(page, 700);
    await t.pan(page, 320, 700);
  });

  await t.gotoMenu(page, '系统监控', '系统接口');
  await t.say(page, 'OpenAPI 文档不是手写注释，而是从 reitit 的路由数据（summary / perms / 参数 schema）生成。');
  await t.step(page, 'Swagger UI 加载出接口文档。', async () => {
    // 页面本体是一个 iframe，文本断言要进到 frame 里
    // 页面本体是 iframe，断言要进到文档里去（Swagger UI 根节点）
    await expect(page.locator('iframe[src="/api/index.html"]')).toHaveCount(1);
    await expect(page.frameLocator('iframe[src="/api/index.html"]').locator('.swagger-ui').first())
      .toBeVisible({ timeout: 15000 });
    await t.pan(page, 260, 900);
  });

  await t.gotoMenu(page, '系统监控', '表单构建');
  await t.say(page, '表单构建设计器把控件拖成 JSON 模板，后端按模板存 schema，页面再按模板渲染。');
  await t.step(page, '左侧组件面板列出可用控件。', async () => {
    await expect(page.getByRole('heading', { name: '组件面板' })).toBeVisible();
    await expect(page.getByText('拖拽左侧组件到此处')).toBeVisible();
    await t.pan(page, 200, 700);
  });
  await t.say(page, '拖一个「文本输入」进画布，右侧立刻出现它的属性表单。');
  await t.step(page, '拖拽组件到画布并改标签名。', async () => {
    await page.getByText('文本输入', { exact: true }).first()
      .dragTo(page.getByText('拖拽左侧组件到此处'));
    await t.wait(page, 700);
    await expect(page.getByText('[input]')).toBeVisible();
    await page.getByText('[input]').click();
    await expect(page.getByRole('heading', { name: '组件属性' })).toBeVisible();
    // Form.Item 没写 name，label 与 input 没有 htmlFor 关联，按表单项文案定位输入框
    await propField(page, '字段名').fill('userName');
    await propField(page, '标签名').fill('用户名');
    await t.wait(page, 500);
    await expect(page.getByText('用户名', { exact: true }).first()).toBeVisible();
  });
  await t.say(page, '生成代码按钮直接产出可复制的 Hiccup 表单片段，模板也能存下来复用。');
  await t.step(page, '查看生成的 Hiccup 代码。', async () => {
    await page.getByRole('button', { name: '生成代码' }).click();
    await expect(page.getByRole('dialog', { name: '生成 Hiccup 代码' })).toBeVisible();
    await t.quiet(page, 900);
    await page.locator('.ant-modal-container:visible')
      .getByRole('button', { name: '关 闭', exact: true }).click();
    await t.wait(page, 600);
  });
  await t.step(page, '打开模板列表抽屉。', async () => {
    await page.getByRole('button', { name: '加载模板' }).click();
    await expect(page.locator('.ant-drawer-content-wrapper:visible')).toBeVisible();
    await t.pan(page, 300, 900);
    await page.keyboard.press('Escape');
    await t.wait(page, 500);
  });
});

test('12｜个性化：语言、主题与个人中心', async ({ page }) => {
  await t.open(page);
  await t.gotoMenu(page, '系统管理', '用户管理');

  await t.chapter(page, {
    n: 12,
    title: '个性化',
    subtitle: '多标签页 · 中英文 · 密度字号 · 暗色主题 · 个人中心',
    points: [
      '标签页保留各页面状态，右键可关闭其它 / 全部',
      '文案走 i18n/tr 词典，右上角一键切英文',
      '布局密度与字号即时生效，颜色全部走 CSS 变量',
      '亮 / 暗主题与主题色存在本地，刷新后保持',
      '个人中心改昵称手机邮箱，头像走上传接口',
    ],
  });

  await t.say(page, '先看标签页：再打开角色管理，顶部就有了第二个可切换的页签。');
  await t.step(page, '打开第二个标签页。', async () => {
    await t.gotoMenu(page, '系统管理', '角色管理');
    await expect(page.locator('.tab-item', { hasText: '角色管理' })).toBeVisible();
  });
  await t.step(page, '右键页签，选择「关闭其他」。', async () => {
    await page.locator('.tab-item', { hasText: '角色管理' }).click({ button: 'right' });
    await t.wait(page, 500);
    await page.locator('.ant-dropdown:not(.ant-dropdown-hidden)').getByText('关闭其他').first().click();
    await t.settle(page, 900);
    await expect(page.locator('.tab-item')).toHaveCount(2);
  });
  await t.say(page, '页签状态来自 re-frame，切换不会重新请求已经在内存里的列表。');

  await t.step(page, '右上角切到 English。', async () => {
    await headerIcon(page, 'global').click();
    await t.wait(page, 400);
    await page.locator('.ant-dropdown:not(.ant-dropdown-hidden)').getByText('English', { exact: true }).click();
    await t.settle(page, 1200);
  });
  await t.say(page, '外壳文案全部来自词典：页签、右键菜单、按钮、分页都跟着换。');
  await t.step(page, '当前页签变成 Roles。', () =>
    expect(page.locator('.tab-item', { hasText: 'Roles' }).first()).toBeVisible()
  );
  await t.step(page, '切回简体中文。', async () => {
    await headerIcon(page, 'global').click();
    await t.wait(page, 400);
    await page.locator('.ant-dropdown:not(.ant-dropdown-hidden)')
      .getByText('简体中文', { exact: true }).click();
    await t.settle(page, 1200);
  });

  await t.say(page, '显示设置里能调布局密度与字号，整页立即重排。');
  await t.step(page, '字号切到「大」。', () => pickSegment(page, '大'));
  await t.step(page, '布局密度切到「紧凑」。', () => pickSegment(page, '紧凑'));
  await t.say(page, '字号从 14 到 16，antd 的 component token 一起变，不需要重编译。');
  await t.step(page, '字号与密度复原。', async () => {
    await pickSegment(page, '中');
    await pickSegment(page, '默认');
  });

  await t.say(page, '最后是暗色主题：所有颜色都取自 CSS 变量，所以切主题不会留下白块。');
  await t.step(page, '打开布局设置并切到暗色。', async () => {
    await avatarMenu(page, '布局设置');
    const drawer = page.locator('.ant-drawer-content-wrapper:visible');
    await drawer.getByText('暗色', { exact: true }).first().click();
    await t.quiet(page, 1200);
  });
  await t.say(page, '注意字幕条和按钮配色都自动跟随，这是模板里颜色规范的收益。');
  await t.step(page, '暗色下列表依旧清晰。', async () => {
    await page.keyboard.press('Escape'); // 抽屉没有关闭按钮，Esc 收起
    await t.wait(page, 700);
    await t.gotoMenu(page, '系统管理', '用户管理');
    await expect(rows(page, 'admin').first()).toBeVisible();
  });
  await t.step(page, '切回亮色。', async () => {
    await avatarMenu(page, '布局设置');
    const drawer = page.locator('.ant-drawer-content-wrapper:visible');
    await drawer.getByText('亮色', { exact: true }).first().click();
    await t.wait(page, 900);
    await page.keyboard.press('Escape');
    await t.wait(page, 500);
  });

  await t.say(page, '个人中心可以改昵称、手机与邮箱，头像支持上传，走的正是同一套带令牌的传输层。');
  await t.step(page, '打开个人中心。', () => avatarMenu(page, '个人中心'));
  await t.step(page, '左侧档案卡：用户、部门、角色、创建日期都来自详情接口。', async () => {
    await expect(page.getByText('个人信息').first()).toBeVisible();
    await expect(page.getByText('用户昵称').first()).toBeVisible();
    await expect(page.getByText('手机号码').first()).toBeVisible();
    await expect(page.getByText('所属部门').first()).toBeVisible();
  });

  await t.say(page, '头像就是一张图：点圆圈弹出选文件，上传成功再重新拉一次档案。');
  await t.step(page, '选一张 PNG 提交，头像立刻换成上传的图。', async () => {
    const before = await page.locator('#avatar-upload + div img').getAttribute('src');
    const buffer = await makeAvatarPng(page.context());
    const [chooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.locator('#avatar-upload + div').click(),
    ]);
    await chooser.setFiles({ name: 'tour-avatar.png', mimeType: 'image/png', buffer });
    const img = page.locator('#avatar-upload + div img');
    await expect(img).toBeVisible({ timeout: 20000 });
    await expect(img).not.toHaveAttribute('src', before ?? '');
  });
  await t.settle(page, 1000);
  await t.say(page, '文件经 infra.files 的 store! 落到上传目录的 avatar 子目录，库里只存相对路径。');
  await t.step(page, '顶部头像同步换上了。', async () => {
    await expect(page.locator('.ant-layout-header .ant-avatar img, .ant-layout-header .ant-avatar-image').first())
      .toBeVisible({ timeout: 10000 });
  });

  await t.step(page, '切到「修改密码」页签。', async () => {
    await page.locator('.ant-tabs-tab', { hasText: '修改密码' }).click();
    await expect(page.getByPlaceholder('请输入旧密码')).toBeVisible();
    await expect(page.getByPlaceholder('请输入新密码')).toBeVisible();
    await expect(page.getByPlaceholder('请再次输入新密码')).toBeVisible();
  });
  await t.say(page, '改密码要先验旧密码，后端拿 BCrypt 哈希比对，通过才写新哈希并让当前会话继续有效；这里只展示表单，演示账号的密码不动。');
  await t.step(page, '回到「基本资料」页签。', async () => {
    await page.locator('.ant-tabs-tab', { hasText: '基本资料' }).click();
    await t.quiet(page, 800);
  });
});
