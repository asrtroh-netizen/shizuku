# 影响面地图 · Shizuku 管家壳 Compose → Flutter（P2–P4）

可行性：**GO**（见 `MIGRATION_FEASIBILITY.md`）。基线 `a10c72a`。

## 1. 现状快照（已核对源码）

### 已完成（P0/P1，不在本次范围）

- `manager/src/main/AndroidManifest.xml`：Launcher 是 `.flutter.FlutterHostActivity`；`.MainActivity`（`class MainActivity : HomeActivity()`）仍注册、无 intent-filter，作回滚。
- `FlutterHostActivity.kt`：注册 `shizuku/home`（MethodChannel）+ `shizuku/home/events`（EventChannel）；所有动作走 `HomeActions`；`replyAsync` 模式 = IO 干活、主线程回、错误码带方法名。
- `manager_flutter/lib/`：`main.dart`（`MaterialApp` → `HomeScreen`）、`home/{home_screen,home_channel,home_models}.dart`、`onetools/{glass,one_palette,one_status_hero,dot_matrix_face}.dart`、`theme/app_theme.dart`。
- 通道约定：方法返回 **JSON 字符串**；Dart 侧无宿主时读空 Map、写 `{'ok': false, 'unavailable': true}`；界面文案全部由 Kotlin `R.string` 经 `copy` 对象下发，Dart 有英文 fallback。

### 待迁移（本次范围）

| 期 | 现 Compose 真源 | 行数 | 逻辑真源（保留在 Kotlin） |
|---|---|---|---|
| P2 应用 | `management/AppsManagementActivity.kt` + `AppsManagementComposeScreen.kt` + `AppsViewModel.kt` | 88 + 323 + 46 | `AuthorizationManager.getPackages/granted/grant/revoke`、`GrantStates.kt`（`GrantedCountCache`、`packageGrantKey`）、`AppIconCache.getOrLoadBitmap`、`UserHandleCompat`、`ShizukuSystemApis.getUserInfo` |
| P2 设置 | `settings/SettingsActivity.kt` + `SettingsComposeScreen.kt` | 15 + 748 | `ShizukuSettings`（KEEP_START_ON_BOOT / _WIRELESS / AUTO_PAIRING_ENABLED / WATCHDOG_ENABLED_ADB / TCPIP_PORT / NIGHT_MODE / LANGUAGE）、`ThemeHelper.KEY_BLACK_NIGHT_THEME / KEY_USE_SYSTEM_COLOR`、`BootCompleteReceiver` 组件开关、`WatchdogService.start/stop`、`LocaleDelegate`、`AppCompatDelegate.setDefaultNightMode`、`R.array.night_mode(_value)`、`ShizukuLocales.LOCALES` |
| P3 终端 | `shell/ShellTutorialActivity.kt` + `ShellTutorialComposeScreen.kt` | 57 + 219 | 导出 `rish` / `rish_shizuku.dex`（`OpenDocumentTree` + `DocumentsContract`），`Helps.RISH` |
| P3 配对 | `adb/AdbPairingTutorialActivity.kt` + `AdbPairingTutorialComposeScreen.kt` | 194 + 284 | `PairingTutorialState`（通知开关 / 通知监听 / 本地网络权限 / 前台服务启动失败）、`AdbPairingService.startIntent`、`requestPermissions(ACCESS_LOCAL_NETWORK)`、`onResume` 重同步、MIUI 提示 |
| P4 拆旧 | `home/{HomeActivity,HomeComposeScreen,LibrarySkinHome,HomeViewModel}.kt`、`MainActivity.kt`、上表全部 Compose 页、`ui/widget/DotMatrixFace.kt`、`ui/theme/Type.kt` | 251 + 532 + 992 + 53 + 3 + 103 + 44 | — |

### 必须留原生（OUT OF SCOPE，禁碰）

