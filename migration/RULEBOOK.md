# 规则手册（RULEBOOK）· Compose → Flutter 皮肤迁移 P2–P4

版本 v1（与 `MIGRATION_SPEC.md` v1 同冻结）。元规则：**两个实现者可能各选一边的地方，都写在这里**。审查引用格式：「违反 RULEBOOK §X.Y」。

---

## §1 通道总则（Kotlin ⇄ Dart）

1.1 每个页面一条 `MethodChannel`，名字固定：`shizuku/apps`、`shizuku/settings`、`shizuku/terminal`、`shizuku/pairing`；需要主动推送的页面再加一条 `EventChannel`，名字 = 方法通道名 + `/events`（例 `shizuku/pairing/events`），事件载荷固定字符串 `"changed"`，Dart 收到后**自己拉 `getState`**，不在事件里传状态。**限制（v1.2）**：只有"推入页"（`Navigator.push`，如配对页）或常驻页（首页）可用 EventChannel；被 `AppShell` 用 `ValueKey` 重建的 Tab（应用 / 终端 / 设置）**一律不用** EventChannel——新页 listen 后旧页 cancel 会把平台侧 sink 置空，事件静默丢失。
1.2 编解码：默认 `StandardMethodCodec`；**每个方法返回 JSON 字符串**（`String`），不是 Map。参数用 `Map<String, Any?>`（`call.argument<T>("key")`）。
1.3 成功统一含 `"ok": true`；失败走 `result.error(<方法名>, message, null)`（错误码 = 方法名，与 `FlutterHostActivity` 现状一致）。未知方法 `result.notImplemented()`。
1.4 每个通道的 `getState` 返回**整页快照**：状态字段 + `"copy": {...}`（全部界面文案，来自 `R.string`）。写操作（toggle / set*）完成后**直接返回新的整页快照**（同 `getState` 结构，可附加结果字段），Dart 直接用它 `setState`，避免二次往返。
1.5 线程：可能阻塞（binder / 包管理 / 文件 / 网络）的方法在 `Dispatchers.IO` 执行、`withContext(Dispatchers.Main)` 回复；纯偏好读写可直接在主线程 `runCatching`。不得在主线程调 `AuthorizationManager.getPackages()`、`AppIconCache`、`DocumentsContract`。
1.6 Kotlin 通道类形状（禁止再抽 BaseChannel）：

```kotlin
package moe.shizuku.manager.flutter

class XxxChannel(private val activity: <Activity 或 ComponentActivity>, private val scope: CoroutineScope) {
    fun register(messenger: BinaryMessenger) { MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result -> ... } }
    companion object { const val CHANNEL = "shizuku/xxx"; /* 可选 */ const val EVENTS = "shizuku/xxx/events" }
}
```
构造与注册由协调者在 `FlutterHostActivity.configureFlutterEngine` 里做；分片**不改** `FlutterHostActivity.kt`。需要生命周期钩子（onResume / onRequestPermissionsResult / onActivityResult）的通道，暴露公开方法（如 `fun onHostResumed()`），由协调者接线。**宿主事实（v1.2）**：`FlutterActivity` 继承 `android.app.Activity`（非 `ComponentActivity`），没有 `registerForActivityResult`；需要系统选择器/权限结果的通道用 `activity.startActivityForResult(...)` / `activity.requestPermissions(...)` + 公开钩子接收。请求码在各通道 `companion` 公开且宿主内唯一：终端 `0x5254`、配对 `1001`。
1.7 Dart 通道文件形状（每页一份，不共享）：复制 `home/home_channel.dart` 的模式——`static const MethodChannel _method = MethodChannel('shizuku/xxx')`；`_invokeMap` 把 JSON 字符串解成 `Map<String, dynamic>`；`PlatformException` / `MissingPluginException` → `{'ok': false, 'unavailable': true}`；空串 → `{}`。
1.8 纯 JSON 组装逻辑在 Kotlin 侧抽成**顶层纯函数**（输入普通数据类，输出 `Map<String, Any?>`），放在同一个 `XxxChannel.kt` 里，供 JUnit 直接测试（参照 `GrantStates.kt` + `GrantStatesTest.kt` 风格：`internal`、**零 Android 依赖**）。注意：JVM 单测里 `android.jar` 的 `org.json.JSONObject` 是 Stub（会抛 "Stub!"），所以纯函数**不得**碰 `org.json` / `android.*`；通道类再用 `JSONObject(map)` 序列化成字符串。

