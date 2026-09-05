# D5 · terminal_screen_test.dart

- 分片：D terminal（Wave 2）
- 目标：`manager_flutter/test/terminal_screen_test.dart`
- 契约：RULEBOOK §7.1

1. 无宿主 → fallback 标题与 `rish` / `rish_shizuku.dex` 文本都在。
2. 完整 JSON → 自定义 `shName:'foo'` 出现在页面。
3. 点"导出" → handler 收到 `exportFiles`；点"查看指南" → 收到 `openGuide`。
4. 坏输入：`copy` 为字符串 → fallback，不崩。

完成判据：`flutter test test/terminal_screen_test.dart` 全绿；第 3 条方法名改错必须红（验证后改回）。
