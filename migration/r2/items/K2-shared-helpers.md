# K2 · 共享 Kotlin 工具收敛（四个文件、四处采用）

- 分片：K2（Wave 1）· 模型 Fable 5.1
- 目标（manifest target）：`manager/src/main/java/moe/shizuku/manager/flutter/NotificationListenerAccess.kt`
- 新建：上者 + `.../flutter/LocaleLabels.kt` + `.../flutter/BootPrefs.kt` + `.../flutter/HtmlText.kt` + `manager/src/test/java/moe/shizuku/manager/flutter/SharedHelpersTest.kt`
- 修改（只改为调用共享实现）：`.../flutter/SettingsChannel.kt`、`.../flutter/PairingChannel.kt`、`.../flutter/AppsChannel.kt`、`.../flutter/TerminalChannel.kt`、`manager/src/test/java/moe/shizuku/manager/flutter/SettingsChannelTest.kt`（仅 `localeLabel(...)` → `LocaleLabels.label(...)` 的调用名）
- **不碰**：`HomeActions.kt`（K1 在改；Wave 2 由 K4 采用你的工具）、`FlutterHostActivity.kt`、任何 Dart
- 契约：R2 RULEBOOK §R1、§R8；v1.3 RULEBOOK §1.8、§6、§8

## 步骤（按序；每一步都是"剪切—粘贴—改调用点"，不改逻辑）

1. **BootPrefs.kt**：把 `SettingsChannel.kt` 顶部的 `BootPrefs`、`applyBootToggle`、`parseTcpipPort`（含各自 KDoc）原样剪到新文件；同包，调用点无需改 import。`SettingsChannelTest` 不需改（同包顶层函数）。
2. **LocaleLabels.kt**：把 `SettingsChannel.kt` 的 `localeLabel(tag)`（43 条表）与 `buildLocaleRows(tags, currentTag, systemLabel)`、`LocaleRow` 数据类剪进 `object LocaleLabels { fun label(...); fun rows(...) }` + 顶层 `LocaleRow`。`SettingsChannel` 调用点改名；`SettingsChannelTest` 里对 `localeLabel` / `buildLocaleRows` 的调用改为 `LocaleLabels.label` / `LocaleLabels.rows`，**期望值一字不改**。
3. **NotificationListenerAccess.kt**：以 `PairingChannel.isNotificationListenerEnabled()` 为准实现 `flatContainsPackage(flat, packageName)`（纯函数）+ `isEnabled(context)`；以 `SettingsChannel.openNotificationAccess()` 为准实现 `openSettings(activity)`（`PairingChannel.openNotificationAccessSettings()` 与之逐行相同，请先 diff 确认；若有差异以 Pairing 版为准并在报告注明）。然后 `SettingsChannel`、`PairingChannel` 删掉各自私有实现，改为调用；`AppsChannel`/`TerminalChannel` 不涉及。
4. **HtmlText.kt**：`htmlToPlainText(html)` = `AppsChannel.plainText` 的实现；`AppsChannel.plainText` 与 `TerminalChannel.plainText` 删掉，调用点改为 `htmlToPlainText(...)`。`TerminalChannel.mono()` 保留在原处（只有它用）。
5. **SharedHelpersTest.kt**（JUnit4，零 Android）：`flatContainsPackage`：`null → false`、`"" → false`、`"a/b:moe.shizuku.privileged.api/x" → true`（用参数传包名，不依赖 BuildConfig）、`"other/x" → false`、`"garbage::" → false`；`LocaleLabels.label`：`"zh-CN"`、`"de"`、`"xx-YY"` 原样；`LocaleLabels.rows`：首项 tag `SYSTEM` 且 label = systemLabel，`selected` 只在 currentTag 处为 true。

## 自查（报告贴原文）

- `rg -n "fun isNotificationListenerEnabled|fun openNotificationAccess\b|fun plainText|fun localeLabel|fun buildLocaleRows|data class BootPrefs|fun applyBootToggle|fun parseTcpipPort" manager/src/main/java` → 每个名字只在共享文件出现一次（`openNotificationAccess` 作为通道**方法路由名**的字符串 `"openNotificationAccess"` 可以留在 SettingsChannel 的 `when` 里，函数体不能留）。
- 四个通道文件的 `git diff --stat` 只应是删除行 + 少量调用行。

## 完成判据

协调者 survey build 通过；`SettingsChannelTest`（15）+ `SharedHelpersTest`（≥ 6）全绿；`AppsChannelTest`、`PairingChannelTest` 不改而全绿。
