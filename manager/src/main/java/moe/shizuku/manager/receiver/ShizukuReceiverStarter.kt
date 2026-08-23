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

internal enum class RootStartMethod {
    LIBSU,
    NATIVE_SU,
}

internal sealed interface RootStartResult {
    val method: RootStartMethod

    data class Success(
        override val method: RootStartMethod,
        val exitCode: Int,
    ) : RootStartResult

    data class Failure(
        override val method: RootStartMethod,
        val exitCode: Int? = null,
        val error: Throwable? = null,
    ) : RootStartResult

    companion object {
        fun success(method: RootStartMethod, exitCode: Int = 0) =
            Success(method, exitCode)

        fun failure(
            method: RootStartMethod,
            exitCode: Int? = null,
            error: Throwable? = null,
        ) = Failure(method, exitCode, error)
    }
}

internal fun runRootStartAttempt(
    hasRootShell: () -> Boolean,
    resetCachedShell: () -> Unit,
    executeLibsu: () -> Int,
    executeNativeSu: () -> RootStartResult,
): RootStartResult {
    fun libsuResult(): RootStartResult = runCatching { executeLibsu() }
        .fold(
            { code ->
                if (code == 0) RootStartResult.success(RootStartMethod.LIBSU, code)
                else RootStartResult.failure(RootStartMethod.LIBSU, code)
            },
            { error -> RootStartResult.failure(RootStartMethod.LIBSU, error = error) },
        )

    if (runCatching(hasRootShell).getOrDefault(false)) {
        return libsuResult()
    }

    runCatching(resetCachedShell)
    if (runCatching(hasRootShell).getOrDefault(false)) {
        return libsuResult()
    }

    return runCatching(executeNativeSu).getOrElse { error ->
        RootStartResult.failure(RootStartMethod.NATIVE_SU, error = error)
    }
}

object ShizukuReceiverStarter {

    fun startOnBoot(context: Context) {
        if (UserHandleCompat.myUserId() > 0) {
            UserPresentRestartReceiver.setEnabled(context, false)
            return
        }
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            UserPresentRestartReceiver.setEnabled(context, false)
            return
        }

        val preferences = ShizukuSettings.getPreferences()
        val startOnBootRootEnabled =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val startOnBootWirelessEnabled =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        if (startOnBootRootEnabled) {
            RootBootStartWorker.enqueue(context)
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

    internal fun startRootNow(): RootStartResult = runRootStartAttempt(
        hasRootShell = { Shell.getShell().isRoot },
        resetCachedShell = { Shell.getCachedShell()?.close() },
        executeLibsu = { Shell.cmd(Starter.internalCommand).exec().code },
        executeNativeSu = { executeNativeRootCommand() },
    )

    private fun executeNativeRootCommand(): RootStartResult {
        val cmd = Starter.internalCommand
        return runCatching {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(AppConstants.TAG, "rootStart: su -c timed out")
                return@runCatching RootStartResult.failure(RootStartMethod.NATIVE_SU)
            }
            val code = process.exitValue()
            Log.i(AppConstants.TAG, "rootStart: su -c exit=$code")
            if (code == 0) RootStartResult.success(RootStartMethod.NATIVE_SU, code)
            else RootStartResult.failure(RootStartMethod.NATIVE_SU, code)
        }.getOrElse { error ->
            Log.w(AppConstants.TAG, "rootStart: su -c failed", error)
            RootStartResult.failure(RootStartMethod.NATIVE_SU, error = error)
        }
    }
}
