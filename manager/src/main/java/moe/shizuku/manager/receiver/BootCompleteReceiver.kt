package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings

/**
 * Boot trigger for wireless / root auto-start, with Direct Boot safety:
 * On [Intent.ACTION_LOCKED_BOOT_COMPLETED] WorkManager / CE storage may be unavailable —
 * only arm [UserPresentRestartReceiver]. Real start runs on unlock / [Intent.ACTION_BOOT_COMPLETED].
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }

        if (ShizukuSettings.getPreferences() == null) {
            ShizukuSettings.initialize(context)
        }

        val prefs = ShizukuSettings.getPreferences()
        val rootBoot = prefs?.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false) == true
        val wirelessBoot =
            prefs?.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false) == true
        if (!rootBoot && !wirelessBoot) {
            Log.w(AppConstants.TAG, "boot: autostart not enabled (action=$action)")
            return
        }

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i(AppConstants.TAG, "locked boot: arm USER_PRESENT (skip WorkManager)")
            UserPresentRestartReceiver.setEnabled(context, true)
            return
        }

        if (wirelessBoot) {
            WifiReadyMonitor.ensureRegistered(context)
        }
        // Keep USER_PRESENT armed until the selected worker confirms Binder readiness.
        // Unlock, Wi-Fi and Magisk may all lag BOOT_COMPLETED on real devices.
        UserPresentRestartReceiver.setEnabled(context, true)

        Log.i(AppConstants.TAG, "boot: startOnBoot")
        runCatching {
            ShizukuReceiverStarter.startOnBoot(context)
        }.onFailure {
            Log.w(AppConstants.TAG, "boot: startOnBoot failed, arm USER_PRESENT", it)
            UserPresentRestartReceiver.setEnabled(context, true)
        }
    }
}
