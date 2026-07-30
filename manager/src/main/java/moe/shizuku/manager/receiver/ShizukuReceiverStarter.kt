package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.starter.SelfStarterService
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

object ShizukuReceiverStarter {

    fun startOnBoot(context: Context) {
        if (UserHandleCompat.myUserId() > 0 || Shizuku.pingBinder()) {
            return
        }

        val preferences = ShizukuSettings.getPreferences()
        val startOnBootRootEnabled =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val startOnBootWirelessEnabled =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        if (startOnBootRootEnabled) {
            rootStart()
            return
        }

        if (startOnBootWirelessEnabled) {
            startWireless(context, requireBootSupport = true)
            return
        }

        Log.w(AppConstants.TAG, "No support start on boot")
    }

    fun startWireless(
        context: Context,
        force: Boolean = false,
        requireBootSupport: Boolean = false
    ) {
        if (UserHandleCompat.myUserId() > 0 || (Shizuku.pingBinder() && !force)) {
            BootStartNotifications.dismiss(context)
            return
        }

        if (requireBootSupport && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(AppConstants.TAG, "Wireless boot start requires Android 13 or above")
            BootStartNotifications.showFailure(
                context,
                context.getString(R.string.wireless_boot_wifi_required)
            )
            return
        }

        val hasSecureSettingsPermission =
            context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        val startablePort = AdbWirelessHelper().getStartableAdbPort()

        if (!hasSecureSettingsPermission && startablePort == null) {
            Log.w(AppConstants.TAG, "Wireless boot worker missing WRITE_SECURE_SETTINGS")
            BootStartNotifications.showFailure(
                context,
                context.getString(R.string.permission_write_secure_settings_required)
            )
            return
        }

        val userManager = context.getSystemService(UserManager::class.java)
        val unlocked = userManager?.isUserUnlocked == true
        val wifiAdbOn = isWirelessAdbEnabled(context)

        // Hot path: burn BOOT_COMPLETED / USER_PRESENT FGS window immediately.
        // WorkManager scheduling often costs multi-seconds after unlock.
        if (unlocked && (startablePort != null || wifiAdbOn || hasSecureSettingsPermission)) {
            val autoEnable = hasSecureSettingsPermission && !wifiAdbOn
            if (startSelfStarterDirect(context, autoEnable)) {
                WifiReadyMonitor.ensureRegistered(context)
                // Port unknown: keep Worker as soft backup for late Wi‑Fi / TLS.
                if (startablePort == null) {
                    WirelessBootStartWorker.enqueue(context)
                }
                return
            }
        }

        WirelessBootStartWorker.enqueue(context)
    }

    private fun isWirelessAdbEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    private fun startSelfStarterDirect(context: Context, autoEnable: Boolean): Boolean {
        return try {
            context.startForegroundService(
                Intent(context, SelfStarterService::class.java).apply {
                    putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, autoEnable)
                    putExtra(SelfStarterService.EXTRA_FORCE_RESTART, false)
                    putExtra(
                        SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED,
                        false
                    )
                }
            )
            Log.i(AppConstants.TAG, "startWireless: direct SelfStarter (autoEnable=$autoEnable)")
            true
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "startWireless: direct SelfStarter failed, fallback Worker", e)
            false
        }
    }

    private fun rootStart() {
        if (Shell.getShell().isRoot) {
            Shell.cmd(Starter.internalCommand).exec()
            return
        }
        Shell.getCachedShell()?.close()
        // libsu 未拿到 root shell 时，再试一次原生 su -c（部分机型 Magisk 授权时机更晚）
        val cmd = Starter.internalCommand
        val ok = runCatching {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(AppConstants.TAG, "rootStart: su -c timed out")
                return@runCatching false
            }
            val code = process.exitValue()
            Log.i(AppConstants.TAG, "rootStart: su -c exit=$code")
            code == 0
        }.getOrElse { error ->
            Log.w(AppConstants.TAG, "rootStart: su -c failed", error)
            false
        }
        if (!ok) {
            Log.w(AppConstants.TAG, "rootStart: no root shell available")
        }
    }
}
