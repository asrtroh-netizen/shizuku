package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker

/**
 * HSSkyBoy-aligned boot trigger.
 *
 * Direct-boot note: on [Intent.ACTION_LOCKED_BOOT_COMPLETED] credential-encrypted
 * storage / WorkManager may be unavailable — do **not** touch WorkManager there
 * (cold-boot crash: "WorkManager is not initialized properly"). Only arm
 * [UserPresentRestartReceiver]; real enqueue happens after unlock / BOOT_COMPLETED.
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(AppConstants.TAG, "locked boot: arm USER_PRESENT (skip WorkManager)")
                if (ShizukuSettings.getStartOnBoot(context) ||
                    EnvironmentUtils.canWirelessAutostart(context)
                ) {
                    UserPresentRestartReceiver.setEnabled(context, true)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                WifiReadyMonitor.ensureRegistered(context)
                if (ShizukuSettings.getWatchdog()) {
                    runCatching { WatchdogService.start(context) }
                        .onFailure {
                            Log.w(AppConstants.TAG, "boot: WatchdogService start failed", it)
                        }
                }
                if (!ShizukuSettings.getStartOnBoot(context) &&
                    !EnvironmentUtils.canWirelessAutostart(context)
                ) {
                    Log.w(AppConstants.TAG, "boot: autostart not enabled")
                    return
                }
                enqueueAutostart(context, "boot")
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                if (!ShizukuSettings.getStartOnBoot(context) &&
                    !EnvironmentUtils.canWirelessAutostart(context)
                ) return
                if (ShizukuStateMachine.isRunning()) return
                @Suppress("DEPRECATION")
                val info = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                } else {
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                }
                if (info?.isConnected != true) return
                if (EnvironmentUtils.isWifiRequired() &&
                    !EnvironmentUtils.isWifiClientConnected(context)
                ) return

                enqueueAutostart(context, "wifi")
            }
        }
    }

    private fun enqueueAutostart(context: Context, reason: String) {
        val um = context.getSystemService(UserManager::class.java)
        if (um != null && !um.isUserUnlocked) {
            Log.i(AppConstants.TAG, "$reason: user locked → USER_PRESENT")
            UserPresentRestartReceiver.setEnabled(context, true)
            return
        }
        Log.i(AppConstants.TAG, "$reason: enqueue AdbStartWorker (HSSkyBoy path)")
        runCatching {
            AdbStartWorker.enqueue(context, replaceStuck = true)
        }.onFailure {
            Log.w(AppConstants.TAG, "$reason: WorkManager enqueue failed, arm USER_PRESENT", it)
            UserPresentRestartReceiver.setEnabled(context, true)
        }
    }
}