`server/`、`starter/`、`shell/`、`api/`（及 `aidl/rish/shared/provider/server-shared/hidden-api-stub`）、`authorization/RequestPermissionActivity(+ComposeScreen)`、`legacy/*`、`adb/AdbPairingService`、`adb/AdbPairingNotificationListener`、`starter/*`、`watchdog/*`、`receiver/*`、`ShizukuManagerProvider`、`ShizukuApplication`、包名 `moe.shizuku.privileged.api`。

## 2. 调用面（谁在用要动的东西）

| 符号 | 调用方 | 分类 | 处理 |
|---|---|---|---|
| `MainActivity` | `AndroidManifest.xml`；`legacy/LegacyIsNotSupportedActivity.kt:67` `startActivity(Intent(this, MainActivity::class.java))` | 仓内 | P4 才动：改指向 `FlutterHostActivity`，并从 Manifest 移除 |
| `HomeActivity.EXTRA_START_SERVICE_VIA_WADB` | `adb/AdbPairingService.kt:283`（通知 PendingIntent 目标已是 `FlutterHostActivity`）、`home/HomeActions.kt:56` | 仓内 | P4：常量迁到 `FlutterHostActivity` 或 `HomeActions`，字符串值不变 |
| `HomeActions.openApps/openTerminal/openSettings/openPairing` | `FlutterHostActivity` 方法路由；Dart `home_screen.dart` 快捷磁贴 | 仓内 | P2/P3：Dart 侧改为切底栏 Tab / 推 Flutter 页；Kotlin 方法保留到 P4 |
| `ShizukuComposeTheme` | Apps/Settings/Shell/AdbPairing Compose 页（待删）、`RequestPermissionComposeScreen`、`LegacyNoticeComposeScreen`（保留） | 仓内 | P4 **不删** `ShizukuComposeTheme.kt`（授权弹窗仍用） |
| `AppsManagementActivity` | Manifest；`HomeActions.openApps`；`HomeActivity` | 仓内 | P4 删 |
| `SettingsActivity` | Manifest（含 `APPLICATION_PREFERENCES` intent-filter）；`HomeActions.openSettings` | 仓内 + 系统入口 | P4：`APPLICATION_PREFERENCES` 需保留一个原生入口 → 改挂到 `FlutterHostActivity`（带 extra 直达设置 Tab）；见 GAP |
| `ShellTutorialActivity` / `AdbPairingTutorialActivity` | Manifest；`HomeActions` | 仓内 | P4 删 |
| `GrantedCountCache` | `HomeActions.grantedCount`、`AppsManagementActivity`、`AppsViewModel` | 仓内 | 新 `AppsChannel` 在 toggle 后必须同样置 `-1`（首页授权数一致性） |
| 动态/字符串引用 | Manifest 中 Activity 类名；`AdbPairingService` 的 PendingIntent；`":settings:fragment_args_key"` | 配置 | 全部记入 P4 清单，codemod 抓不到 |
| 外部消费者 | 第三方 App 依赖 `api/` 与 `moe.shizuku.privileged.api` 包名、`REQUEST_PERMISSION` / `REQUEST_BINDER` intent | 跨仓 | 不受影响（全部 OUT OF SCOPE） |

## 3. 依赖图与迁移序（依赖先于依赖者）

```
onetools/glass.dart(加性 liquid*) ─▶ onetools/liquid_glass.dart ─▶ nav/glass_dock.dart ─▶ nav/app_shell.dart ─▶ main.dart
widgets/glass_choice_dialog.dart ─▶ (apps/settings/terminal/pairing 各页的选择弹窗)
home/home_screen.dart(加导航回调) ─▶ nav/app_shell.dart
apps_models.dart ─▶ apps_channel.dart ─▶ apps_screen.dart ─▶ test/apps_screen_test.dart
AppsChannel.kt ─▶ (宿主接线 FlutterHostActivity.kt，协调者做)
settings_* / terminal_* / pairing_* 同构
```

