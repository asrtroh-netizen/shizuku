# 规则手册 R2（增补）· 在 `migration/RULEBOOK.md` v1.3 之上生效

v1（2026-09-06）。冲突时以本文件为准；未提及的沿用 v1.3（通道形状 §1、文案 §2、视觉 §3、状态 §4、权限 §5、所有权 §6、测试 §7、报告 §8）。

## §R1 共享 Kotlin 工具的精确形状（K2 创建；K1/K4 只引用，不创建）

```kotlin
package moe.shizuku.manager.flutter

// NotificationListenerAccess.kt —— 唯一实现，取自 PairingChannel（与已删 Activity 逐行同义）
internal object NotificationListenerAccess {
    /** 纯函数：enabled_notification_listeners 的 ":" 列表里是否含本包。flat 为 null/空 → false。 */
    fun flatContainsPackage(flat: String?, packageName: String): Boolean
    fun isEnabled(context: Context): Boolean          // Settings.Secure.getString + flatContainsPackage
    fun openSettings(activity: Activity)              // R+ 走 ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS(component)，否则 ACTION_NOTIFICATION_LISTENER_SETTINGS；双层 catch 回退（同 SettingsChannel.openNotificationAccess）
}

// LocaleLabels.kt —— 43 条表取自 SettingsChannel.localeLabel（逐字搬移）
internal object LocaleLabels {
    fun label(tag: String): String                    // 未知 tag 原样返回
    fun rows(tags: List<String>, currentTag: String, systemLabel: String): List<LocaleRow>  // 首项 SYSTEM 用 systemLabel
}
internal data class LocaleRow(val tag: String, val label: String, val selected: Boolean)   // 从 SettingsChannel.kt 搬到本文件

// BootPrefs.kt —— 从 SettingsChannel.kt 逐字搬移（含 KDoc）
internal data class BootPrefs(val bootRoot: Boolean, val bootWireless: Boolean)
internal fun applyBootToggle(current: BootPrefs, key: String, checked: Boolean): BootPrefs
internal fun parseTcpipPort(raw: String): Int?

// HtmlText.kt
internal fun htmlToPlainText(html: String): String    // HtmlCompat.fromHtml(html).toString().replace(Regex("\\s+"), " ").trim()
```

- 搬移 = 剪切粘贴，不改逻辑、不改常量、不"顺手"改名参数。`SettingsChannelTest.kt` 引用的 `BootPrefs / applyBootToggle / parseTcpipPort / localeLabel` 名字：`localeLabel` 搬进 `LocaleLabels.label` 后，测试文件里对 `localeLabel(...)` 的调用需同步改为 `LocaleLabels.label(...)`——**这属 K2 所有权**（`SettingsChannelTest.kt` 归 K2 只为改这一处调用名，期望值不变）。
- 现有 `isNotificationListenerEnabled` 两份实现差在 `flat != null`（Pairing）与 `isNullOrEmpty`（Settings）；统一取 `flat != null`（与已删 Activity 一致，且空串 split 后无有效 ComponentName，行为等价）。

## §R2 HomeChannel（K1）

- `class HomeChannel(private val activity: Activity, private val scope: CoroutineScope, private val actions: HomeActions)`；`fun register(messenger: BinaryMessenger)`；`fun emitChanged(dartExecuting: Boolean, hostResumed: Boolean)`（内部用 `shouldEmitHomeEvent(dartExecuting, hostResumed, triggeredByHostResume = false)`，主线程投递，`eventSink?.success("changed")`）；`companion { CHANNEL = "shizuku/home"; EVENTS = "shizuku/home/events" }`。
- `when` 分支逐条从 `FlutterHostActivity.configureFlutterEngine` 搬入：方法名、参数名、`result.error(<方法名>, ...)`、`recreate()` 时机（`toggleTheme` / `setLocale` 先 `result.success(ok())` 再 `activity.recreate()`）、`setBootWireless` 的 `extra.put("state", snapshot)` 全部不变。
- `emitChanged` 由宿主的 binder 监听器调用（协调者接线）；`isDestroyed` 守卫保留在 HomeChannel 内。
- `HomeActions.handleStartViaWadbIntent(intent)` 改为 `fun handleStartViaWadb(requested: Boolean)`；宿主传 `intent?.getBooleanExtra(EXTRA_START_SERVICE_VIA_WADB, false) == true`。此后 `HomeActions.kt` 不得 import `FlutterHostActivity` / `HomeChannel`（切断对宿主的反向依赖）；引用同包共享工具 `LocaleLabels / LocaleRow / BootPrefs / applyBootToggle` 是允许的（v1.2 措辞修正）。

## §R3 HomeState 纯函数（K1）

```kotlin
package moe.shizuku.manager.home

internal data class HomeFacts(
    val running: Boolean, val uid: Int, val permission: Boolean, val grantedCount: Int, val rooted: Boolean,
    val sdkAtLeastR: Boolean, val adbTcpPort: Int, val bootRoot: Boolean, val bootWireless: Boolean,
    val watchdog: Boolean, val dark: Boolean, val adbCommand: String,
)
internal data class HomeTexts(val appsWaiting: String, val appsUnavailable: String, val appsCount: String, val rootUnavailable: String, val rootRestart: String, val rootStart: String)
internal fun buildHomeStateMap(facts: HomeFacts, texts: HomeTexts, locales: List<Map<String, Any?>>, copy: Map<String, Any?>): Map<String, Any?>
```
- 字段与派生规则逐字同现 `snapshot()`：`state = if running "ready" else "inactive"`、`rootRestart = running && uid == 0`、`showWireless = sdkAtLeastR || adbTcpPort > 0`、`showPair = sdkAtLeastR`、`adbLimited = running && !permission`、`appsSub`/`rootSub` 三分支。字段名一个都不能改（Dart `HomeSnapshot.fromJson` 依赖）。
- `HomeActions.snapshot()` 变成"采集 facts → `buildHomeStateMap` → `JSONObject(map)`"；`copyJson()` 保留（纯字符串查表，不抽）。

