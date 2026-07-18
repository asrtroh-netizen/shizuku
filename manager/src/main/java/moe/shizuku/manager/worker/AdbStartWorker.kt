package moe.shizuku.manager.worker

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.util.concurrent.TimeUnit
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.WorkerState
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.updateNotification
import moe.shizuku.manager.receiver.UserPresentRestartReceiver
import moe.shizuku.manager.starter.SelfStarterService
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * HSSkyBoy-aligned wireless boot worker:
 * - UNMETERED constraint when no TCP port (system waits for Wi‑Fi)
 * - no in-app 20s Wi‑Fi busy-wait
 * - unlock → [UserPresentRestartReceiver]
 * - then start [SelfStarterService] for mDNS + AdbStarter
 */
class AdbStartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ShizukuStateMachine.isRunning()) {
            UserPresentRestartReceiver.setEnabled(applicationContext, false)
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm?.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)
            return Result.success()
        }

        val startablePort = EnvironmentUtils.getAdbTcpPort().takeIf { it > 0 }
        val hasSecureSettings =
            applicationContext.checkSelfPermission(WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasSecureSettings && startablePort == null) {
            Log.w(AppConstants.TAG, "AdbStartWorker: missing WRITE_SECURE_SETTINGS")
            ShizukuReceiverStarter.showPermissionErrorNotification(applicationContext)
            return Result.failure()
        }

        val keyguard =
            applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isDeviceLocked) {
            Log.i(AppConstants.TAG, "AdbStartWorker: waiting for unlock")
            UserPresentRestartReceiver.setEnabled(applicationContext, true)
            updateNotification(applicationContext, WorkerState.AWAITING_RETRY)
            return Result.success()
        }
        UserPresentRestartReceiver.setEnabled(applicationContext, false)

        val wirelessAdbEnabled = if (hasSecureSettings) {
            try {
                enableWirelessIfWifiReady()
            } catch (e: SecurityException) {
                Log.w(AppConstants.TAG, "AdbStartWorker: enable wireless denied", e)
                ShizukuReceiverStarter.showPermissionErrorNotification(applicationContext)
                return Result.failure()
            }
        } else {
            false
        }

        if (!wirelessAdbEnabled && startablePort == null) {
            val attempt = runAttemptCount
            Log.i(AppConstants.TAG, "AdbStartWorker: waiting Wi‑Fi/TCP (attempt=$attempt)")
            if (attempt > 5) {
                updateNotification(applicationContext, WorkerState.AWAITING_WIFI)
                return Result.failure()
            }
            updateNotification(applicationContext, WorkerState.AWAITING_WIFI)
            return Result.retry()
        }

        updateNotification(applicationContext, WorkerState.RUNNING)
        val intent = Intent(applicationContext, SelfStarterService::class.java).apply {
            putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, wirelessAdbEnabled)
            putExtra(
                SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED,
                wirelessAdbEnabled
            )
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        }.onFailure {
            Log.w(AppConstants.TAG, "AdbStartWorker: start SelfStarterService failed", it)
            return Result.retry()
        }
        return Result.success()
    }

    private fun enableWirelessIfWifiReady(): Boolean {
        if (EnvironmentUtils.isWifiRequired() &&
            !EnvironmentUtils.isWifiClientConnected(applicationContext)
        ) {
            return false
        }
        val cr = applicationContext.contentResolver
        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
        Log.i(AppConstants.TAG, "AdbStartWorker: wireless debugging enabled")
        return true
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "adb_start_worker"

        fun enqueue(context: Context, replaceStuck: Boolean = false) {
            val tcpReady = EnvironmentUtils.getAdbTcpPort() > 0
            val constraints = if (!tcpReady && EnvironmentUtils.isWifiRequired()) {
                updateNotification(context, WorkerState.AWAITING_WIFI)
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            } else {
                updateNotification(context, WorkerState.RUNNING)
                Constraints.NONE
            }

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            val policy =
                if (replaceStuck) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            UserPresentRestartReceiver.setEnabled(context, false)
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)
        }

        const val CHANNEL_ID = "AdbStartWorker"
        const val NOTIFICATION_ID = 1448
    }
}
