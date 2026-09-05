# E5 · pairing_screen_test.dart

- 分片：E pairing（Wave 3）
- 目标：`manager_flutter/test/pairing_screen_test.dart`
- 契约：RULEBOOK §7.1

1. 无宿主 → fallback 渲染，不崩；`start` 被调用一次（handler 记录）。
2. `notificationEnabled:false` → 出现通知引导卡与按钮；点按 → handler 收到 `openNotificationOptions`。
3. 全就绪 JSON → 出现开发者选项按钮；点按 → `openDeveloperOptions`。
4. `pairingServiceStartFailed:true` → 错误卡出现。
5. 坏输入：`supported` 为字符串 → 视为 true 走正常分支，不崩。

完成判据：`flutter test test/pairing_screen_test.dart` 全绿；第 2 条方法名改错必须红（验证后改回）。