- 强耦合簇（必须同批）：`glass.dart` 加性成员 + `liquid_glass.dart` + `glass_dock.dart`（缺一不编译）。
- 并行分片按目录切：`nav/`+`widgets/`+`onetools/liquid_glass.dart`（分片 A）、`apps/`+`AppsChannel.kt`（分片 B）、`settings/`+`SettingsChannel.kt`（分片 C）、`terminal/`+`TerminalChannel.kt`（分片 D）、`pairing/`+`PairingChannel.kt`（分片 E）。彼此无共享文件。
- 接线点（`FlutterHostActivity.kt`、`app_shell.dart`、`main.dart`）是唯一多分片都会想碰的文件 → **只由协调者（主 Agent）在批间串行修改**。

顺序文件：`MIGRATION_ORDER.txt`。

## 4. 依赖与 breaking

- 不新增 pub 依赖（Ultra 的 `app_shell.dart` 用了 `provider`，**不复制**，自写 Shizuku 版）。`DEPENDENCY_ALLOWLIST.txt` 为空。
- 不改 Gradle 依赖；Flutter AAR 仍由 `flutter build aar --no-profile` 产出，本地 repo 已存在（`manager_flutter/build/host/outputs/repo/moe/shizuku/manager_flutter/{flutter_debug,flutter_release}`）。
- Flutter 3.44 / Dart 3.12：`withValues(alpha:)`、`WidgetStateProperty`、`MediaQuery.viewPaddingOf` 均为 Ultra 文件已在用的 API，随复制进入，无 breaking。

## 5. 测试与行为基线（裁判）

| 裁判 | 命令（仓根） | 基线结果 | 证据 |
|---|---|---|---|
| Dart 静态分析 | `flutter analyze`（在 `manager_flutter/`） | No issues found | `baseline/flutter-analyze.log` |
| Dart Widget 测试 | `flutter test`（在 `manager_flutter/`） | 3 passed | `baseline/flutter-test.log` |
| Kotlin 单元测试 | `JAVA_HOME=JDK21; .\gradlew.bat :manager:testDebugUnitTest --offline` | 18 tests, 0 failures（HomeEventGateTest 2 / GrantStatesTest 4 / RootBootStartWorkerTest 5 / RootStartAttemptTest 3 / UserPresentRestartReceiverTest 4） | `baseline/gradle-unit.log` |
| 编译 | 同上任务隐含 `:manager:compileDebugKotlin` | BUILD SUCCESSFUL 1m15s | 同上 |
| 真机行为 | 装包点按（P1 也仍待装包确认） | 未做 | 本会话无设备；记入 GAP，rollout 前必须补 |

裁判"能抓坏"验证：GrantStatesTest 断言 `granted()` 不在调用线程执行，把 `loadGrantStates` 改成同步即红（属既有测试设计，本次未重复破坏验证）。新页面裁判由各分片交付时必须包含 1 个"故意坏"场景的断言（见 RULEBOOK §7）。

## 6. 风险分级

| 分片 | 风险 | 理由 | 批上限 |
|---|---|---|---|
| A shell-nav | 低-中 | 纯 Dart、复制为主；但改 `main.dart` 与 `home_screen.dart` 触及已上线首页 | 试点批内 |
| B apps | 中 | 授权 grant/revoke 是权限操作；语义必须与 `AppsManagementActivity` 完全一致 | 试点批内，逐 diff 审 |
| C settings | 中 | 7 个开关/选择项各带副作用（组件开关、Watchdog、recreate） | 30 文件以内（实际 6） |
| D terminal | 低-中 | 需 Activity Result（文档树）在宿主注册 | 同批 C |
| E pairing | 高 | 权限请求 + 前台服务 + onResume 状态机；系统行为差异大 | 单独一批，人审 |
| P4 拆旧 | 高 | 删 Activity/Manifest 项，回滚路径消失 | 需用户显式授权，真机验收后 |

## 7. gap 候选

见 `GAP_INVENTORY.md`。
