# F2 · P4-2 删源码（观察一个版本后，或用户再次点头）

- 分片：F p4（Wave 4 第二步；**未执行**）
- 目标（收据）：`migration/receipts/F2-p4-delete-sources.txt`
- 契约：SPEC §13 P4-2

## 引用核查结果（2026-09-05，P4-1 之后）

对清单内全部类名（含 `LibraryHeroCard`、`ApplicationManagementComposeScreen`、`appsViewModel`、`PairingTutorialState`、`ToggleResult`）在清单外 `rg`：**代码引用为零**，仅剩 `flutter/{Apps,Settings,Terminal,Pairing}Channel.kt` 与 `PairingChannelTest.kt` 的 KDoc/注释提及（说明"逐行同义于旧实现"），删文件后只是注释里的方括号链接悬空，不影响编译；`ServiceStatus` 被 `receiver/ShizukuReceiver.kt` 使用 → 保留；`Type.kt` 的 `ShizukuTypography` 被 `ShizukuComposeTheme` 使用 → 保留。清单可直接执行。

## 删除清单（15 个文件；执行前再跑一次上述 `rg` 复核）

```
manager/src/main/java/moe/shizuku/manager/MainActivity.kt
manager/src/main/java/moe/shizuku/manager/home/HomeActivity.kt
manager/src/main/java/moe/shizuku/manager/home/HomeComposeScreen.kt
manager/src/main/java/moe/shizuku/manager/home/LibrarySkinHome.kt
manager/src/main/java/moe/shizuku/manager/home/HomeViewModel.kt
manager/src/main/java/moe/shizuku/manager/management/AppsManagementActivity.kt
manager/src/main/java/moe/shizuku/manager/management/AppsManagementComposeScreen.kt
manager/src/main/java/moe/shizuku/manager/management/AppsViewModel.kt
manager/src/main/java/moe/shizuku/manager/settings/SettingsActivity.kt
manager/src/main/java/moe/shizuku/manager/settings/SettingsComposeScreen.kt
manager/src/main/java/moe/shizuku/manager/shell/ShellTutorialActivity.kt
manager/src/main/java/moe/shizuku/manager/shell/ShellTutorialComposeScreen.kt
manager/src/main/java/moe/shizuku/manager/adb/AdbPairingTutorialActivity.kt
manager/src/main/java/moe/shizuku/manager/adb/AdbPairingTutorialComposeScreen.kt
manager/src/main/java/moe/shizuku/manager/ui/widget/DotMatrixFace.kt
```

## 保留（有活引用）

`ui/theme/ShizukuComposeTheme.kt`、`ui/theme/Type.kt`（`RequestPermissionComposeScreen`、`LegacyNoticeComposeScreen`）、`management/GrantStates.kt`（`AppsChannel`、`HomeActions`）、`app/AppActivity.kt`、`app/AppBarActivity.kt`（原生 Activity 基类）、`model/ServiceStatus.kt`（若仅 `HomeViewModel` 用则随删——执行时核查）。

## 执行步骤

1. 对清单每个类名跑 `rg -n "<ClassName>" manager/src --glob '!<自身文件>'`，除清单内文件互相引用外必须零命中；有命中即停，把命中加进本卡再决定。
2. 删除文件；`rg` 复查 `ServiceStatus`、`HomeViewModel`、`appsViewModel`、`DotMatrixFace` 是否还有引用。
3. `JAVA_HOME=JDK21 gradlew :manager:testDebugUnitTest --offline` 全绿 → `flutter build aar --no-profile` → `:manager:assembleDebug` 与 `:manager:assembleRelease` 各出一包。
4. 真机：第三方 Shizuku 客户端仍能连、授权弹窗仍是原生。
