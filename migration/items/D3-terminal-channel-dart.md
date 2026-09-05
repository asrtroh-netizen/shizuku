# D3 · terminal_channel.dart

- 分片：D terminal（Wave 2）
- 目标：`manager_flutter/lib/terminal/terminal_channel.dart`
- 契约：RULEBOOK §1.7、§11；模式同 `home/home_channel.dart`

`TerminalChannel`：`getState()`、`exportFiles()`、`openGuide()`；`static const EventChannel events = EventChannel('shizuku/terminal/events')`（页面订阅 `"changed"` 重拉，可选）。

完成判据：`flutter analyze` 无问题；D5 覆盖。
