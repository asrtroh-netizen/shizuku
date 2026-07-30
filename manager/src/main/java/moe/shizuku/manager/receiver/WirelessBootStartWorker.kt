package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.starter.SelfStarterService
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

class WirelessBootStartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val adbWirelessHelper = AdbWirelessHelper()

    override suspend fun doWork(): Result {
        if (Shizuku.pingBinder()) {
            UserPresentRestartReceiver.setEnabled(applicationContext, false)
            BootStartNotifications.dismiss(applicationContext)
            return Result.success()
        }

        val startablePort = adbWirelessHelper.getStartableAdbPort()
        val hasSecureSettingsPermission =
            applicationContext.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        if (!hasSecureSettingsPermission && startablePort == null) {
            Log.w(AppConstants.TAG, "Wireless boot worker missing WRITE_SECURE_SETTINGS")
            BootStartNotifications.showFailure(
                applicationContext,
                applicationContext.getString(moe.shizuku.manager.R.string.permission_write_secure_settings_required)
            )
            return Result.failure()
        }

        // Use CE unlock (UserManager), not KeyguardManager.isDeviceLocked:
        // on Pixel Fold / some devices isDeviceLocked stays true after the UI is usable,
        // which painted "Waiting for unlock" forever and missed USER_PRESENT.
        val userManager =
            applicationContext.getSystemService(Context.USER_SERVICE) as UserManager
        if (!userManager.isUserUnlocked) {
            Log.i(AppConstants.TAG, "Wireless boot worker waiting for CE user unlock")
            UserPresentRestartReceiver.setEnabled(applicationContext, true)
            BootStartNotifications.showFailure(
                applicationContext,
                applicationContext.getString(moe.shizuku.manager.R.string.boot_start_waiting_for_unlock)
            )
            return Result.success()
        }

        UserPresentRestartReceiver.setEnabled(applicationContext, false)

        val wifiAdbAlreadyOn = try {
            Settings.Global.getInt(applicationContext.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Throwable) {
            false
        }

        // Skip the ~20s STA poll when wireless ADB is already on (common after first boot).
        val wirelessAdbEnabled = when {
            !hasSecureSettingsPermission -> false
            wifiAdbAlreadyOn -> true
            else -> try {
                adbWirelessHelper.validateThenEnableWirelessAdb(
                    applicationContext.contentResolver,
                    applicationContext,
                    true
                )
            } catch (e: SecurityException) {
                Log.w(
                    AppConstants.TAG,
                    "Wireless boot worker permission denied enabling wireless ADB",
                    e
                )
                BootStartNotifications.showFailure(
                    applicationContext,
                    applicationContext.getString(moe.shizuku.manager.R.string.permission_write_secure_settings_required)
                )
                return Result.failure()
            }
        }

        if (!wirelessAdbEnabled && startablePort == null) {
            val attempt = runAttemptCount
            Log.i(AppConstants.TAG, "Wireless boot worker waiting for Wi-Fi or TCP ADB port (attempt: $attempt)")
            BootStartNotifications.showWaitingForNetwork(applicationContext)
            WifiReadyMonitor.ensureRegistered(applicationContext)
            // Never permanent-fail on Wi‑Fi wait — that forced users to tap「重试」.
            // Keep retrying with backoff; WifiReadyMonitor also re-enqueues when STA is up.
            return Result.retry()
        }

        BootStartNotifications.showConnecting(applicationContext)
        try {
            applicationContext.startForegroundService(
                Intent(applicationContext, SelfStarterService::class.java).apply {
                    // Worker already enabled wireless ADB when needed — do not make
                    // SelfStarter burn another STA wait loop.
                    putExtra(
                        SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING,
                        false
                    )
                    putExtra(SelfStarterService.EXTRA_FORCE_RESTART, false)
                    // Keep wireless ADB on through the connect window; SelfStarter only
                    // disables on failed starts when explicitly requested.
                    putExtra(
                        SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED,
                        false
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Failed to start SelfStarterService from worker", e)
            BootStartNotifications.showFailure(
                applicationContext,
                e.message ?: "SelfStarterService"
            )
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "wireless_boot_start"

        fun getStartableAdbPort(): Int? = AdbWirelessHelper().getStartableAdbPort()

        fun enqueue(context: Context) {
            // CONNECTED (not UNMETERED): some OEMs mark Wi‑Fi as metered, which left
            // the worker permanently "Waiting for Wi‑Fi" despite Wi‑Fi being up.
            // Actual Wi‑Fi STA readiness is still checked inside doWork / AdbWirelessHelper.
            val constraints = if (getStartableAdbPort() == null) {
                BootStartNotifications.showWaitingForNetwork(context)
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            } else {
                BootStartNotifications.showConnecting(context)
                Constraints.NONE
            }

            val request = OneTimeWorkRequestBuilder<WirelessBootStartWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            WifiReadyMonitor.ensureRegistered(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            UserPresentRestartReceiver.setEnabled(context, false)
            BootStartNotifications.dismiss(context)
        }
    }
}
