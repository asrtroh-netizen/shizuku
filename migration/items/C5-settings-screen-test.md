# C5 · settings_screen_test.dart

- 分片：C settings（Wave 2）
- 目标：`manager_flutter/test/settings_screen_test.dart`
- 契约：RULEBOOK §7.1

## 必须覆盖

1. 无宿主 → 三组卡标题为 fallback，7 个开关/条目都在，不崩。
2. 完整 JSON（`bootRoot:true, bootWireless:false, ...`）→ 对应 `Switch.value` 正确。
3. 点"无线开机" → handler 收到 `setBool(key=KEEP_START_ON_BOOT_WIRELESS 的字符串值, checked:true)`；handler 返回 `needGrant:true, grantCmd:'adb shell pm grant ...'` → 弹窗含 `grantCmd` 文本；点 `manual` → handler 收到 `openWirelessGuide` 与 `copyText`。
4. 点"语言" → 出现 `GlassChoiceDialog`，选第二项 → handler 收到 `setLocale(tag)`。
5. 坏输入：`nightModeOptions` 是字符串 → 深色模式行仍渲染、点击不崩；`tcpipPort` 是数字 `5555` → 显示为 `'5555'`。

## 完成判据

`flutter test test/settings_screen_test.dart` 全绿；把第 3 条期望 key 改错必须红（验证后改回）。
