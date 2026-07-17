package moe.shizuku.manager.adb

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import java.util.concurrent.TimeoutException

/**
 * In-process wireless ADB activation (OneKuku-aligned).
 * Boot FGS must call this directly — WorkManager alone is frozen by OEM battery policy.
 */
object WirelessAdbActivation {
    private const val POST_WIRELESS_ENABLE_MS = 2_400L
    private const val MDNS_DISCOVERY_TIMEOUT_MS = 30_000L
    private const val WIFI_WAIT_MS = 20_000L

    /**
     * @return true when binder is running after activation.
     */
    suspend fun activate(context: Context, alreadyWaitedWifi: Boolean = false): Boolean {
        val app = context.applicationContext
        if (ShizukuStateMachine.isRunning()) {
            Log.i(AppConstants.TAG, "WirelessAdbActivation: already running")
            return true
        }

        if (EnvironmentUtils.isWifiRequired()) {
            if (!alreadyWaitedWifi && !EnvironmentUtils.isWifiClientConnected(app)) {
                val wifiOk = withContext(Dispatchers.IO) {
                    EnvironmentUtils.waitForWifiClient(app, WIFI_WAIT_MS)
                }
                if (!wifiOk) {
                    Log.w(AppConstants.TAG, "WirelessAdbActivation: Wi‑Fi not ready")
                    return false
                }
            }
            val crWifi = app.contentResolver
            val wifiWasOn = Settings.Global.getInt(crWifi, "adb_wifi_enabled", 0) == 1
            Settings.Global.putInt(crWifi, "adb_wifi_enabled", 1)
            if (!wifiWasOn) {
                delay(POST_WIRELESS_ENABLE_MS)
            }
        }

        val cr = app.contentResolver
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)

        val tcpPort = EnvironmentUtils.getAdbTcpPort()
        if (tcpPort > 0 && !ShizukuSettings.getTcpMode()) {
            AdbStarter.stopTcp(app, tcpPort)
        }

        val port = tcpPort.takeIf { !EnvironmentUtils.isWifiRequired() } ?: discoverMdnsPort(app)

        Log.i(AppConstants.TAG, "WirelessAdbActivation: AdbStarter on port=$port")
        AdbStarter.startAdb(app, port)
        Starter.waitForBinder()
        val ok = ShizukuStateMachine.isRunning()
        Log.i(AppConstants.TAG, "WirelessAdbActivation: binder ok=$ok")
        return ok
    }

    private suspend fun discoverMdnsPort(context: Context): Int = callbackFlow {
        val cr = context.contentResolver
        val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { p ->
            if (p.second > 0) trySend(p.second)
        }

        var awaitingAuth = false
        var timeoutJob: Job? = null
        var unlockReceiver: BroadcastReceiver? = null

        fun startDiscoveryWithTimeout() {
            adbMdns.start()
            timeoutJob?.cancel()
            timeoutJob = launch {
                delay(MDNS_DISCOVERY_TIMEOUT_MS)
                close(TimeoutException("Timed out during mDNS port discovery"))
            }
        }

        fun handleAuth() {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                unlockReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == Intent.ACTION_USER_PRESENT) {
                            ctx.unregisterReceiver(this)
                            unlockReceiver = null
                            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                        }
                    }
                }
                context.registerReceiver(unlockReceiver, filter)
            } else {
                awaitingAuth = true
            }
            timeoutJob?.cancel()
            adbMdns.stop()
        }

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                    0 -> if (awaitingAuth) {
                        close(SecurityException("Network is not authorized for wireless debugging"))
                    } else {
                        handleAuth()
                    }
                    1 -> startDiscoveryWithTimeout()
                }
            }
        }

        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        cr.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, observer)
        startDiscoveryWithTimeout()

        awaitClose {
            adbMdns.stop()
            timeoutJob?.cancel()
            cr.unregisterContentObserver(observer)
            unlockReceiver?.let { context.unregisterReceiver(it) }
        }
    }.first()
}
