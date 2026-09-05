# A4 · app_shell.dart（新写）+ main.dart / home_screen.dart / home_models.dart / HomeActions.kt 伴随修改

- 分片：A shell-nav（Wave 1 试点）
- 目标：`manager_flutter/lib/nav/app_shell.dart`
- 伴随修改：`manager_flutter/lib/main.dart`、`manager_flutter/lib/home/home_screen.dart`、`manager_flutter/lib/home/home_models.dart`、`manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt`
- 参考（只读）：Ultra `lib/nav/app_shell.dart`（结构参考；**不要复制**，它依赖 `provider` 与 IMS 页面）

## 做什么

1. `AppShell`（`StatefulWidget`）：
   - 构造参数：`initialIndex`（默认 0）、`appsTab`、`terminalTab`、`settingsTab`（三个 `Widget?`，为 `null` 时渲染占位 `_PlaceholderTab(label)`——一张 `GlassPanel` 卡 + `Icons.hourglass_empty_outlined` + 该 Tab 标签；占位在后续 Wave 由协调者替换为真实页面）。
   - `IndexedStack` 保活 4 页（RULEBOOK §4.4）；`Scaffold(backgroundColor: Colors.transparent, extendBody: true, bottomNavigationBar: GlassDock(...))`。
   - `GlassDock.destinations`：4 个 `NavigationDestination`（图标与顺序见 RULEBOOK §3.7），标签用 `HomeCopy` 的 `tabHome / tabApps / tabTerminal / tabSettings`。标签来源：`AppShell` 自己拉一次 `HomeChannel.getState()` 取 `copy`（无宿主时 fallback），并监听 `AppLifecycleState.resumed` 重拉（语言切换后宿主会 recreate，所以其实一次就够；重拉是保险）。
   - 把 `HomeScreen(onOpenApps: () => _select(1), onOpenTerminal: () => _select(2), onOpenSettings: () => _select(3))` 放在 index 0。
2. `home_screen.dart`：`HomeScreen` 新增三个可选参数 `onOpenApps / onOpenTerminal / onOpenSettings`（`VoidCallback?`）。在现有调用 `_call('openApps')`、`_call('openTerminal')` 的两处（首页目前没有 `openSettings` 调用点，`onOpenSettings` 只声明作预留），改为「回调非 null 就调回调，否则走原 `_call`」的 if/else（`void` 不能作 `??` 左操作数）。不改其它任何行。（v1.1 修订）
3. `home_models.dart`：`HomeCopy` 加 `tabHome / tabApps / tabTerminal / tabSettings` 四个字段、构造参数、`fallback`（`'Shizuku'` / `'Apps'` / `'Terminal'` / `'Settings'`）、`fromJson` 的 `pick`。
4. `HomeActions.kt` `copyJson()`：在 `.put("cancel", ...)` 之后追加 4 行：
   ```kotlin
   .put("tabHome", c.getString(R.string.app_name))
   .put("tabApps", c.getString(R.string.home_app_management_title))
   .put("tabTerminal", c.getString(R.string.home_terminal_title_plain))
   .put("tabSettings", c.getString(R.string.settings_title))
   ```
   不改任何其它行；无法编译验证（分片禁跑 Gradle），协调者 survey build 兜底。
5. `main.dart`：`home: const HomeScreen()` → `home: const AppShell()`。

## 完成判据

- `flutter analyze` 无问题；`flutter test` 全部通过（含 A5 新测试；`widget_test.dart` 若因根组件变化需要调整，只改定位不改期望——RULEBOOK §7.3）。
- `home_screen.dart` diff 只涉及 3 个回调调用点 + 构造参数声明。