## §2 文案与本地化

2.1 所有用户可见文案由 Kotlin `activity.getString(R.string.xxx)` 下发到 `copy`；Dart 模型必须提供英文 `fallback`（同 `HomeCopy.fallback`），无宿主时用 fallback，`flutter test` 才能跑。
2.2 **不新增** `res/values/strings.xml` key。找不到完全对应的 key 时：先用 Compose 页当前用的 key（它们都存在），再找语义最近的既有 key，并在 `migration/OBSERVATIONS.md` 记一条。
2.3 语言/主题切换后宿主 `recreate()`（与 `toggleTheme` / `setLocale` 现状一致），Dart 不做本地 i18n。

## §3 视觉与组件

3.1 只用 `Glass` 令牌与既有组件：`GlassPanel`（**页面级少量面**：门面、动作卡、空状态/占位卡，一页 ≤ 4 张）、主题 `Card`（**列表项 / 重复行**：`Glass.apply` 已给 `Card` 半透明填充 + 发丝边，无模糊）、`GlassSheet` / `GlassDialogBackdrop`（弹层）、`GlassSelectTile` / `GlassOption`（单选）、`OneStatusHero`（仅首页）。页面**禁止**私写 hex 颜色、禁止自定义圆角/间距数值；页边距 `Glass.pageMargin`、卡间距 `Glass.cardGap`、卡内边距 `Glass.padCard`。
3.2 **列表项一律用主题 `Card`（或 `Card` + `ListTile`），不得用 `GlassPanel`**，也不得额外包 `BackdropFilter` / `LiquidGlass`——`GlassPanel` 内部就是 `BackdropFilter`，每行一个会拖垮滚动性能（`glass.dart` 文件头与 `GlassPanel` 注释均明示）。真模糊只给底栏（`GlassDock`）、门面（`OneStatusHero`）与页面级少量 `GlassPanel`。（v1.1 修订：Wave 1 试点暴露；应用 Tab 列表行据此重生成）
3.3 从 Ultra 复制文件的规则：`import 'package:oneims_flutter/...'` → `import 'package:manager_flutter/...'`，其余**逐字节不改**；例外只允许 GAP 明示的（`glass_choice_dialog.dart` 的 `l10n.exclClose` → 构造参数 `closeTooltip`，默认 `'Close'`）。
3.4 `glass.dart` 只允许**加性**移植 Ultra 的 `blurLiquid`、`liquidRefraction`、`liquidFill`、`liquidHighlight`、`liquidBackdropFilter`（值与实现逐字节同 Ultra），不得改动任何既有成员。
3.5 选择类交互（语言、夜间模式）用 `GlassChoiceDialog` + `GlassChoiceWell` 或既有 `GlassSelectTile`；确认类（ADB 受限、缺权限）沿用 `home_screen.dart` 的 `_GlassAlert` 模式（在自己文件内复制一份私有实现，不跨文件共享私有类）。
3.6 图标用 `Icons.*_outlined`；空状态 = 一张 `GlassPanel` 卡 + `Icons.info_outline` + 文案（对齐 Compose `EmptyState`）。
3.7 底栏 4 项固定顺序与图标：首页 `Icons.home_outlined`、应用 `Icons.apps_outlined`、终端 `Icons.terminal_outlined`、设置 `Icons.settings_outlined`。标签文案来自首页快照 `copy` 新增的 4 个 key（GAP-2）：`tabHome=app_name`、`tabApps=home_app_management_title`、`tabTerminal=home_terminal_title_plain`、`tabSettings=settings_title`；Kotlin 侧只在 `HomeActions.copyJson()` 末尾**加 4 行 `.put`**（不改其它任何行），Dart 侧 `HomeCopy` 加 4 个字段 + fallback（`'Shizuku'`、`'Apps'`、`'Terminal'`、`'Settings'`）。`NavigationDestination.label` 用这些字符串，不硬编码中文/英文。

