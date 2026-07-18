package moe.shizuku.manager.receiver

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker

/**
 * Process-local Wi‑Fi STA callback: once paired (key + WSS), retry start when Wi‑Fi is up.
 */
object WifiReadyMonitor {
    private const val DEBOUNCE_MS = 1_000L

    @Volatile
    private var registered = false

    @Volatile
    private var lastEnqueueAtMs = 0L

    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun ensureRegistered(context: Context) {
        if (registered) return
        // Prefer start-on-boot; also register when already paired so late Wi‑Fi still reconnects.
        if (!ShizukuSettings.getStartOnBoot(context) && !EnvironmentUtils.canWirelessAutostart(context)) {
            return
        }

        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                maybeRetry(app)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    maybeRetry(app)
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, cb)
            callback = cb
            registered = true
            Log.i(AppConstants.TAG, "WifiReadyMonitor registered")
            maybeRetry(app)
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "WifiReadyMonitor register failed", e)
        }
    }

    @Synchronized
    fun unregister(context: Context) {
        if (!registered) return
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val cb = callback
        if (cm != null && cb != null) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        callback = null
        registered = false
    }

    private fun maybeRetry(context: Context) {
        if (!EnvironmentUtils.canWirelessAutostart(context) && !ShizukuSettings.getStartOnBoot(context)) return
        if (ShizukuStateMachine.isRunning()) return
        if (EnvironmentUtils.isWifiRequired() && !EnvironmentUtils.isWifiClientConnected(context)) return

        val now = System.currentTimeMillis()
        if (now - lastEnqueueAtMs < DEBOUNCE_MS) return
        lastEnqueueAtMs = now

        Log.i(AppConstants.TAG, "WifiReadyMonitor: Wi‑Fi up, AdbStartWorker")
        AdbStartWorker.enqueue(context, replaceStuck = true)
    }
}
