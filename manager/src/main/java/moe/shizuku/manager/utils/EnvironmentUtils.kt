package moe.shizuku.manager.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemProperties
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

object EnvironmentUtils {

    /** adbd / shell uid — wireless debugging TLS listener is owned by this uid. */
    private const val ADBD_UID = 2000

    /** /proc/net/tcp* st field: TCP_LISTEN */
    private const val TCP_LISTEN = "0A"

    @JvmStatic
    fun isWatch(context: Context): Boolean {
        return (context.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_WATCH)
    }

    @JvmStatic
    fun isTelevision(context: Context): Boolean {
        return (context.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_TELEVISION)
    }

    fun isRooted(): Boolean {
        return System.getenv("PATH")?.split(File.pathSeparatorChar)?.find { File("$it/su").exists() } != null
    }

    fun getAdbTcpPort(): Int {
        var port = SystemProperties.getInt("service.adb.tcp.port", -1)
        if (port == -1) port = SystemProperties.getInt("persist.adb.tcp.port", -1)
        return port
    }

    /**
     * Discover adbd listening ports that classic `service.adb.tcp.port` does not expose.
     * Pixel wireless debugging uses a TLS connect port (e.g. 35051) owned by uid 2000;
     * without this, boot/retry stay on "Waiting for … TCP ADB port" forever.
     */
    fun findAdbdListeningPorts(): List<Int> {
        val ports = linkedSetOf<Int>()
        for (path in arrayOf("/proc/net/tcp", "/proc/net/tcp6")) {
            val file = File(path)
            if (!file.canRead()) continue
            runCatching {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.readLine() // header
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.trim().split(Regex("\\s+"))
                        if (parts.size < 8) continue
                        if (!parts[3].equals(TCP_LISTEN, ignoreCase = true)) continue
                        val uid = parts[7].toIntOrNull() ?: continue
                        if (uid != ADBD_UID) continue
                        val local = parts[1]
                        val colon = local.lastIndexOf(':')
                        if (colon < 0 || colon == local.lastIndex) continue
                        val portHex = local.substring(colon + 1)
                        val port = portHex.toIntOrNull(16) ?: continue
                        if (port in 1..65535) ports.add(port)
                    }
                }
            }
        }
        return ports.toList()
    }

    /** Prefer a non-classic wireless TLS-style port when several adbd listeners exist. */
    fun findAdbdWirelessPort(): Int {
        val ports = findAdbdListeningPorts()
        if (ports.isEmpty()) return -1
        val classic = getAdbTcpPort()
        return ports.firstOrNull { it != classic && it != 5555 } ?: ports.first()
    }

    /**
     * True when something is already bound on 127.0.0.1:[port] (same heuristic as AdbMdns).
     * Used to reject stale last-port prefs after reboot when TLS port changed.
     */
    fun isLocalPortInUse(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            ServerSocket().use {
                it.bind(InetSocketAddress("127.0.0.1", port), 1)
                false
            }
        } catch (_: IOException) {
            true
        }
    }
}