## §4 状态与生命周期（Dart）

4.1 每个页面是 `StatefulWidget`，`initState` 拉一次 `getState`；实现 `WidgetsBindingObserver.didChangeAppLifecycleState(resumed)` → 重拉；有 EventChannel 的页面订阅 `"changed"` → 重拉。参考 `home_screen.dart` 的 `_refresh` / `_refreshSeq` 防乱序模式。
4.2 页面不持久化任何状态；真源永远是 Kotlin 快照。
4.3 `unavailable`（无宿主）时页面必须仍能完整渲染 fallback 文案（Widget 测试就靠这条）。
4.4 Tab 切换用 `IndexedStack` 保活首页；`AppShell` 持 `selectedIndex`，向 `HomeScreen` 传 `onOpenApps / onOpenTerminal / onOpenSettings` 回调。`HomeScreen` 新增这三个**可选**构造参数，为 `null` 时退回现有 `_call('openApps')` 等通道调用（P1 行为不变；写法用 if/else，`void` 表达式不能作 `??` 左操作数——v1.1 修订）。非首页 Tab 在每次被选中时用递增 `ValueKey` 重建（见 §9 刷新时机）。首页目前没有"设置"按钮，`onOpenSettings` 仅作预留钩子；设置入口 = 底栏（v1.1 修订）。

## §5 权限与副作用（Kotlin）

5.1 授权切换：与 `AppsManagementActivity.onTogglePackage` 逐行同义——`granted → revoke`，否则 `grant`；成功后 `GrantedCountCache.value = -1`；`SecurityException` 时若 `Shizuku.getUid() != 0` 返回 `adbLimited`，否则视为 success；`getUid()` 抛异常视为 success。
5.2 设置副作用与 `SettingsComposeScreen` 逐项同义（Root/无线互斥；`BootCompleteReceiver` 组件开关 = 任一开机项开；无线开机缺 `WRITE_SECURE_SETTINGS` 不落盘只返回 `needGrant` + `grantCmd`；自动配对缺通知监听不落盘只返回 `needNotificationAccess`；Watchdog 开 = `lastLaunchMode == ADB && pingBinder()` 才 `start`，关 = `stop`；`TCPIP_PORT` 只接受 **10..65535** 的整数字符串或空（清除）——与源码及 `dialog_adb_invalid_port` 原文一致，v1.2 修订；设置页开关无线开机**不**调 `WifiReadyMonitor`（与 Compose 设置页现状一致，首页 `HomeActions` 会调，差异记 OBSERVATIONS）；SDK<30 且非 TV 且非 root 时四个启动开关隐藏，快照用 `supportsStartOnBoot` 表达）。
5.3 危险/系统跳转（开发者选项、通知设置、通知监听设置、外链）全部在 Kotlin 执行，Dart 只发方法名。
5.4 不得调用 `Shizuku.exit()`、不得改 `Starter`、不得碰 `AdbPairingService` 内部；配对页只允许 `startForegroundService(AdbPairingService.startIntent(activity))` 及其既有失败回退。

## §6 文件所有权与越界

