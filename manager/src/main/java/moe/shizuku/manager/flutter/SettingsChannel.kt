package moe.shizuku.manager.flutter

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.watchdog.WatchdogService
import org.json.JSONObject
import rikka.core.util.ClipboardUtils
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku
import rikka.shizuku.manager.ShizukuLocales
import java.util.Locale

// ───────────────────────── 纯函数（零 Android / org.json 依赖，供 JUnit 直测）─────────────────────────

/** 夜间模式候选：`R.array.night_mode_value` ↔ `R.array.night_mode`。 */
internal data class NightModeOption(val value: Int, val label: String)

/**
 * 整页快照（RULEBOOK §10）。`tcpipPort` 未设（null / 空）时为 `""` 而非 null。
 * `supportsStartOnBoot` 为 false 时 Compose 页隐藏四个启动开关（仅留 TCP 端口），Dart 同样处理。
 */
internal fun buildSettingsStateMap(
    supportsStartOnBoot: Boolean,
    boot: BootPrefs,
    autoPairing: Boolean,
    watchdog: Boolean,
    tcpipPort: String?,
    nightMode: Int,
    nightModeOptions: List<NightModeOption>,
    blackNightTheme: Boolean,
    useSystemColor: Boolean,
    locales: List<LocaleRow>,
    translationUrl: String,
    copy: Map<String, Any?>,
): Map<String, Any?> {
    return linkedMapOf(
        "ok" to true,
        "supportsStartOnBoot" to supportsStartOnBoot,
        "bootRoot" to boot.bootRoot,
        "bootWireless" to boot.bootWireless,
        "autoPairing" to autoPairing,
        "watchdog" to watchdog,
        "tcpipPort" to tcpipPort.orEmpty(),
        "nightMode" to nightMode,
        "nightModeOptions" to nightModeOptions.map {
            linkedMapOf<String, Any?>("value" to it.value, "label" to it.label)
        },
        "blackNightTheme" to blackNightTheme,
        "useSystemColor" to useSystemColor,
        "locales" to locales.map {
            linkedMapOf<String, Any?>("tag" to it.tag, "label" to it.label, "selected" to it.selected)
        },
        "translationUrl" to translationUrl,
        "copy" to copy,
    )
}

// ───────────────────────────────────────── 通道类 ─────────────────────────────────────────

/**
 * 「设置」Tab 的 MethodChannel（RULEBOOK §10）。界面在 Dart，业务逻辑与
 * 原 Compose `SettingsComposeScreen`（已于 P4 删除）逐项同义（§5.2）。
 *
 * `getState` 含 `EnvironmentUtils.isRooted()` 的文件探测，走 [scope]（IO）并在主线程回复；
 * 写操作是纯偏好读写 + 组件开关，直接在主线程 `runCatching`，
 * 需要重建宿主的项**先回复再**调度 `recreate()`（等开关动画 200ms，同 Compose `recreateAfterAnimation`）。
 */
