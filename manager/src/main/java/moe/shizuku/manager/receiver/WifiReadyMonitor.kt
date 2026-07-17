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

/**
 * Process-local Wi‑Fi STA callback: when start-on-boot is on and Shizuku is not running,
 * retry start once Wi‑Fi client connects (survives late Wi‑Fi after BOOT_COMPLETED).
 */
object WifiReadyMonitor {
    private const val DEBOUNCE_MS = 3_000L

    @Volatile
    private var registered = false

    @Volatile
    private var lastEnqueueAtMs = 0L

    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun ensureRegistered(context: Context) {
        if (registered) return
        if (!ShizukuSettings.getStartOnBoot(context)) return

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
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) {
                    maybeRetry(app)
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, cb)
            callback = cb
            registered = true
            Log.i(AppConstants.TAG, "WifiReadyMonitor registered")
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
        if (!ShizukuSettings.getStartOnBoot(context)) return
        if (ShizukuStateMachine.isRunning()) return
        if (EnvironmentUtils.isWifiRequired() && !EnvironmentUtils.isWifiClientConnected(context)) return

        val now = System.currentTimeMillis()
        if (now - lastEnqueueAtMs < DEBOUNCE_MS) return
        lastEnqueueAtMs = now

        Log.i(AppConstants.TAG, "WifiReadyMonitor: Wi‑Fi up, retry start")
        ShizukuReceiverStarter.start(context)
    }
}