6.1 每个分片只写 `MIGRATION_SPEC.md §2` 表中自己那一行的文件；读任何文件都可以。
6.2 分片不得改：`FlutterHostActivity.kt`、`main.dart`（分片 A 除外）、`app_shell.dart`（分片 A 创建，其后只有协调者改）、任何 Compose 文件、`res/**`、Gradle / pubspec、`migration/**`（分片只在最终报告里给建议，不直接写状态文件）。
6.3 分片不得运行 Gradle、`flutter pub *`、`flutter build`、`flutter clean`、任何 git 写命令。允许：`flutter analyze`、`flutter test test/<自己的文件>`。
6.4 临时重复：新通道类允许暂时复制旧 Activity 里的私有逻辑（如终端导出、配对状态同步），**不得**为了复用去改旧 Activity 或抽公共类；P4 删旧时重复自然消失。

## §7 测试要求

7.1 每个 Dart 页面：`test/<page>_screen_test.dart`，至少覆盖：(a) 空 Map（无宿主）能渲染 fallback；(b) 一份完整 JSON 能渲染出关键文本；(c) 一个"坏输入"断言（字段类型错/缺失时用默认值而不崩）。用 `TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler` 打桩通道。
7.2 每个 Kotlin 通道：`AppsChannelTest.kt` / `SettingsChannelTest.kt` 只测 §1.8 的纯函数（JSON 字段名、互斥逻辑、边界值），JUnit4，无 Robolectric。
7.3 不改既有测试的断言语义；`widget_test.dart` 若因根组件变 AppShell 需要调整，只改定位方式不改期望。

## §8 提交与报告

8.1 分片不提交 git。完成后报告：改动/新建文件清单（仓根相对）、跑过的命令与结果原文摘要、对 RULEBOOK 的疑问或发现的歧义（建议条款文字）、越界诱惑（想改但没改的地方）、需要协调者接线的确切代码片段（例：`AppsChannel(this, ioScope).register(messenger)`）。
8.2 任何"顺手修 bug / 优化 / 统一风格"一律不做，写进报告的 OBSERVATIONS 段。

## §9 Apps 通道契约（分片 B）

`shizuku/apps`
- `getState` → `{ok, running, adbLimited, apps:[{packageName, uid, userId, label, requiresRoot, granted}], copy:{title, empty, requiresRoot, adbLimitedTitle, adbLimitedMessage, notRunning, back, ok}}`
  - `running = Shizuku.pingBinder()`；`adbLimited = running && Shizuku.getUid() != 0`；未运行 → `apps = []`。
  - `label` 已按 Compose 规则拼好（他用户加 ` - <userName> (<userId>)`）。
  - copy key ↔ R.string：`title=home_app_management_title`、`empty=home_app_management_empty`、`requiresRoot=app_management_item_summary_requires_root`、`adbLimitedTitle=app_management_dialog_adb_is_limited_title`、`adbLimitedMessage=app_management_dialog_adb_is_limited_message`（带 `Helps.ADB.get()` 参数，且经 `HtmlCompat.fromHtml(...).toString()` 去标签、空白折叠）、`notRunning=home_status_service_not_running(app_name)`、`back=action_back`、`ok=android.R.string.ok`。
- `toggle {packageName:String, uid:Int}` → 整页快照 + `"result": "success" | "adbLimited"`。
- `getIcon {packageName, uid, sizePx:Int}` → `{ok:true, png:<base64 PNG>}`；找不到 → `{ok:false}`。用 `AppIconCache.getOrLoadBitmap(activity, applicationInfo, userId, sizePx)`；`applicationInfo` 从最近一次 `getState` 缓存的列表按 `packageName#uid` 找。Dart 侧用 `Image.memory` 并按 key 缓存，失败显示 `Icons.android_outlined`。
- 纯函数：`internal fun buildAppsStateJson(running: Boolean, adbLimited: Boolean, apps: List<AppRow>, copy: Map<String, Any?>): Map<String, Any?>`，`AppRow` 为普通数据类；通道类用 `JSONObject(map).toString()` 序列化（与 §1.8 一致，v1.1 修订）。
- 刷新时机（GAP-3，v1.1 修订）：`shizuku/apps` **不设** EventChannel（同名 `EventChannel` 二次 `receiveBroadcastStream()` 会顶掉首页的 sink）。`AppShell` 在每次切到应用 Tab 时用递增 `ValueKey` 重建该 Tab（等价于 Compose 每次打开 `AppsManagementActivity` 都重新加载），加上 `resumed` 重拉与 `toggle` 直接返回快照，三者共同保证新鲜度。