## §R4 Dart 共享组件（D1 创建；D2/协调者采用）

```dart
// lib/widgets/glass_alert.dart
class GlassAlert extends StatelessWidget {
  const GlassAlert({super.key, required this.title, required this.body, required this.confirmLabel,
    this.cancelLabel, this.extraLabel, this.monospace = false, this.tone = GlassAlertTone.neutral,
    this.icon});
  // 返回值约定与现有三份一致：confirm → pop(true)；cancel → pop(false)；extra → pop('extra')（若现有实现用别的哨兵值，以 home 版为准并在 KDoc 写明）
}
enum GlassAlertTone { neutral, error }   // error = errorContainer 底 + onErrorContainer 字（apps 的 ADB 受限、settings 的缺权限）

// lib/widgets/glass_notice_card.dart
class GlassNoticeCard extends StatelessWidget {   // 一张 GlassPanel + 左图标 + 文案；apps 的 _NoticeCard、app_shell 的 _PlaceholderTab、pairing 的占位统一用它
  const GlassNoticeCard({super.key, required this.icon, required this.text, this.title});
}
```
- D1 先读三份 `_GlassAlert`（home / apps / settings）取**并集**设计 API，保证三处调用改写后视觉与返回值不变；`showDialog` 的 `barrierColor` / `GlassDialogBackdrop` 用法沿用 home 版。
- 采用方（D2）只做"删私有类 + 改构造调用"，不得改弹窗文案、按钮顺序、返回值处理。

## §R5 首页拆分（D1）

- 文件与归属：`home_header.dart`（`HomeTopCapsules`、`HomeLangChip`、`HomeThemeSlide`）、`home_cards.dart`（`HomeActionCard`、`HomeQuickGrid`、`HomeQuickTile`、`HomeLimitedBanner`）、`home_dialogs.dart`（`HomeBootDialog`、`showHomeLanguageSheet`）。命名规则：去掉下划线、加 `Home` 前缀；**不用** `part / part of`。
- `HomeScreen` 构造参数：`onOpenApps`、`onOpenTerminal`、`onOpenPairing` 改为 `required VoidCallback`；删除 `onOpenSettings`（无调用点）；删除三处 `_call('open…')` 回退分支（Kotlin 路由已在 P4-1 删除）。
- 底部留白：`ListView.padding.bottom` 改为 `glassDockScrollPadding(context)`，并把 `SafeArea` 改成 `SafeArea(bottom: false)`（与 apps/settings/terminal 三页一致；改进 I-1）。
- 其它行为、文案、图标、动画时长一律不变（`ThemeSlide` 220ms 等）。
- `home_hero_face_test.dart` 构造 `HomeScreen()` 处补三个空回调 `() {}`；`widget_test.dart` 经 `ShizukuFlutterApp` 不需改。

## §R6 死代码与依赖（K3）

- 删除前必须逐个 `rg` 核查零引用并把命令与结果写进报告；删除后 `rg` 复查。
- `manager/build.gradle` 只删这些行：`libs.androidx.compose.runtime.livedata`、`libs.androidx.compose.material.icons.extended`、`libs.androidx.compose.ui.tooling.preview`、`debugImplementation libs.androidx.compose.ui.tooling`；**保留** `compose.bom / activity.compose / compose.ui / material3`（授权弹窗与 legacy 页仍是 Compose）、全部 rikka / lifecycle / fragment 依赖（`StarterActivity`、`AppActivity` 基类链仍用；不确定的一律不删并记 OBSERVATIONS）。
- `libs.versions.toml` 不改。

## §R7 onetools 回同步（D3）

- `one_status_hero.dart`：以 Ultra 为准整文件替换，只换 import 前缀（`package:oneims_flutter/` → `package:manager_flutter/`）；完成后用 `Compare-Object`（去 CR、统一前缀）证明零差异。新增的 `secondaryActionLabel / onSecondaryAction` 为可选参数，首页不传，视觉不变。
- `dot_matrix_face.dart`：内容已一致，只按 Ultra 版重写一遍保证逐字节（LF）相同。
- `home_hero_face_test.dart` 必须不改而全绿。

## §R8 测试

- `HomeStateTest`（K1）：`rootRestart` 四象限、`showWireless` 三情形、`adbLimited`、`appsSub` 三分支、`state` 字符串、字段名精确集合、`locales`/`copy` 透传。
- `SharedHelpersTest`（K2）：`flatContainsPackage`（null / 空 / 含本包 / 只含他包 / 畸形项）、`LocaleLabels.label`（`zh-CN` / `de` / 未知 tag）、`LocaleLabels.rows` 首项与 selected、`applyBootToggle` 与 `parseTcpipPort` 原有用例可从 `SettingsChannelTest` 保留在原处（不重复）。
- `home_screen_test.dart`（D1）：无宿主渲染；点"应用"磁贴触发 `onOpenApps`（而非通道）；点"配对"触发 `onOpenPairing`；ListView 底部 padding ≥ `Glass.dockHeight`；`adbLimited` 时 `HomeLimitedBanner` 出现。

## 口令

**剪切粘贴不改逻辑；共享文件只准建六个；首页拆文件不拆行为；删代码先 rg 后删再 rg；一切顺手改进进 OBSERVATIONS。**
