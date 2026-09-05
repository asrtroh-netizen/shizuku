# C2 · settings_models.dart

- 分片：C settings（Wave 2）
- 目标：`manager_flutter/lib/settings/settings_models.dart`
- 契约：RULEBOOK §2、§10；模式同 `home/home_models.dart`

## 做什么

`SettingsCopy`（§10 全部 key + 英文 fallback + `fromJson`）、`NightModeOption(value:int, label)`、`SettingsLocale(tag,label,selected)`、`SettingsSnapshot`（`bootRoot, bootWireless, autoPairing, watchdog, tcpipPort:String, nightMode:int, nightModeOptions, blackNightTheme, useSystemColor, locales, translationUrl, copy`）+ `empty` + `fromJson`（坏类型用默认值）+ `copyWith`（供本地乐观更新前后对比，可选）。

## 完成判据

`flutter analyze` 无问题。