## §10 Settings 通道契约（分片 C）

`shizuku/settings`
- `getState` → `{ok, supportsStartOnBoot, bootRoot, bootWireless, autoPairing, watchdog, tcpipPort:String("" 表示未设), nightMode:Int, nightModeOptions:[{value:Int,label}], blackNightTheme, useSystemColor, locales:[{tag,label,selected}], translationUrl, copy:{...}}`（`supportsStartOnBoot` v1.2 新增，Dart 缺省视为 true）；copy 覆盖 `SettingsComposeScreen` 用到的全部标题/副标题/对话框文案（同 key 名用 R.string 名去前缀 `settings_` 的小驼峰，如 `startOnBoot`、`startOnBootWireless`、`autoPairing`、`watchdogAdb`、`tcpipPort`、`language`、`translationContributors`、`translation`、`userInterface`、`startup`、`darkTheme`、`blackNightTheme`、`useSystemColor`、`permissionMissing`、`wirelessBootPermissionTooltip`、`manual`、`cancel`、`ok`、`title`）。
- `setBool {key, checked}`：key ∈ {`KEEP_START_ON_BOOT`, `KEEP_START_ON_BOOT_WIRELESS`, `AUTO_PAIRING_ENABLED`, `WATCHDOG_ENABLED_ADB`, `ThemeHelper.KEY_BLACK_NIGHT_THEME`, `ThemeHelper.KEY_USE_SYSTEM_COLOR`} 的实际字符串值；返回整页快照 + 可选 `needGrant/grantCmd` 或 `needNotificationAccess`；需要重建宿主的项由通道自己在回复后 `activity.recreate()`（Dart 不用管——与 `toggleTheme` 一致）。
- **重建后回到设置 Tab（GAP-7 契约，v1.1）**：通道在调用 `activity.recreate()` **之前**执行 `activity.intent?.putExtra("moe.shizuku.manager.extra.TAB", 3)`；宿主（协调者接线）重写 `FlutterActivity.getInitialRoute()`，读该 extra 返回 `"/tab/<n>"`，Dart `main.dart` 解析 `defaultRouteName` 作为 `AppShell(initialIndex)`。key 字面量固定为上面这个字符串，分片不得自创常量名。
- 重建时机：为对齐 Compose `recreateAfterAnimation`（等开关动画 200ms），用 `Handler(Looper.getMainLooper()).postDelayed({ activity.recreate() }, 200)`，且必须在 `result.success(...)` 之后调度。
- `setTcpipPort {port:String}`、`setNightMode {mode:Int}`、`setLocale {tag}`、`openNotificationAccess`、`openTranslation`、`openWirelessGuide`（= `CustomTabsHelper.launchUrlOrCopy(activity, "https://shizuku.rikka.app/guide/setup/")`）、`copyText {text}`。
- 纯函数：`internal fun applyBootToggle(current: BootPrefs, key: String, checked: Boolean): BootPrefs`（互斥）、`internal fun parseTcpipPort(raw: String): Int?` 与 `internal fun buildSettingsStateMap(...)`（v1.2 统一命名）。

## §11 Terminal 通道契约（分片 D）

