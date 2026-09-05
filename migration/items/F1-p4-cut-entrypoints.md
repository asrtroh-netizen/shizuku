# F1 · P4-1 断入口（Manifest + 指向 + 常量 + 删 4 路由）

- 分片：F p4（用户已授权；Wave 4 第一步）
- 目标（收据）：`migration/receipts/F1-p4-cut-entrypoints.txt`（完成后由协调者写入改动文件哈希）
- 允许修改：`manager/src/main/AndroidManifest.xml`、`manager/src/main/java/moe/shizuku/manager/legacy/LegacyIsNotSupportedActivity.kt`、`manager/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt`（仅 `HomeActivity.EXTRA_START_SERVICE_VIA_WADB` 引用处与 import）、`manager/src/main/java/moe/shizuku/manager/home/HomeActions.kt`、`manager/src/main/java/moe/shizuku/manager/flutter/FlutterHostActivity.kt`
- 契约：SPEC §13 P4-1；RULEBOOK §1、§6、§8

## 做什么（逐条）

1. `AndroidManifest.xml`：删除 `.MainActivity`、`.management.AppsManagementActivity`、`.adb.AdbPairingTutorialActivity`、`.shell.ShellTutorialActivity`、`.settings.SettingsActivity` 五个 `<activity>` 块；把原 `SettingsActivity` 的 `<intent-filter><action android:name="android.intent.action.APPLICATION_PREFERENCES"/></intent-filter>` 追加到 `.flutter.FlutterHostActivity` 的 `<activity>` 内（保留其原有 LAUNCHER filter；`exported="true"` 已有）。其它一律不动（`StarterActivity`、`RequestPermissionActivity`、legacy 两个 Activity、services、receivers、provider）。
2. `FlutterHostActivity.kt`：
   - `companion object` 加 `const val EXTRA_START_SERVICE_VIA_WADB = "moe.shizuku.manager.extra.START_SERVICE_VIA_WADB"`（字符串值必须与 `HomeActivity` 里的一字不差）。
   - `getInitialRoute()`：在现有 TAB extra 逻辑之前加 `if (intent?.action == Intent.ACTION_APPLICATION_PREFERENCES) return "/tab/3"`。
   - 删除 `"openApps"`、`"openTerminal"`、`"openSettings"`、`"openPairing"` 四条 `when` 分支（其它分支不动）。
3. `HomeActions.kt`：删除 `openApps()`、`openTerminal()`、`openSettings()`、`openPairing()` 四个方法及其专用 import（`AppsManagementActivity`、`SettingsActivity`、`ShellTutorialActivity`；`AdbPairingTutorialActivity` 是全限定名引用，无 import）；`handleStartViaWadbIntent` 里 `HomeActivity.EXTRA_START_SERVICE_VIA_WADB` → `moe.shizuku.manager.flutter.FlutterHostActivity.EXTRA_START_SERVICE_VIA_WADB`（加 import）。类注释里提到 `[HomeActivity]` 的地方改成说明"Compose 首页已下线"。其它方法一行不改。
4. `AdbPairingService.kt`：`HomeActivity.EXTRA_START_SERVICE_VIA_WADB` → `FlutterHostActivity.EXTRA_START_SERVICE_VIA_WADB`；删除 `import moe.shizuku.manager.home.HomeActivity`，如需加 `import moe.shizuku.manager.flutter.FlutterHostActivity`（该文件第 263 行已用全限定名启动 FlutterHostActivity，可顺手统一为 import，但不改任何逻辑）。
5. `LegacyIsNotSupportedActivity.kt`：`Intent(this, MainActivity::class.java)` → `Intent(this, FlutterHostActivity::class.java)`；import 相应替换。
6. **不删任何源码文件**（那是 P4-2）。`HomeActivity.kt` 里的常量保留不动（文件 P4-2 才删；两处常量值相同，编译无冲突）。

## 完成判据

- `JAVA_HOME=JDK21 gradlew :manager:testDebugUnitTest --offline` BUILD SUCCESSFUL，50 单测全绿（协调者跑）。
- `rg -n "MainActivity|AppsManagementActivity|SettingsActivity|ShellTutorialActivity|AdbPairingTutorialActivity" manager/src/main/AndroidManifest.xml` 零命中。
- `rg -n "HomeActivity\." manager/src/main/java --glob '!**/home/HomeActivity.kt' --glob '!**/MainActivity.kt'` 零命中。
