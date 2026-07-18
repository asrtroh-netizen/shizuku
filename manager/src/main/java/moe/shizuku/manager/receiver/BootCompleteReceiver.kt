package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker

/**
 * HSSkyBoy-aligned boot trigger:
 * BOOT / LOCKED_BOOT → WorkManager (UNMETERED) → SelfStarterService.
 * NETWORK_STATE kept as a late Wi‑Fi kick (same worker).
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                try {
                    WifiReadyMonitor.ensureRegistered(context)
                    if (ShizukuSettings.getWatchdog()) {
                        WatchdogService.start(context)
                    }
                    if (!ShizukuSettings.getStartOnBoot(context) &&
                        !EnvironmentUtils.canWirelessAutostart(context)
                    ) {
                        Log.w(AppConstants.TAG, "boot: autostart not enabled")
                        return
                    }
                    Log.i(AppConstants.TAG, "boot: enqueue AdbStartWorker (HSSkyBoy path)")
                    AdbStartWorker.enqueue(context, replaceStuck = true)
                } finally {
                    pending.finish()
                }
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

                Log.i(AppConstants.TAG, "wifi connected → AdbStartWorker")
                AdbStartWorker.enqueue(context, replaceStuck = true)
            }
        }
    }
}
