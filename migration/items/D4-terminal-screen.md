# D4 · terminal_screen.dart

- 分片：D terminal（Wave 2）
- 目标：`manager_flutter/lib/terminal/terminal_screen.dart`
- 契约：RULEBOOK §3、§4、§11；SPEC §6"终端 Tab"
- 只读参考：`shell/ShellTutorialComposeScreen.kt`（段落顺序、代码块、按钮）、`manager_flutter/lib/home/home_screen.dart`（`_ActionCard` 模式）

## 信息结构

页头标题 `copy.title`；按 Compose 段落顺序逐段一张 `GlassPanel`：说明 → 步骤（含 `shName` / `dexName` 的等宽 `Text`，`fontFamily: 'monospace'` 允许，因 Compose 也用等宽）→ 操作按钮行（`导出` → `TerminalChannel.exportFiles()`；`查看指南` → `openGuide()`）。Compose 若有"复制命令"类动作，一并映射到 `HomeChannel` 之外的本通道方法（如需 `copyText`，在 D1 加 `copyText {text}` 方法并同步更新 D1 卡片说明——报告里注明）。

## 完成判据

`flutter analyze` 无问题；D5 全绿。
