# Gap Inventory · 默认规则盖不住、需要人为决策的硬点

版本 v1（与 SPEC / RULEBOOK v1 同冻结）。每条给出：现象 → 决策 → 归属分片。

| # | 现象 | 决策（已定） | 分片 |
|---|---|---|---|
| GAP-1 | Ultra 的 `glass_dock.dart` 依赖 `liquid_glass.dart`，后者依赖 Ultra 新版 `glass.dart` 的 `blurLiquid / liquidRefraction / liquidFill / liquidHighlight / liquidBackdropFilter`；本仓库 `glass.dart` 没有这些成员 | **加性移植**：只把这 5 个成员逐字节复制进本仓库 `glass.dart`，不动既有成员。Wave 1 结果：移植后本仓库 `glass.dart` 与 Ultra `glass.dart` SHA256 一致（原本只差这 5 个成员）；`one_status_hero / dot_matrix_face` 仍漂移，记 OBSERVATIONS 另立项 | A（已完成） |
| GAP-14 | `GlassPanel` 内含 `BackdropFilter`，规则手册 v1 误把它当通用卡片；应用列表每行一个会拖垮滚动 | v1.1：列表行一律主题 `Card`；`GlassPanel` 只给页面级少量面。应用 Tab 列表行按新规则重生成 | B |
| GAP-2 | 底栏 4 个标签没有现成 `copy` key；`res/values` 禁改 | 复用既有 `R.string`：`app_name` / `home_app_management_title` / `home_terminal_title_plain` / `settings_title`，通过 `HomeActions.copyJson()` 加 4 行下发（RULEBOOK §3.7）。英文 "Application management" 偏长，`NavigationDestination` 交给 M3 自行省略；记 OBSERVATIONS 建议后续单独加短 key | A |
| GAP-3 | `AppsManagementActivity` 在 binder 死 / 未运行时直接 `finish()`；Tab 无法 finish | Tab 内渲染"服务未运行"占位卡（文案 `home_status_service_not_running(app_name)`），列表清空、开关不可用；binder 恢复（首页事件 / resumed）后重拉。属**显式声明的行为差异**，写入 SPEC §6 | B |
| GAP-4 | Compose 用 `AppIconCache` 直接拿 `Bitmap`；Flutter 拿不到 Android Bitmap | 按需 `getIcon` 返回 base64 PNG（RULEBOOK §9）；Dart 端按 `packageName#uid` 缓存 `Uint8List`；失败用 `Icons.android_outlined`。不在 `getState` 里内联图标（避免大 JSON） | B |
| GAP-5 | `ShellTutorialActivity` 用 `registerForActivityResult(OpenDocumentTree())`；但宿主 `FlutterActivity` 继承 `android.app.Activity`（`javap` 核实引擎 jar），没有该 API（v1 假设错误） | v1.2：`TerminalChannel` 用 `startActivityForResult(OpenDocumentTree().createIntent(...), 0x5254)` + 公开钩子 `onActivityResult`，宿主覆写 `onActivityResult` 先 `super` 再转发；通道实例保存为宿主字段以承接进程重建后的结果派发。选择器行为与 launcher 版一致（同一 contract 的 `createIntent/parseResult`） | D / 协调者 |
| GAP-15 | 被 `AppShell` 用 `ValueKey` 重建的 Tab 若订阅 EventChannel：新页先 listen、旧页后 cancel，平台侧 `IncomingStreamRequestHandler.onCancel` 会把新 sink 置空，事件静默丢失 | v1.2：应用 / 终端 / 设置三个 Tab 一律不用 EventChannel（RULEBOOK §1.1）；终端页据此去掉 `shizuku/terminal/events`。配对页是推入页，push/pop 串行，不受影响 | D / 协调者 |
| GAP-16 | Compose 设置页 TCP 端口合法区间是 10..65535（`dialog_adb_invalid_port` 原文），规则手册 v1 误写 1..65535；且 SDK<30 非 TV 非 root 时隐藏四个启动开关 | v1.2：按源码取 10..65535；快照加 `supportsStartOnBoot` | C |
| GAP-6 | `AdbPairingTutorialActivity` 是 194 行状态机（权限请求、前台服务失败回退、`onResume` 条件补启动） | 单独 Wave 3；`PairingChannel` 复刻全部私有逻辑（RULEBOOK §6.4 允许临时重复）；宿主需转发 `onResume` 与 `onRequestPermissionsResult(requestCode=1001)`，由协调者接线；页面用 `Navigator.push` 进入，不是 Tab | E / 协调者 |
| GAP-7 | 语言 / 夜间模式 / 主题类开关在 Compose 侧改后 `recreate()`；Flutter 宿主 `recreate()` 会重建整个 FlutterActivity（当前 Tab 丢失） | 接受：与现状 `toggleTheme` / `setLocale` 行为一致。协调者在 `FlutterHostActivity` 用 `intent.putExtra("tab", index)` + `AppShell(initialIndex)` 保留当前 Tab（Wave 2 接线时做，属显式改进，需单独验证） | 协调者 |
| GAP-8 | `SettingsActivity` 挂着 `android.intent.action.APPLICATION_PREFERENCES` intent-filter（系统"应用信息 → 设置"入口） | P4 前不动；P4 时把该 intent-filter 挂到 `FlutterHostActivity` 并携带 `tab=3`。列入 P4 待批清单 | P4 |
| GAP-9 | `MainActivity` 被 `LegacyIsNotSupportedActivity` 显式启动；`HomeActivity.EXTRA_START_SERVICE_VIA_WADB` 被 `AdbPairingService` 与 `HomeActions` 引用 | P4 时：`LegacyIsNotSupportedActivity` 改指向 `FlutterHostActivity`；常量迁到 `FlutterHostActivity.companion`，字符串值 `"moe.shizuku.manager.extra.START_SERVICE_VIA_WADB"` 不变。列入 P4 待批清单 | P4 |
| GAP-10 | Watchdog 开关语义不一致：`HomeActions.setWatchdog` 只看 `pingBinder()`，`SettingsComposeScreen` 还要求 `lastLaunchMode == ADB` | 各面保持各自现状（首页走 HomeActions，设置 Tab 走 Settings 语义）；不统一。记 OBSERVATIONS 供产品决定 | C |
| GAP-11 | `ShizukuComposeTheme.kt` / `Type.kt` 仍被授权弹窗（`RequestPermissionComposeScreen`）与 `LegacyNoticeComposeScreen` 使用 | P4 **不删**主题文件，只删页面；`DotMatrixFace.kt` 仅被 `LibrarySkinHome` 用，随之删 | P4 |
| GAP-12 | 本会话无真机；P1 的"点阵笑/哭与无线/开机仍待装包确认" | 真机验收作为 P3→P4 的硬门禁写入 SPEC §4；本轮不宣称真机行为 | 协调者 |
| GAP-13 | Ultra `glass_choice_dialog.dart` 引用 `l10n.exclClose`（Ultra 本地化） | 复制时把该处改为构造参数 `closeTooltip`（默认 `'Close'`），其余逐字节不改；调用方传 `copy.cancel` 或 `copy.ok` | A |
