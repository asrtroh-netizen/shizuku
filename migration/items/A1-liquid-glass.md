# A1 · liquid_glass.dart（复制）+ glass.dart 加性移植

- 分片：A shell-nav（Wave 1 试点）
- 目标：`manager_flutter/lib/onetools/liquid_glass.dart`
- 伴随修改：`manager_flutter/lib/onetools/glass.dart`（仅新增成员）
- 真源：`E:\GQ\Ultra\OneIms Ultra\oneims_flutter\lib\onetools\liquid_glass.dart`、同目录 `glass.dart`

## 做什么

1. 复制 Ultra `liquid_glass.dart`，只把 `package:oneims_flutter/` 换成 `package:manager_flutter/`（RULEBOOK §3.3）。
2. 从 Ultra `glass.dart` 逐字节复制这 5 个成员到本仓库 `glass.dart` 的 `Glass` 类里（RULEBOOK §3.4，GAP-1）：`blurLiquid`、`liquidRefraction`、`liquidFill(...)`、`liquidHighlight(...)`、`liquidBackdropFilter(...)`。如果它们依赖 `dart:ui` 的 `ImageFilter` 等 import，一并补 import；不得改动任何既有成员。

## 完成判据

- `flutter analyze` 无问题；`liquid_glass.dart` 能被 `glass_dock.dart` 引用编译。
- `git diff manager_flutter/lib/onetools/glass.dart` 只有新增行（无删除/修改行）。
