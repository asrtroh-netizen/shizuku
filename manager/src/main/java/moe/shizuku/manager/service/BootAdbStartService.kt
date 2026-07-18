package moe.shizuku.manager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.WirelessAdbActivation
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker

/**
 * OneKuku-aligned boot starter: complete wireless activation **inside this FGS**.
 * Do not only enqueue WorkManager — OEM battery policy freezes WM after the boot allowlist ends.
 */
class BootAdbStartService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var foregroundStarted = false
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        val debounce = intent?.getLongExtra(EXTRA_DEBOUNCE_MS, 0L) ?: 0L
        scheduleRun(debounce)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.wadb_notification_title),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(getString(R.string.wadb_notification_title))
            .setContentText(getString(R.string.boot_adb_starting))
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun scheduleRun(debounceMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (running) return@postDelayed
            running = true
            scope.launch {
                try {
                    runBootStart()
                } finally {
                    running = false
                    stopSelf()
                }
            }
        }, debounceMs.coerceAtLeast(0L))
    }

    private suspend fun runBootStart() {
        if (ShizukuStateMachine.isRunning()) {
            Log.i(AppConstants.TAG, "BootAdbStartService: already running")
            return
        }
        if (!waitUntilUnlocked()) {
            Log.i(AppConstants.TAG, "BootAdbStartService: user not unlocked yet")
            // Still arm late Wi‑Fi path.
            AdbStartWorker.enqueue(this, replaceStuck = true)
            return
        }

        var waitedWifi = false
        if (EnvironmentUtils.isWifiRequired()) {
            ShizukuReceiverStarter.updateNotification(this, ShizukuReceiverStarter.WorkerState.AWAITING_WIFI)
            val wifiOk = withContext(Dispatchers.IO) {
                EnvironmentUtils.waitForWifiClient(this@BootAdbStartService, BOOT_WIFI_WAIT_MS)
            }
            if (!wifiOk) {
                Log.i(AppConstants.TAG, "BootAdbStartService: waiting Wi‑Fi, will retry on NETWORK_STATE")
                ShizukuReceiverStarter.updateNotification(this, ShizukuReceiverStarter.WorkerState.AWAITING_WIFI)
                AdbStartWorker.enqueue(this, replaceStuck = true)
                return
            }
            waitedWifi = true
            ensureAdbWifiWithSettle()
        }

        Log.i(AppConstants.TAG, "BootAdbStartService: activate in FGS")
        ShizukuReceiverStarter.updateNotification(this, ShizukuReceiverStarter.WorkerState.RUNNING)
        val ok = runCatching {
            WirelessAdbActivation.activate(this, alreadyWaitedWifi = waitedWifi)
        }.onFailure {
            Log.w(AppConstants.TAG, "BootAdbStartService: in-FGS activate failed", it)
        }.getOrDefault(false)

        if (ok) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)
            Log.i(AppConstants.TAG, "BootAdbStartService: binder ready")
            return
        }

        Log.w(AppConstants.TAG, "BootAdbStartService: fallback WorkManager")
        AdbStartWorker.enqueue(this, replaceStuck = true)
    }

    private suspend fun waitUntilUnlocked(): Boolean {
        val um = getSystemService(UserManager::class.java) ?: return true
        val deadline = System.currentTimeMillis() + UNLOCK_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (um.isUserUnlocked) return true
            delay(UNLOCK_POLL_MS)
        }
        return um.isUserUnlocked
    }

    private suspend fun ensureAdbWifiWithSettle() {
        val cr = contentResolver
        val wasOn = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
        runCatching {
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        }
        if (!wasOn) {
            delay(POST_WIRELESS_ENABLE_MS)
        }
    }

    companion object {
        private const val CHANNEL = "shizuku_boot_adb_fg"
        private const val NOTIF_ID = 1450
        const val EXTRA_DEBOUNCE_MS = "debounce_ms"
        // Keep short: late Wi‑Fi still retries via NETWORK_STATE → BootAdbStartService.
        private const val BOOT_WIFI_WAIT_MS = 12_000L
        private const val POST_WIRELESS_ENABLE_MS = 1_200L
        private const val UNLOCK_WAIT_MS = 90_000L
        private const val UNLOCK_POLL_MS = 500L

        fun enqueue(context: Context, debounceMs: Long = 0L) {
            val intent = Intent(context, BootAdbStartService::class.java)
                .putExtra(EXTRA_DEBOUNCE_MS, debounceMs)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                Log.w(AppConstants.TAG, "BootAdbStartService enqueue failed, fallback WorkManager", it)
                AdbStartWorker.enqueue(context, replaceStuck = true)
            }
        }
    }
}
