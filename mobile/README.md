# mobile/ — ClojureDart 移动端

需要 App(iOS / Android / macOS / Windows / Linux / Web)时用这一份模板。
它用 [ClojureDart](https://github.com/tensegritics/ClojureDart) 把 Clojure 编译成 Dart,
界面是 Flutter Material,状态管理是 [re-dash](https://github.com/hti/re-dash)(re-frame 的 ClojureDart 移植),
打的接口与网页端**完全同一套**(`{:code :msg :data}`、`page`/`size`、JWT + `sys_online` 会话)。

> 这一份是**操作手册**(命令、目录、踩坑清单)。它在整体设计里的位置看架构文档:
> [`docs/architecture/c4-model.html`](../docs/architecture/c4-model.html) 的 §2/§3(作为容器的移动端)、
> §5.3–§5.5(组件图与设计点)、§7.5(登录→取列表→会话失效)、§8.3(构建与注入)、§9.8(两端共用契约)、
> §11(门禁为什么单独一个 job)、ADR-013(选型与被放弃的方案)、§13 #20–#26(移动端已知缺口)、#27(文档同步没有门禁)。

## 这是一个独立工程

| | 后端 / 网页端 | 移动端 |
|---|---|---|
| 依赖表 | 根 `deps.edn`、`shadow-cljs.edn`、`package.json` | `mobile/deps.edn`、`mobile/pubspec.yaml` |
| 工具链 | Clojure CLI + Node | Clojure CLI + **Flutter SDK** |
| 门禁 | `bb ci`(lint / fmt / test / test:cljs) | `bb mobile:compile` + `bb mobile:test`,CI 里是独立的 `mobile` job |

`mobile/` 不在根 `deps.edn` 的 `:paths` 里,所以 `bb test`、`bb uberjar`、Docker 镜像都不需要 Flutter;
反过来 `bb ci` 也不管移动端 —— 移动端改了要单独跑 `bb mobile:compile`。
规模约束(单文件 ≤500 行、单函数 ≤50 行)对 `.cljd` 一样生效(`bb check` 会检查 `mobile/src`、`mobile/test`)。

**`.cljd` 不进 clj-kondo**:clj-kondo 没有可靠的 ClojureDart 支持,`["package:http/http.dart" :as http]`、
`dart/is?`、`Duration`、`(.-statusCode resp)` 这类互操作写法会报十几个假错。
移动端的静态检查是 **ClojureDart 编译器本身**:`clj -M:cljd compile` 过了才算过
(注意它只保证能生成 Dart,类型真正的问题是 Flutter 编译期才炸,所以发布前跑一次 `bb mobile:run` 或 `flutter build`)。

## 目录

```
mobile/
├── deps.edn              # ClojureDart + re-dash;:cljd/opts {:kind :flutter :main <ns>.main}
├── pubspec.yaml          # Dart 依赖(package:http;flutter pub add 会自动加 dev 依赖 test)
├── lib/main.dart         # 入口垫片:export "cljd-out/<ns>/main.dart" show main;由 cljd init 生成
├── src/<app>/
│   ├── main.cljd         # 只有 runApp + MaterialApp,别放逻辑
│   ├── config.cljd       # 接口地址 / 超时 / 每页条数(--dart-define 注入)
│   ├── json.cljd         # JSON <-> Clojure 数据(键转关键字)
│   ├── api.cljd          # 唯一的后端出口:带令牌、解信封、把失败归类
│   ├── fx.cljd           # re-dash 的自定义效果 :app/request(请求 → 成功/失败事件)
│   ├── model.cljd        # app-db 初值 + register!(装配 fx / events / subs)
│   ├── events.cljd       # 唯一改 app-db 的地方
│   ├── subs.cljd         # 唯一读 app-db 的地方(派生值在这里算,视图不写算法)
│   ├── theme.cljd        # 配色
│   └── views/            # login.cljd / posts.cljd / shell.cljd(页面 = 订阅 + dispatch)
└── test/<app>/           # cljd.test 单元测试(json 换算、事件与订阅),bb mobile:test
```

命名空间分层与网页端一一对应(`api` ↔ `frontend/api/transport`、`events` ↔ `events/`、`subs` ↔ `subs/`),
所以同一份后端契约两边读同一套文档。**新增一个移动端页面**照这个顺序改:
`config`(如需新参数)→ `events`(取数事件)→ `subs`(订阅)→ `views/<页>.cljd` → `shell.cljd` 挂进路由。
`bb new-module` 目前只生成后端 + 网页端;移动端每个模块的页面按上面的分层手写。

## 环境准备

```bash
bb mobile:doctor      # 看缺什么:flutter / clojure / bb / 平台工程目录 / macOS 网络权限
bb mobile:create      # flutter create 生成平台工程目录(默认 macos,可加 android ios …)+ cljd init
bb mobile:compile     # 编译(= 移动端的静态检查)
bb mobile:run         # 编译并前台运行,改 .cljd 自动重编译 + 热重载,Ctrl-C 退出
bb mobile:test        # 单元测试(cljd.test,不需要后端在跑)
bb mobile:web         # 编网页版并部署到 resources/public/mobile(录屏/演示)
bb mobile:clean       # 清构建产物(平台工程目录保留)
```

- **跑单个测试文件**要给生成出来的 Dart 路径,并用 `--` 分隔:`bb mobile:test -- test/cljd-out/ruoyi/json-test_test.dart`。
  传命名空间或 `.cljd` 源路径都不行(ClojureDart 把位置参数当命名空间去 `src` 里找,报 `Could not locate …`)。

- 需要 **Flutter 3.35+**(与 CI 的 `flutter-version` 一致)。macOS 上 `brew install flutter` 会从源码编译,很慢,建议直接下官方 tarball 解压进 PATH。
- **平台工程目录(`macos/ android/ ios/ linux/ windows/ web/`)不进版本库**:`flutter create` 随时能重新生成,
  `bb mobile:create` 幂等补齐缺的那几个。真要改签名、包名、图标,再把对应目录从 `mobile/.gitignore` 里放出来。
- `bb mobile:run` 默认连本机后端 `http://127.0.0.1:3000/api`(`PORT` 环境变量可覆盖)。

## 连哪个后端:`--dart-define`,不要改源码

```bash
bb mobile:run                                                        # 默认 127.0.0.1:3000/api
MOBILE_DART_DEFINES="--dart-define=RUOYI_API_BASE_URL=http://192.168.1.10:3200/api" bb mobile:run
MOBILE_DART_DEFINES="--dart-define=RUOYI_API_BASE_URL=https://api.example.com/api" bb mobile:run
```

## 网页版与录屏:`bb mobile:web` / `bb video:mobile`

`bb mobile:web` 把 `mobile/src` 编成网页版并搬到 `resources/public/mobile/`(构建产物,已 gitignore),
由后端原样伺服 —— 和网页端同一个端口,不用另起服务。三个参数都是有原因的:

- `--no-web-resources-cdn` + `--dart-define=UseLocalCanvasKit=true`:没有外网的机器上 CanvasKit 走仓库里那份,不打 `www.gstatic.com`;
- `--base-href /mobile/`:产物挂在子目录,不然 `main.dart.js` 找不到;
- 接口地址默认**同源相对路径** `/api`(环境变量 `MOBILE_WEB_API_BASE` 可覆盖)。写成 `http://127.0.0.1:3000/api`
  会和页面所在 origin 不同,所有请求被 CORS 拦掉。

`bb video:mobile` 录四段分镜(功能 / 通讯方式 / 状态管理 / 主题设置),一条命令跑完:

1. `bb mobile:web` 重编网页版;
2. 在 `PORT=3210` 起一个**独立库**的后端(`target/mobile-demo.db`,每次删掉重建),写 20 条演示岗位,
   加上种子 4 条正好 24 条 —— 分镜里断言的「共 24 条 / 第 2 页第一条是 `post_id` 21」靠这份确定性数据成立,
   所以录制不打开发库,也不受库里已有的数据影响;
3. `npx playwright test -c playwright.mobile.config.js` 跑 `tests/e2e/mobile/*.spec.js`,一段一个 webm。
   画面是 1440x900 的舞台页(`tests/e2e/mobile/stage.html`),App 以真机尺寸 390x844 嵌在同域 iframe 里,
   台词、假鼠标、要点与代码卡都画在舞台上,所以字幕**烧在视频里**,不需要后期压制;
4. ffmpeg 拼接 + 内嵌章节 → `target/mobile-tour/mobile.mp4`,旁白与网页导览共用 `tasks.narrate`(MiMo TTS)。

`--compose-only` 用已有 webm 重合成(调节奏/字幕样式时用),`--no-narrate` 不出音轨。
移动端**不贴左侧目录带**(舞台自带),原片直接就是成片,少一次全片重编码。
旁白对得上画面的原理:`at` 是相对分镜卡的毫秒数,卡片前的登录/导航空白靠读画面亮度现场量出来 ——
舞台整体偏暗,所以这里的 `card-luma` 绑成 40(网页导览那条是 95)。

演示「刷新会丢状态」时,`mobile-helper.js/reload` **只刷 App 那层 iframe**:整页 reload 会把舞台自己的
目录高亮、右侧要点和右上角分镜号一起抹掉(实测刷完跳回「01 / 准备中」),观众以为换了分镜。

台词的时长(`speakable`)按实测留:MiMo「语速偏快」在这批台词上是 **100~180 毫秒一个字**
(标识符多的句子快、纯中文的慢),所以按 160 毫秒/字 + 600 毫秒起落留窗口,并且 `step` 会撑到这句念完
再切下一条字幕。留少了不会报错,只会在合成时被顶到最快速度(实测一版 39 句里 9 句顶到上限 1.3 倍,
念得喘不过气;按这套规则重录后 43 句全部 1.00 倍速、0 句被舍弃)。
代码卡上的每一行都带 `code` 标记,旁白**只念 `voice:` 那句人话** —— 逐字念 Clojure/JSON 既听不懂又占满时间。

## 通讯方式:为什么是 JSON 而不是 transit

实测过 transit(同一批 `/system/post` 列表响应):
- **体积**:transit-json 与现网 JSON 差 ~6%,列表越大差距越小,省不下有意义的流量。
- **依赖**:后端 *能* 协商 transit(`web.middleware.formats` 用的是 muuntaja 默认格式表,实测 `Accept: application/transit+json`
  会从 `/api/health` 回 transit 文本;`com.cognitect/transit-clj` 由 muuntaja 带进 classpath),但 **没有任何客户端使用它**,
  两条通道各写各的反而更容易漂移;真要统一到 JSON,可以按设计文档 §13 #25 把格式表收窄并去掉 `luminus-transit`。
- **收益**:transit 的价值是带类型标签(`:t`/`:r` 引用、instant、keyword 键)。
  后端返回的时间戳**已经**由 `infra.json` 统一成 `yyyy-MM-dd HH:mm:ss` 字符串,键是 snake_case 字符串,
  移动端拿到后由 `ruoyi.json/->edn` 一次性转成关键字 —— 也就是说 transit 想解决的问题在这套契约里已经解决了。
- **ClojureDart 侧**:没有 transit 库,得自己写 Dart 侧的 reader;而 `dart:convert` + 一层 `->edn` 是 40 行、有单元测试的代码。

结论:**JSON + 客户端关键字化**。要压缩优先上 gzip(服务端做,两端零改动)。

## 状态管理:re-dash 的用法约定

- app-db 只有 `model.cljd/initial-db` 这一份;改它只走事件,读它只走订阅。
- **所有 `reg-event-* / reg-sub / reg-fx` 必须写在会被调用的 `register!` 里**。
  Dart 编译器会树摇:写在顶层的注册代码从没被调用过就直接被删掉,表现是
  「Event not found」而不是编译错误。
- re-dash 只认内置效果 `:db :dispatch :dispatch-later :fx :deregister-event-handler`,
  **没有** `:dispatch-n`;一次事件只派发一条,要连着做两件事就串事件链
  (`:auth/logged-in → :app/session-opened → :profile/load → :posts/load`)。
- 接口调用是自定义效果 `:app/request`(`fx.cljd`):事件只声明
  `{request … :ok … :bad …}`,发请求、解信封、401 归到 `:auth/session-expired` 都在效果层,
  页面不需要自己处理登录失效 —— 与网页端 `api.transport` 同一套纪律。
- 计时(提示条 3 秒自动收)用 `:dispatch-later`,不要在 widget 里自己 `Timer`。

## ClojureDart 的坑(都踩过,写代码前先扫一遍)

1. **关键字读不到 Dart 对象的字段**。`(:status resp)` 永远是 `nil`;要写 `(.-statusCode resp)`,
   并且加类型提示 `^http/Response`,否则报 `DYNAMIC WARNING: can't resolve member status`。
   属性名以 Dart 侧为准(`http.BaseResponse` 上是 `statusCode`,不是 `status`)。
2. **函数内部一 `await` 就变成异步函数**,所有调用方都必须 `(await …)`。
   `api/call`、re-dash 的 `dispatch-sync` 都是这一类:漏掉 `await` 不报错,
   拿到的是 `Future`,解构出来全是 `nil`,表现是「界面永远转圈」「断言永远差一拍」。
3. **`await` 不能写在 `let` 的解构绑定里**。
   `(let [{:keys [a b]} (await f)] …)` 直接编译失败,而且报错位置指向入口命名空间的 `ns` 表单
   (`=== Faulty form === (ns …)`),很有迷惑性。写成
   `(let [result (await f) {:keys [a b]} result] …)`。
4. **`--dart-define` 只在 const 上下文生效**。ClojureDart 只把**键名为字面量**的那一层调用编成
   `const String.fromEnvironment("K")`;把键当参数传进自己的函数里,运行期会静默用回默认值。
   见 `config.cljd` 的写法。
5. **`try` 里可以 `await`,但 `catch` 必须写类型**;catch-all 用 `(catch dynamic _e …)`。
   `dart/is?` 只认字面量类型,不能拿它分辨异常类,超时与 DNS 失败只能一起归类。
6. **可空的 Dart 回调(`VoidCallback?`)要由 `defn` 返回**,不要在表单里内联 `(if … nil …)`,
   类型推不出来。
7. **ClojureDart 没有 `Math/ceil`、`instance?`、部分 `clojure.core`**;
   整数上取整改写 `(int (/ (+ total size -1) size))`(见 `subs.cljd`)。
8. **macOS 沙箱默认拦出站网络**:`flutter create` 的模板没给 `com.apple.security.network.client`,
   于是每个请求都是 `SocketException … Operation not permitted, errno = 1`,
   而且只在桌面/真机构建里出现(`flutter run -d chrome` 看不出来)。
   `bb mobile:create` 会幂等地把这条权限补进 `macos/Runner/*.entitlements`,`bb mobile:doctor` 会检查。
9. **同一时间只能有一个 ClojureDart 进程**(`REPL.lock`);报「Another ClojureDart process is running」时
   按它给的 PID 杀 `java` 进程,`pkill -f 'clj -M:cljd'` 匹配不到。
10. **无头环境怎么验证界面**:用 `clj -M:cljd flutter` 起的 Dart VM service 调
    `ext.flutter.debugDumpApp` 打印 widget 树(截图/键鼠在 CI、容器与远程机器上通常不可用)。
11. **`test/cljd-out/` 里留着的旧用例会被重复跑**:`flutter test` 扫的是这个目录,不是 `.cljd` 源码。
    `bb rename` 之后要先 `bb mobile:clean`(实测:改名后旧名字的一套 9 个用例还在,`bb mobile:test` 报 +18)。
12. **hiccup 向量里调用子组件要再套一层括号**。`[(toggle-button dark?)]` 才对,
    写 `[toggle-button dark?]` 是把「函数本身」和「它的参数」当成两个 children 塞进向量,
    编译照样通过,渲染时 Dart 才说拿到了非 Widget(见 `views/shell.cljd` 的注释)。
13. **`flutter create` 附带的 `test/widget_test.dart` 会让 `bb mobile:test` 必挂**:那是计数器示例,
    import 的 `MyApp` 在本工程里不存在(`lib/main.dart` 只 export `<ns>.main/main`)。`bb mobile:create` 生成平台目录后顺手删掉它。
14. **跑过 `bb mobile:test` 之后不能直接 `flutter build web`**:测试会在 `lib/cljd-out` 里留下测试命名空间的 import,
   构建报 `Error when reading 'lib/test/cljd-out/…_test.dart'`。`bb mobile:web` 先清 `lib/cljd-out` 与 `test/cljd-out` 再编。

## 改名

`bb rename <命名空间> <项目名>` 会一并改写 `mobile/pubspec.yaml`(`name: <项目名>_mobile`)、
`mobile/deps.edn` 的 `:cljd/opts {:main …}`、`mobile/lib/main.dart` 的 export、
`mobile/src/<旧名>/*.cljd` 里的命名空间,并把
`mobile/src/<旧名>`、`mobile/test/<旧名>` 两个目录搬到新名字;
平台工程目录不用管,`bb mobile:create` 会按新的 `kit.edn` 重新生成。
改名后先 `bb mobile:clean` 再 `bb mobile:compile`、`bb mobile:test`(见坑 11)。

