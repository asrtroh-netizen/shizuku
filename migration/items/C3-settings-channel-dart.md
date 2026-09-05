# C3 · settings_channel.dart

- 分片：C settings（Wave 2）
- 目标：`manager_flutter/lib/settings/settings_channel.dart`
- 契约：RULEBOOK §1.7、§10；模式同 `home/home_channel.dart`

## 做什么

`SettingsChannel`：`getState()`、`setBool(String key, bool checked)`、`setTcpipPort(String port)`、`setNightMode(int mode)`、`setLocale(String tag)`、`invoke(String method, [args])`（给 `openNotificationAccess` / `openTranslation` / `openWirelessGuide` / `copyText` 用）。全部返回 `Future<Map<String, dynamic>>`，`unavailable` 语义同 Home。

## 完成判据

`flutter analyze` 无问题；C5 用 mock handler 覆盖。