class SettingsChannel(private val activity: Activity, private val scope: CoroutineScope) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 写操作的结果：回复给 Dart 的 JSON + 回复后是否重建宿主。 */
    private class Outcome(val json: String, val recreate: Boolean)

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> runIo(result, "getState") { snapshotJson() }
                "setBool" -> {
                    val key = call.argument<String>("key").orEmpty()
                    val checked = call.argument<Boolean>("checked") == true
                    runMain(result, "setBool") { setBool(key, checked) }
                }
                "setTcpipPort" -> {
                    val port = call.argument<String>("port").orEmpty()
                    runMain(result, "setTcpipPort") { setTcpipPort(port) }
                }
                "setNightMode" -> {
                    val mode = call.argument<Number>("mode")?.toInt()
                        ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    runMain(result, "setNightMode") { setNightMode(mode) }
                }
                "setLocale" -> {
                    val tag = call.argument<String>("tag") ?: "SYSTEM"
                    runMain(result, "setLocale") { setLocale(tag) }
                }
                "openNotificationAccess" -> runAction(result, "openNotificationAccess") {
                    NotificationListenerAccess.openSettings(activity)
                }
                "openTranslation" -> runAction(result, "openTranslation") {
                    CustomTabsHelper.launchUrlOrCopy(activity, activity.getString(R.string.translation_url))
                }
                "openWirelessGuide" -> runAction(result, "openWirelessGuide") {
                    CustomTabsHelper.launchUrlOrCopy(activity, WIRELESS_GUIDE_URL)
                }
                "copyText" -> {
                    val text = call.argument<String>("text").orEmpty()
                    runAction(result, "copyText") { ClipboardUtils.put(activity, text) }
                }
                else -> result.notImplemented()
            }
        }
    }

    // ───── 调度 ─────

    private fun runIo(result: MethodChannel.Result, name: String, block: () -> String) {
        scope.launch {
            try {
                val json = block()
                withContext(Dispatchers.Main) { result.success(json) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { result.error(name, t.message, null) }
            }
        }
    }

    private fun runMain(result: MethodChannel.Result, name: String, block: () -> Outcome) {
        runCatching(block).fold(
            { outcome ->
                result.success(outcome.json)
                if (outcome.recreate) scheduleRecreate()
            },
            { result.error(name, it.message, null) },
        )
    }

    private fun runAction(result: MethodChannel.Result, name: String, block: () -> Unit) {
        runCatching(block).fold(
            { result.success(JSONObject().put("ok", true).toString()) },
            { result.error(name, it.message, null) },
        )
    }

    /**
     * GAP-7 契约：重建前把当前 Tab（设置 = 3）写进宿主 intent，宿主 `getInitialRoute()` 读它回到设置 Tab。
     * 必须在 `result.success(...)` 之后调用；延迟 200ms 对齐 Compose `recreateAfterAnimation`。
     */
    private fun scheduleRecreate() {
        activity.intent?.putExtra("moe.shizuku.manager.extra.TAB", 3)
        mainHandler.postDelayed({
            if (!activity.isDestroyed) activity.recreate()
        }, 200)
    }

    // ───── 快照 ─────

    private fun snapshotJson(extra: Map<String, Any?> = emptyMap()): String {
        val prefs = ShizukuSettings.getPreferences()
        val supportsStartOnBoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            EnvironmentUtils.isTelevision(activity) ||
            EnvironmentUtils.isRooted()
        val labels = activity.resources.getStringArray(R.array.night_mode)
        val values = activity.resources.getIntArray(R.array.night_mode_value)
        val nightModeOptions = values.mapIndexed { index, value -> NightModeOption(value, labels[index]) }
        val currentTag = prefs.getString(ShizukuSettings.LANGUAGE, "SYSTEM") ?: "SYSTEM"
        val locales = LocaleLabels.rows(
            ShizukuLocales.LOCALES.toList(),
            currentTag,
            activity.getString(R.string.settings_language_system),
        )
        val map = LinkedHashMap(
            buildSettingsStateMap(
                supportsStartOnBoot = supportsStartOnBoot,
                boot = BootPrefs(
                    bootRoot = prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false),
                    bootWireless = prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false),
                ),
                autoPairing = prefs.getBoolean(ShizukuSettings.AUTO_PAIRING_ENABLED, false),
                watchdog = prefs.getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false),
                tcpipPort = prefs.getString(ShizukuSettings.TCPIP_PORT, ""),
                nightMode = ShizukuSettings.getNightMode(),
                nightModeOptions = nightModeOptions,
                blackNightTheme = prefs.getBoolean(ThemeHelper.KEY_BLACK_NIGHT_THEME, false),
                useSystemColor = ThemeHelper.isUsingSystemColor(),
                locales = locales,
                translationUrl = activity.getString(R.string.translation_url),
                copy = copyMap(),
            ),
        )
        map.putAll(extra)
        return JSONObject(map).toString()
    }

    private fun copyMap(): Map<String, Any?> {
        val c = activity
        return linkedMapOf(
            "title" to c.getString(R.string.settings_title),
            "startup" to c.getString(R.string.settings_startup),
            "startOnBoot" to c.getString(R.string.settings_start_on_boot),
            "startOnBootSummary" to c.getString(R.string.settings_start_on_boot_summary),
            "startOnBootWireless" to c.getString(R.string.settings_start_on_boot_wireless),
            "startOnBootWirelessSummary" to c.getString(R.string.settings_start_on_boot_wireless_summary),
            "autoPairing" to c.getString(R.string.settings_auto_pairing),
            "autoPairingSummary" to c.getString(R.string.settings_auto_pairing_summary),
            "watchdogAdb" to c.getString(R.string.settings_watchdog_adb),
            "watchdogAdbSummary" to c.getString(R.string.settings_watchdog_adb_summary),
            "tcpipPort" to c.getString(R.string.settings_tcpip_port),
            "tcpipPortSummary" to c.getString(R.string.settings_tcpip_port_summary),
            "tcpipPortDisabled" to c.getString(R.string.settings_tcpip_port_disabled),
            "dialogAdbInvalidPort" to c.getString(R.string.dialog_adb_invalid_port),
            "language" to c.getString(R.string.settings_language),
            "languageSystem" to c.getString(R.string.settings_language_system),
            "translationContributors" to c.getString(R.string.settings_translation_contributors),
            "translationContributorsSummary" to c.getString(R.string.translation_contributors)
                .ifBlank { c.getString(R.string.settings_translation_contributors_fallback) },
            "translation" to c.getString(R.string.settings_translation),
            "translationSummary" to c.getString(R.string.settings_translation_summary, c.getString(R.string.app_name)),
            "userInterface" to c.getString(R.string.settings_user_interface),
            "darkTheme" to c.getString(R.string.dark_theme),
            "followSystem" to c.getString(R.string.follow_system),
            "blackNightTheme" to c.getString(R.string.settings_black_night_theme),
            "blackNightThemeSummary" to c.getString(R.string.settings_black_night_theme_summary),
            "useSystemColor" to c.getString(R.string.settings_use_system_color),
            "useSystemColorSummary" to c.getString(R.string.settings_use_system_color_summary),
            "permissionMissing" to c.getString(R.string.permission_missing),
            "wirelessBootPermissionTooltip" to c.getString(R.string.wireless_boot_permission_tooltip),
            "autoPairingNotificationAccessTooltip" to c.getString(R.string.auto_pairing_notification_access_tooltip),
            "manual" to c.getString(R.string.manual),
            "cancel" to c.getString(R.string.cancel),
            "ok" to c.getString(android.R.string.ok),
        )
    }

    // ───── 写操作（逐项同 SettingsComposeScreen.onToggle / 对话框保存分支）─────

    private fun setBool(key: String, checked: Boolean): Outcome {
        val prefs = ShizukuSettings.getPreferences()
        return when (key) {
            ShizukuSettings.KEEP_START_ON_BOOT,
            ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS -> {
                if (key == ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS && checked && !hasWriteSecureSettings()) {
                    // 缺 WRITE_SECURE_SETTINGS：不落盘，只让 Dart 弹缺权限对话框。
                    val grantCmd =
                        "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
                    return Outcome(
                        snapshotJson(mapOf("needGrant" to true, "grantCmd" to grantCmd)),
                        recreate = false,
                    )
                }
                val current = BootPrefs(
                    bootRoot = prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false),
                    bootWireless = prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false),
                )
                val next = applyBootToggle(current, key, checked)
                prefs.edit {
                    putBoolean(ShizukuSettings.KEEP_START_ON_BOOT, next.bootRoot)
                    putBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, next.bootWireless)
                }
                setBootReceiverEnabled(next.bootRoot || next.bootWireless)
                Outcome(snapshotJson(), recreate = true)
            }

            ShizukuSettings.AUTO_PAIRING_ENABLED -> {
                if (checked && !NotificationListenerAccess.isEnabled(activity)) {
                    // 未开通知监听：不落盘，只让 Dart 引导到监听设置。
                    Outcome(snapshotJson(mapOf("needNotificationAccess" to true)), recreate = false)
                } else {
                    prefs.edit { putBoolean(key, checked) }
                    Outcome(snapshotJson(), recreate = false)
                }
            }

            ShizukuSettings.WATCHDOG_ENABLED_ADB -> {
                prefs.edit { putBoolean(key, checked) }
                if (checked) {
                    if (ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ADB &&
                        Shizuku.pingBinder()
                    ) {
                        WatchdogService.start(activity)
                    }
                } else {
                    WatchdogService.stop(activity)
                }
                Outcome(snapshotJson(), recreate = true)
            }

            ThemeHelper.KEY_BLACK_NIGHT_THEME,
            ThemeHelper.KEY_USE_SYSTEM_COLOR -> {
                prefs.edit { putBoolean(key, checked) }
                Outcome(snapshotJson(), recreate = true)
            }

            else -> throw IllegalArgumentException("unknown key: $key")
        }
    }

    private fun setTcpipPort(port: String): Outcome {
        val parsed = parseTcpipPort(port)
        // Compose 存的是去空白后的原字符串（非规范化整数），这里保持一致。
        val stored = if (parsed == null) "" else port.trim()
        ShizukuSettings.getPreferences().edit { putString(ShizukuSettings.TCPIP_PORT, stored) }
        return Outcome(snapshotJson(), recreate = true)
    }

    private fun setNightMode(mode: Int): Outcome {
        ShizukuSettings.getPreferences().edit { putInt(ShizukuSettings.NIGHT_MODE, mode) }
        AppCompatDelegate.setDefaultNightMode(mode)
        return Outcome(snapshotJson(), recreate = true)
    }

    private fun setLocale(tag: String): Outcome {
        val locale = if (tag == "SYSTEM") LocaleDelegate.systemLocale else Locale.forLanguageTag(tag)
        ShizukuSettings.getPreferences().edit { putString(ShizukuSettings.LANGUAGE, tag) }
        LocaleDelegate.defaultLocale = locale
        return Outcome(snapshotJson(), recreate = true)
    }

    // ───── 权限探测 / 组件开关（逐行同 SettingsComposeScreen 私有函数）─────

    private fun hasWriteSecureSettings(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setBootReceiverEnabled(enabled: Boolean) {
        val component = ComponentName(activity.packageName, BootCompleteReceiver::class.java.name)
        activity.packageManager.setComponentEnabled(component, enabled)
    }

    companion object {
        const val CHANNEL = "shizuku/settings"
        private const val WIRELESS_GUIDE_URL = "https://shizuku.rikka.app/guide/setup/"
    }
}
