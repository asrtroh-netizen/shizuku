package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.starter.SelfStarterService

class BootStartActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RETRY -> retryNow(context)
                    ACTION_CANCEL -> WirelessBootStartWorker.cancel(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun retryNow(context: Context) {
        val app = context.applicationContext
        Log.i(AppConstants.TAG, "BootStartActionReceiver retryNow")
        BootStartNotifications.showConnecting(app)

        // Same outcome as in-app「启动」: enable wireless ADB then start headless service
        // immediately. Also re-enqueue WorkManager as a safety net.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AdbWirelessHelper().validateThenEnableWirelessAdb(
                    app.contentResolver,
                    app,
                    true
                )
            }
        } catch (e: SecurityException) {
            Log.w(AppConstants.TAG, "Retry: WRITE_SECURE_SETTINGS denied", e)
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "Retry: enable wireless ADB failed", e)
        }

        val knownPort = AdbWirelessHelper().getStartableAdbPort()
        Log.i(AppConstants.TAG, "Retry: startablePort=$knownPort")

        val serviceIntent = Intent(app, SelfStarterService::class.java).apply {
            putExtra(SelfStarterService.EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, true)
            putExtra(SelfStarterService.EXTRA_FORCE_RESTART, false)
            putExtra(SelfStarterService.EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED, false)
        }
        try {
            app.startForegroundService(serviceIntent)
            Log.i(AppConstants.TAG, "Retry: startForegroundService issued")
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "Retry: startForegroundService failed, enqueue worker", e)
            ShizukuReceiverStarter.startWireless(app, force = true)
            return
        }

        ShizukuReceiverStarter.startWireless(app, force = true)
    }

    companion object {
        const val ACTION_RETRY = "moe.shizuku.manager.action.BOOT_START_RETRY"
        const val ACTION_CANCEL = "moe.shizuku.manager.action.BOOT_START_CANCEL"
    }
}
