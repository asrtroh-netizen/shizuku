package moe.shizuku.manager.home

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.management.AppsManagementActivity
import moe.shizuku.manager.management.GrantedCountCache
import moe.shizuku.manager.management.resolveGrantedCount
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.receiver.WifiReadyMonitor
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.update.UpdateChecker
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.watchdog.WatchdogService
import org.json.JSONArray
import org.json.JSONObject
import rikka.core.util.ClipboardUtils
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku
import rikka.shizuku.manager.ShizukuLocales
import java.util.Locale

/**
 * 原 Compose 首页的全部动作。Flutter 换皮和 [HomeActivity] 共用这一份，避免两套逻辑。
 */
class HomeActions(private val activity: Activity) {

    fun handleStartViaWadbIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(HomeActivity.EXTRA_START_SERVICE_VIA_WADB, false) != true) return
        val nm = activity.getSystemService(NotificationManager::class.java)
        nm.cancel(AdbPairingService.NOTIFICATION_ID)
        startWirelessAdb()
    }

    fun openApps() {
        activity.startActivity(Intent(activity, AppsManagementActivity::class.java))
    }

    fun openTerminal() {
        activity.startActivity(Intent(activity, ShellTutorialActivity::class.java))
    }

    fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    fun openPairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.startActivity(Intent(activity, moe.shizuku.manager.adb.AdbPairingTutorialActivity::class.java))
        }
    }

    fun openWirelessGuide() {
        CustomTabsHelper.launchUrlOrCopy(activity, Helps.ADB_ANDROID11.get())
    }

    fun openAdbPermissionHelp() {
        CustomTabsHelper.launchUrlOrCopy(activity, Helps.ADB_PERMISSION.get())
    }

    fun startRoot() {
        WatchdogService.stop(activity)
        activity.startActivity(Intent(activity, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, true)
        })
    }

    fun stopService() {
        if (!Shizuku.pingBinder()) return
        WatchdogService.stop(activity)
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
        }
    }

    fun copyAdbCommand() {
        if (ClipboardUtils.put(activity, Starter.adbCommand)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_copied_to_clipboard, Starter.adbCommand),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun sendAdbCommand() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
        }
        activity.startActivity(
            Intent.createChooser(
                intent,
                activity.getString(R.string.home_adb_dialog_view_command_button_send),
            ),
        )
    }

    fun startWirelessAdb() {
        val helper = AdbWirelessHelper()
        val customPort = helper.getConfiguredTcpipPort() ?: -1
        val systemPort = EnvironmentUtils.getAdbTcpPort()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when {
                systemPort in 1..65535 -> startWirelessAdb(systemPort)
                customPort in 1..65535 -> startWirelessAdb(customPort)
                else -> showWirelessAdbDiscoveryDialog()
            }
        } else {
            val port = if (systemPort > 0) systemPort else customPort
            if (port > 0) startWirelessAdb(port) else showWirelessAdbNotEnabledDialog()
        }
    }

    fun setBootRoot(checked: Boolean) {
        saveBool(ShizukuSettings.KEEP_START_ON_BOOT, checked)
        if (checked) saveBool(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
        val wireless = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
        setBootReceiverEnabled(checked || wireless)
    }

    fun setBootWireless(checked: Boolean): JSONObject {
        if (checked && !hasWriteSecureSettings()) {
            val grantCmd =
                "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
            return JSONObject()
                .put("ok", false)
                .put("needGrant", true)
                .put("grantCmd", grantCmd)
                .put("title", activity.getString(R.string.permission_missing))
                .put(
                    "message",
                    activity.getString(R.string.wireless_boot_permission_tooltip) + "\n\n" + grantCmd,
                )
        }
        saveBool(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, checked)
        if (checked) saveBool(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val root = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        setBootReceiverEnabled(checked || root)
        if (checked) WifiReadyMonitor.ensureRegistered(activity)
        else WifiReadyMonitor.unregister(activity)
        return JSONObject().put("ok", true)
    }

    fun setWatchdog(checked: Boolean) {
        saveBool(ShizukuSettings.WATCHDOG_ENABLED_ADB, checked)
        if (checked) {
            if (Shizuku.pingBinder()) WatchdogService.start(activity)
        } else {
            WatchdogService.stop(activity)
        }
    }

    fun toggleTheme() {
        val next = if (isDarkThemeActive()) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        ShizukuSettings.getPreferences().edit { putInt(ShizukuSettings.NIGHT_MODE, next) }
        AppCompatDelegate.setDefaultNightMode(next)
    }

    fun setLocale(tag: String) {
        val locale =
            if (tag == "SYSTEM") LocaleDelegate.systemLocale
            else Locale.forLanguageTag(tag)
        ShizukuSettings.getPreferences().edit { putString(ShizukuSettings.LANGUAGE, tag) }
        LocaleDelegate.defaultLocale = locale
    }

    fun copyText(text: String) {
        ClipboardUtils.put(activity, text)
    }

    fun checkUpdateBlocking(): JSONObject {
        val info = UpdateChecker.checkLatest(activity)
        if (info.hasUpdate && info.downloadUrl.isNotBlank()) {
            UpdateChecker.downloadAndInstall(activity, info.downloadUrl, info.latestVersion)
            return JSONObject()
                .put("ok", true)
                .put("hasUpdate", true)
                .put("message", activity.getString(R.string.update_download_started, info.latestVersion))
        }
        return JSONObject()
            .put("ok", true)
            .put("hasUpdate", false)
            .put("message", info.message)
    }

    fun snapshot(): JSONObject {
        val running = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        val uid = if (running) {
            try {
                Shizuku.getUid()
            } catch (_: Throwable) {
                -1
            }
        } else {
            -1
        }
        val permission = if (running) {
            try {
                Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") ==
                    PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }
        val grantedCount = grantedCount()
        val rooted = EnvironmentUtils.isRooted()
        val prefs = ShizukuSettings.getPreferences()
        val bootRoot = prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val bootWireless = prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
        val watchdog = prefs.getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false)
        val showWireless =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0
        val appsSub = when {
            !running -> activity.getString(R.string.home_checks_apps_sub_waiting)
            grantedCount < 0 -> activity.getString(R.string.home_app_management_binder_unavailable)
            else -> activity.resources.getQuantityString(
                R.plurals.home_app_management_authorized_apps_count,
                grantedCount,
                grantedCount,
            )
        }
        val rootSub = when {
            !rooted -> activity.getString(R.string.home_root_tile_unavailable)
            running && uid == 0 -> activity.getString(R.string.home_root_button_restart)
            else -> activity.getString(R.string.home_root_button_start)
        }
        return JSONObject()
            .put("running", running)
            .put("state", if (running) "ready" else "inactive")
            .put("uid", uid)
            .put("permission", permission)
            .put("grantedCount", grantedCount)
            .put("rooted", rooted)
            .put("rootRestart", running && uid == 0)
            .put("showWireless", showWireless)
            .put("showPair", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            .put("bootRoot", bootRoot)
            .put("bootWireless", bootWireless)
            .put("watchdog", watchdog)
            .put("dark", isDarkThemeActive())
            .put("adbLimited", running && !permission)
            .put("adbCommand", Starter.adbCommand)
            .put("appsSub", appsSub)
            .put("rootSub", rootSub)
            .put("locales", localeArray())
            .put("copy", copyJson())
    }

    private fun copyJson(): JSONObject {
        val c = activity
        return JSONObject()
            .put("appName", c.getString(R.string.app_name))
            .put("heroEyebrow", c.getString(R.string.home_hero_eyebrow))
            .put("heroTitleInactive", c.getString(R.string.home_hero_title_inactive))
            .put("heroPillInactive", c.getString(R.string.home_hero_pill_inactive))
            .put("heroPillReady", c.getString(R.string.home_hero_pill_ready))
            .put("heroSubtitle", c.getString(R.string.home_hero_subtitle_inactive))
            .put("heroDetail", c.getString(R.string.home_hero_detail_inactive))
            .put("stageInactive", c.getString(R.string.home_hero_stage_inactive))
            .put("stageReady", c.getString(R.string.home_hero_stage_ready))
            .put("quickTitle", c.getString(R.string.home_quick_entry_title))
            .put("wirelessTitle", c.getString(R.string.home_wireless_adb_title_plain))
            .put("wirelessGuide", c.getString(R.string.home_wireless_adb_view_guide_button))
            .put("pairing", c.getString(R.string.adb_pairing))
            .put("start", c.getString(R.string.home_root_button_start))
            .put("restart", c.getString(R.string.home_root_button_restart))
            .put("bootTitle", c.getString(R.string.home_capsule_boot))
            .put("bootConfigure", c.getString(R.string.home_boot_configure))
            .put("bootRoot", c.getString(R.string.settings_start_on_boot))
            .put("bootWireless", c.getString(R.string.settings_start_on_boot_wireless))
            .put("watchdog", c.getString(R.string.settings_watchdog_adb))
            .put("appsTitle", c.getString(R.string.home_app_management_title))
            .put("appsOpen", c.getString(R.string.home_app_management_view_authorized_apps))
            .put("terminalTitle", c.getString(R.string.home_terminal_title_plain))
            .put("terminalSub", c.getString(R.string.home_terminal_tile_sub))
            .put("terminalBody", c.getString(R.string.home_terminal_description))
            .put("terminalOff", c.getString(R.string.home_status_service_not_running, c.getString(R.string.app_name)))
            .put("rootTitle", c.getString(R.string.home_root_title_plain))
            .put("rootConfirm", c.getString(R.string.home_root_tile_confirm))
            .put("rootUnavailable", c.getString(R.string.home_root_tile_unavailable_detail))
            .put("adbTitle", c.getString(R.string.home_adb_tile_title))
            .put("adbSub", c.getString(R.string.home_checks_adb_sub))
            .put("adbViewCommand", c.getString(R.string.home_adb_button_view_command))
            .put("adbCopy", c.getString(R.string.home_adb_dialog_view_command_copy_button))
            .put("adbSend", c.getString(R.string.home_adb_dialog_view_command_button_send))
            .put("adbLimited", c.getString(R.string.home_adb_is_limited_title))
            .put("checkUpdate", c.getString(R.string.home_check_update))
            .put("checkingUpdate", c.getString(R.string.home_checking_update))
            .put("lang", c.getString(R.string.home_lang_chip))
            .put("language", c.getString(R.string.settings_language))
            .put("themeLight", c.getString(R.string.home_theme_light))
            .put("themeDark", c.getString(R.string.home_theme_dark))
            .put("ok", c.getString(android.R.string.ok))
            .put("cancel", c.getString(android.R.string.cancel))
    }

    private fun localeArray(): JSONArray {
        val tags = ShizukuLocales.LOCALES
        val current = ShizukuSettings.getPreferences().getString(ShizukuSettings.LANGUAGE, "SYSTEM") ?: "SYSTEM"
        val arr = JSONArray()
        tags.forEachIndexed { index, tag ->
            val label = if (index == 0) activity.getString(R.string.settings_language_system)
            else when (tag) {
                "zh-CN" -> "简体中文"
                "zh-TW" -> "繁體中文"
                "en" -> "English"
                "ja" -> "日本語"
                "ko" -> "한국어"
                else -> tag
            }
            arr.put(
                JSONObject()
                    .put("tag", tag)
                    .put("label", label)
                    .put("selected", tag == current),
            )
        }
        return arr
    }

    private fun grantedCount(): Int {
        return resolveGrantedCount(GrantedCountCache.value) {
            if (!Shizuku.pingBinder()) return@resolveGrantedCount -1
            try {
                var count = 0
                for (pi in moe.shizuku.manager.authorization.AuthorizationManager.getPackages()) {
                    val uid = pi.applicationInfo?.uid ?: continue
                    if (moe.shizuku.manager.authorization.AuthorizationManager.granted(pi.packageName, uid)) {
                        count++
                    }
                }
                count
            } catch (_: Throwable) {
                -1
            }
        }
    }

    private fun startWirelessAdb(port: Int) {
        AdbWirelessHelper().launchStarterActivity(activity, "127.0.0.1", port)
    }

    private fun showWirelessAdbNotEnabledDialog() {
        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.dialog_wireless_adb_not_enabled)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openDevelopmentSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun showWirelessAdbDiscoveryDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val discoveredPort = MutableLiveData<Int>()
        val adbMdns = AdbMdns(activity, AdbMdns.TLS_CONNECT) {
            discoveredPort.postValue(it)
        }
        val currentPort = EnvironmentUtils.getAdbTcpPort()
        var dialog: AlertDialog? = null
        val observer = Observer<Int> {
            if (it in 1..65535) {
                dialog?.dismiss()
                startWirelessAdb(it)
            }
        }
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_adb_discovery)
            .setMessage(R.string.dialog_adb_discovery_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.development_settings, null)
            .apply {
                if (currentPort in 1..65535) {
                    setNeutralButton(currentPort.toString(), null)
                }
            }
            .create()
        val adbDialog = dialog
        adbDialog.setCanceledOnTouchOutside(false)
        adbDialog.setOnShowListener {
            adbMdns.start()
            discoveredPort.observeForever(observer)
            if (activity.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Settings.Global.putInt(activity.contentResolver, "adb_wifi_enabled", 1)
                Settings.Global.putInt(activity.contentResolver, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(activity.contentResolver, "adb_allowed_connection_time", 0L)
            }
            adbDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                openDevelopmentSettings()
            }
            adbDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                adbDialog.dismiss()
                startWirelessAdb(EnvironmentUtils.getAdbTcpPort())
            }
        }
        adbDialog.setOnDismissListener {
            discoveredPort.removeObserver(observer)
            adbMdns.stop()
        }
        adbDialog.show()
    }

    private fun hasWriteSecureSettings(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDarkThemeActive(): Boolean {
        return when (ShizukuSettings.getNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun saveBool(key: String, value: Boolean) {
        ShizukuSettings.getPreferences().edit { putBoolean(key, value) }
    }

    private fun setBootReceiverEnabled(enabled: Boolean) {
        val component = ComponentName(activity.packageName, BootCompleteReceiver::class.java.name)
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        activity.packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        fun copyGrantCmd(context: Context, cmd: String) {
            ClipboardUtils.put(context, cmd)
            if (context is Activity) {
                Toast.makeText(context, cmd, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
