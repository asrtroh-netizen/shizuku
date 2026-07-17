package moe.shizuku.manager.utils

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemProperties
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import com.topjohnwu.superuser.Shell

private val appContext = ShizukuApplication.appContext

object EnvironmentUtils {

    @JvmStatic
    fun isWatch(): Boolean {
        return (appContext.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_WATCH)
    }

    @JvmStatic
    fun isTelevision(): Boolean {
        return (appContext.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_TELEVISION ||
                appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
    }

    fun isTlsSupported(): Boolean {
        return if (isTelevision())
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            else Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun isWifiRequired(): Boolean {
        return (getAdbTcpPort() <= 0 || !ShizukuSettings.getTcpMode())
    }

    /**
     * Paired once (ADB key stored) + WRITE_SECURE_SETTINGS → wireless path can autostart.
     * User rule: once paired, anything that can connect should auto-connect.
     */
    @JvmStatic
    fun canWirelessAutostart(context: Context = appContext): Boolean {
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return PreferenceAdbKeyStore(ShizukuSettings.getPreferences()).get() != null
    }

    /**
     * Enable boot/Wi‑Fi autostart after a successful pair (idempotent).
     */
    @JvmStatic
    fun enableAutostartAfterPair(context: Context = appContext) {
        if (!canWirelessAutostart(context)) return
        if (!ShizukuSettings.getStartOnBoot(context)) {
            ShizukuSettings.setStartOnBoot(context, true)
        } else {
            moe.shizuku.manager.receiver.WifiReadyMonitor.ensureRegistered(context)
        }
    }

    /**
     * True when any Wi‑Fi STA is up — not only when Wi‑Fi is the default route.
     * Old/remembered Wi‑Fi often stays connected while cellular remains
     * [ConnectivityManager.getActiveNetwork]; gating on activeNetwork alone
     * skipped boot / late-WiFi autostart and forced a manual tap.
     */
    @JvmStatic
    fun isWifiClientConnected(context: Context = appContext): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true
            }
        }
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * OneKuku-aligned: actively wait for remembered Wi‑Fi STA after reboot
     * instead of only relying on WorkManager network constraints.
     */
    @JvmStatic
    fun waitForWifiClient(context: Context = appContext, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isWifiClientConnected(context)) return true
            try {
                Thread.sleep(500L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return isWifiClientConnected(context)
    }

    fun isRooted(): Boolean {
        return Shell.getShell().isRoot
    }

    fun getAdbTcpPort(): Int {
        var port = SystemProperties.getInt("service.adb.tcp.port", -1)
        if (port == -1) port = SystemProperties.getInt("persist.adb.tcp.port", -1)
        if (port == -1 && isTelevision() && !isTlsSupported()) port = ShizukuSettings.getTcpPort()
        return port
    }
}
