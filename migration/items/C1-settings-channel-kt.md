# C1 · SettingsChannel.kt

- 分片：C settings（Wave 2）
- 目标：`manager/src/main/java/moe/shizuku/manager/flutter/SettingsChannel.kt`
- 契约：RULEBOOK §1、§5.2、§10；GAP-7、GAP-10
- 只读参考：`settings/SettingsComposeScreen.kt`（`buildSettingsModel`、`onToggle` / `onClick` 分支、`buildLocaleItems`、对话框文案）、`ShizukuSettings.java`、`app/ThemeHelper.java`、`home/HomeActions.kt`（`setLocale` / `localeArray` 写法可参考但不要调用私有方法）、`ktx/Context.kt`（`isComponentEnabled` / `setComponentEnabled`）

## 做什么

- 纯函数（零 Android 依赖）：`internal data class BootPrefs(val bootRoot: Boolean, val bootWireless: Boolean)`；`internal fun applyBootToggle(current: BootPrefs, key: String, checked: Boolean): BootPrefs`（Root/无线互斥）；`internal fun parseTcpipPort(raw: String): Int?`（`""`→null 表示清除；非 1..65535 → 抛 `IllegalArgumentException`）；`internal fun buildSettingsStateMap(...)`。
- `getState`：§10 字段。`nightModeOptions` 来自 `R.array.night_mode` / `R.array.night_mode_value`；`locales` 与 `HomeActions.localeArray()` 同构（`ShizukuLocales.LOCALES`，首项 `settings_language_system`，其余中文/英文/日文/韩文标签同表）。
- `setBool`：逐项同 `SettingsComposeScreen.onToggle`（§5.2）。`KEEP_START_ON_BOOT_WIRELESS` 且缺权限 → 不落盘，返回快照 + `needGrant:true, grantCmd`。`AUTO_PAIRING_ENABLED` 且未开通知监听 → 不落盘，返回 `needNotificationAccess:true`。`WATCHDOG_ENABLED_ADB`、两个开机项、两个主题项落盘后 `activity.recreate()`（与 Compose `recreateAfterAnimation` 等价；延迟 200ms 用 `Handler(Looper.getMainLooper()).postDelayed`）。
- `setTcpipPort {port}`：`ShizukuSettings.getPreferences().edit { putString(TCPIP_PORT, ...) }`（对齐 Compose 的 TcpIpPort 对话框保存逻辑，请读源码确认 key 与类型）。
- `setNightMode {mode}`：写 `NIGHT_MODE` + `AppCompatDelegate.setDefaultNightMode` + `recreate()`。
- `setLocale {tag}`：同 `HomeActions.setLocale` 语义 + `recreate()`。
- `openNotificationAccess` / `openTranslation` / `openWirelessGuide` / `copyText`：§10。

## 完成判据

协调者 survey build 通过；C6 单测全绿。
