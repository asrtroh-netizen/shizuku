# A3 · glass_choice_dialog.dart（复制 + 去 l10n）

- 分片：A shell-nav（Wave 1 试点）
- 目标：`manager_flutter/lib/widgets/glass_choice_dialog.dart`
- 真源：`E:\GQ\Ultra\OneIms Ultra\oneims_flutter\lib\widgets\glass_choice_dialog.dart`（173 行）

## 做什么

1. 复制真源，换 import 前缀；删除 `import 'package:oneims_flutter/l10n/app_localizations.dart'`。
2. 唯一允许的改动（GAP-13）：`l10n.exclClose` 所在处改为读构造参数 `closeTooltip`（`final String closeTooltip;`，构造默认值 `'Close'`）；相关 `final l10n = AppLocalizations.of(context)` 之类的行删掉。
3. 导出 `GlassChoiceDialog` 与 `GlassChoiceWell` 两个类，签名其余不变。

## 完成判据

- `flutter analyze` 无问题。
- 与真源 diff 除 import / `closeTooltip` 外无差异。
