# A2 · glass_dock.dart（复制）

- 分片：A shell-nav（Wave 1 试点）
- 目标：`manager_flutter/lib/nav/glass_dock.dart`
- 真源：`E:\GQ\Ultra\OneIms Ultra\oneims_flutter\lib\nav\glass_dock.dart`
- 依赖：A1（`liquid_glass.dart` + `Glass.liquid*`）

## 做什么

复制真源，只换 import 前缀（RULEBOOK §3.3）。`Glass.radiusDock / dockHeight / dockBottomGap / pageMargin` 在本仓库 `glass.dart` 已存在，不需要动。

## 完成判据

- `flutter analyze` 无问题。
- 与真源 diff 仅 import 两行不同。