`shizuku/terminal`（v1.2 修订）
- `getState` → `{ok, shName:"rish", dexName:"rish_shizuku.dex", copy:{...}}`，copy 覆盖 `ShellTutorialComposeScreen` 的全部 `R.string`（`copy.open = action_open` 只作图标语义标签；"查看指南"由说明卡整卡点击触发，不另设按钮；Tab 无 AppBar，标题只渲染一次）。
- `exportFiles` → `activity.startActivityForResult(ActivityResultContracts.OpenDocumentTree().createIntent(activity, null), TerminalChannel.REQUEST_OPEN_DOCUMENT_TREE /* 0x5254 */)`，立即回 `{ok:true}`；通道暴露 `fun onActivityResult(requestCode, resultCode, data): Boolean`，宿主 `onActivityResult` 先 `super` 再转发；结果用 `OpenDocumentTree().parseResult` 解析。**不设 EventChannel**（§1.1 限制：本页会被 `ValueKey` 重建）。
- `openGuide` → `CustomTabsHelper.launchUrlOrCopy(activity, Helps.RISH.get())`。
- 导出实现逐行同 `ShellTutorialActivity.openDocumentsTree` 回调（删旧同名 → 从 assets 写入），在 `Dispatchers.IO` 执行；异常 `runCatching` 吞掉（Compose 在主线程会崩——声明的差异，记 SPEC §6）。
- 通道必须在 `configureFlutterEngine`（`onCreate` 期间）构造并**保存为宿主字段**：选择器打开期间进程被杀，系统重建后 `onActivityResult` 紧随 `onCreate` 派发，实例不在就丢结果。

## §12 Pairing 通道契约（分片 E，高风险）

`shizuku/pairing` + `shizuku/pairing/events`
- `getState` → `{ok, supported:(SDK>=R), notificationEnabled, notificationListenerEnabled, localNetworkPermissionGranted, pairingServiceStartFailed, autoPairingEnabled（= 偏好 AUTO_PAIRING_ENABLED，Dart 缺省 false）, showMiuiHint, copy:{...}}`；通知监听卡仅在 `autoPairingEnabled && !notificationListenerEnabled` 时显示（同 Compose，v1.3）。
- `start` = Activity `onCreate` 语义：进入前把状态重置为 `PairingTutorialState()` 默认值与 `localNetworkPermissionRequested=false`，再 `syncState(); startPairingIfReady()`（通道随宿主长存，不重置会把上次的 `pairingServiceStartFailed` 带进下次，v1.3）；`openDeveloperOptions`、`openNotificationOptions`、`openNotificationAccessSettings`、`requestLocalNetworkPermission`。
- **在场门（v1.3）**：`onHostResumed()` 仅在 `shizuku/pairing/events` 处于 listen 状态（`eventSink != null`）时执行同步与条件补启动；Dart 页面必须在 `initState` 先订阅事件流、`dispose` 取消。否则用户从未打开配对页，宿主回前台也会拉起配对前台服务。
- 线程（v1.3，§1.5 例外）：`syncState` 的轻量系统调用（NotificationManager / Settings.Secure / 权限检查）与状态机留在主线程，与 Activity 版一致并与生命周期钩子串行。
- 公开钩子：`fun onHostResumed()`（复刻 `AdbPairingTutorialActivity.onResume` 条件补启动）与 `fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray)`；状态变化后推 `"changed"`。权限请求用 `activity.requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK), 1001)`（`android.app.Activity` API 23+ 自带，宿主是 `FlutterActivity`，见 §1.6 宿主事实）。
- 配对页是**推入页**（`Navigator.push`，push/pop 串行），因此**允许** EventChannel `shizuku/pairing/events`（§1.1 限制不适用）。
- 进入方式（v1.2）：`HomeScreen` 新增可选参数 `onOpenPairing: VoidCallback?`，在现有 `_call('openPairing')` 调用点用 if/else 分流（非 null 调回调，null 走原通道——P1 行为不变）；此伴随修改归分片 E。`AppShell` 由协调者接线：`onOpenPairing: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PairingScreen()))`。Kotlin `HomeActions.openPairing` 保留不动。

## 口令

**通道返回 JSON 字符串、快照带 copy、IO 干活主线程回；只用 Glass 令牌不写 hex；复制 Ultra 只换 import；分片只碰自己的文件、不跑 Gradle、不提交；每页一份测试、坏输入必须红；顺手改进只记不做。**
