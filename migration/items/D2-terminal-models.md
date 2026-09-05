# D2 · terminal_models.dart

- 分片：D terminal（Wave 2）
- 目标：`manager_flutter/lib/terminal/terminal_models.dart`
- 契约：RULEBOOK §2、§11；模式同 `home/home_models.dart`

`TerminalCopy`（D1 的全部 copy key + 英文 fallback，英文文案取 `res/values/strings.xml` 默认值原文）、`TerminalSnapshot(shName, dexName, copy)` + `empty`（`'rish'`, `'rish_shizuku.dex'`）+ `fromJson`（坏类型用默认）。

完成判据：`flutter analyze` 无问题。
