# B4 · apps_screen.dart

- 分片：B apps（Wave 1 试点）
- 目标：`manager_flutter/lib/apps/apps_screen.dart`
- 契约：RULEBOOK §3、§4、§9；SPEC §6 行为契约"应用 Tab"；GAP-3、GAP-4
- 参考（只读）：`management/AppsManagementComposeScreen.kt`（信息结构一一对应）、`manager_flutter/lib/home/home_screen.dart`（`_refresh` / `_GlassAlert` / `_ActionCard` 模式）、`manager_flutter/lib/onetools/glass.dart`

## 信息结构（对齐 Compose）

1. 页头：标题 `copy.title`（`titleLarge`，与首页 `_TopCapsules` 同一行高；不放返回箭头——它是 Tab）。
2. `running == false` → 一张 `GlassPanel` 占位卡（`Icons.info_outline` + `copy.notRunning`），列表不渲染（GAP-3）。
3. `running && apps.isEmpty` → 空状态卡（`Icons.info_outline` + `copy.empty`）。
4. 列表：每项一张**主题 `Card`**（RULEBOOK §3.1/§3.2 v1.1：列表行禁用 `GlassPanel`——它内含 `BackdropFilter`）。`Card(child: InkWell(onTap: toggle, child: Padding(EdgeInsets.symmetric(horizontal: Glass.space20, vertical: 18), Row(...))))`：左 40×40 圆角图标（`Image.memory`，按 key 缓存，加载中/失败 `Icons.android_outlined`），中间 `label`（`titleMedium`）+ `packageName`（`bodyMedium`, `onSurfaceVariant`）+ 可选 `copy.requiresRoot`（`bodySmall`），右 `Switch(value: granted)`。点卡片或开关都调 `AppsChannel.toggle`。占位卡与空状态卡（每页最多 1 张）仍可用 `GlassPanel`。
5. `toggle` 返回 `result == 'adbLimited'` → `_GlassAlert`（`errorContainer` 底、`Icons.info_outline`、标题 `copy.adbLimitedTitle`、正文 `copy.adbLimitedMessage`、按钮 `copy.ok`）；否则用返回的快照 `setState`。
6. `initState` 拉 `getState`；`resumed` 重拉（§4.1）。`AppShell` 保活后切回本 Tab 不重拉（与 Compose 不同的是没有 `onResume`，靠 lifecycle）。

## 禁止

- 列表卡不套 `BackdropFilter` / `LiquidGlass`（§3.2）。
- 不写 hex 颜色，不自定义圆角 / 间距数值。
- 不在页面里持久化授权状态；每次以快照为准。

## 完成判据

`flutter analyze` 无问题；B5 全绿。
