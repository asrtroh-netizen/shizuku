package moe.shizuku.manager.receiver

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku

/**
 * When wireless boot is enabled, re-enqueue [WirelessBootStartWorker] as soon as
 * Wi‑Fi transport becomes available — so users do not have to tap「重试」after late association.
 */
object WifiReadyMonitor {
    private const val DEBOUNCE_MS = 1_500L

    @Volatile
    private var registered = false

    @Volatile
    private var lastEnqueueAtMs = 0L

    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun ensureRegistered(context: Context) {
        if (registered) return
        if (ShizukuSettings.getPreferences() == null) {
            ShizukuSettings.initialize(context)
        }
        val prefs = ShizukuSettings.getPreferences() ?: return
        val wireless =
            prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
        if (!wireless) return

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
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
        val prefs = ShizukuSettings.getPreferences() ?: return
        if (!prefs.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)) return
        if (Shizuku.pingBinder()) return

        val now = System.currentTimeMillis()
        if (now - lastEnqueueAtMs < DEBOUNCE_MS) return
        lastEnqueueAtMs = now

        // Same path as notification「重试」button.
        Log.i(AppConstants.TAG, "WifiReadyMonitor: Wi‑Fi up, startWireless(force)")
        runCatching {
            ShizukuReceiverStarter.startWireless(context, force = true)
        }.onFailure {
            Log.w(AppConstants.TAG, "WifiReadyMonitor startWireless failed", it)
        }
    }
}
