package moe.shizuku.manager.starter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.receiver.BootStartNotifications
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.shizuku.Shizuku

/**
 * Headless wireless start used by boot / retry / watchdog.
 *
 * Must use specialUse FGS (not shortService). AdbClient work runs on [appScope] so it
 * survives service teardown. When a port is known we also try [StarterActivity] (same as
 * tapping「启动」); if BAL blocks the Activity we stay on the headless path.
 */
class SelfStarterService : Service(), LifecycleOwner {

    companion object {
        const val EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING =
            "moe.shizuku.manager.extra.AUTO_ENABLE_WIRELESS_DEBUGGING"
        const val EXTRA_FORCE_RESTART =
            "moe.shizuku.manager.extra.FORCE_RESTART"
        const val EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED =
            "moe.shizuku.manager.extra.DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED"
        const val EXTRA_STARTED_BY_WATCHDOG =
            "moe.shizuku.manager.extra.STARTED_BY_WATCHDOG"

        private const val PORT_WAIT_TIMEOUT_MS = 120_000L
        /** Fast poll while TLS port is coming up after wireless ADB enable. */
        private const val PORT_POLL_INTERVAL_MS = 400L

        /** Survives SelfStarterService destruction so AdbClient can finish. */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val portLive = MutableLiveData<Int>()
    private var adbMdns: AdbMdns? = null
    private val adbWirelessHelper = AdbWirelessHelper()
    private var disableWirelessDebuggingWhenFinished = false
    private var pollJob: Job? = null

    @Volatile
    private var starting = false

    private val portObserver = Observer<Int> { p ->
        if (p in 1..65535) {
            Log.i(AppConstants.TAG, "Discovered adb port via mDNS: $p")
            startShizukuWithPort("127.0.0.1", p)
        } else {
            Log.w(AppConstants.TAG, "mDNS returned invalid port: $p")
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        startServiceNotification()
        Log.i(AppConstants.TAG, "SelfStarterService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        Log.i(AppConstants.TAG, "SelfStarterService starting command")

        val forceRestart = intent?.getBooleanExtra(EXTRA_FORCE_RESTART, false) == true
        disableWirelessDebuggingWhenFinished =
            intent?.getBooleanExtra(EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED, false) == true
        val startedByWatchdog = intent?.getBooleanExtra(EXTRA_STARTED_BY_WATCHDOG, false) == true

        if (startedByWatchdog) {
            Log.i(AppConstants.TAG, "SelfStarterService invoked by WatchdogService")
        }

        if (Shizuku.pingBinder()) {
            if (!forceRestart) {
                Log.i(AppConstants.TAG, "Shizuku is already running, stopping service.")
                BootStartNotifications.dismiss(this)
                stopSelf()
                return START_NOT_STICKY
            }
            Log.i(AppConstants.TAG, "Shizuku is running, forcing stop before restart.")
            try {
                Shizuku.exit()
                Thread.sleep(300)
            } catch (tr: Throwable) {
                Log.w(AppConstants.TAG, "Failed to force stop Shizuku before restart", tr)
            }
        }

        BootStartNotifications.showConnecting(this)

        val autoEnableWirelessDebugging =
            intent?.getBooleanExtra(EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, false) == true
        if (autoEnableWirelessDebugging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyOn = try {
                Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
            } catch (_: Throwable) {
                false
            }
            if (alreadyOn) {
                Log.i(AppConstants.TAG, "Wireless ADB already on, skip STA wait")
            } else {
                try {
                    adbWirelessHelper.validateThenEnableWirelessAdb(contentResolver, this, true)
                } catch (e: SecurityException) {
                    Log.w(AppConstants.TAG, "Permission denied enabling wireless ADB", e)
                    BootStartNotifications.showFailure(
                        this,
                        getString(R.string.permission_write_secure_settings_required)
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        val knownPort = adbWirelessHelper.getStartableAdbPort()
        if (knownPort != null) {
            Log.i(AppConstants.TAG, "Using known ADB TCP port: $knownPort")
            startShizukuWithPort("127.0.0.1", knownPort)
            return START_STICKY
        }

        beginMdnsDiscovery()
        return START_STICKY
    }

    /**
     * Headless AdbClient only on boot — [StarterActivity] is BAL-blocked from FGS
     * (`BAL_BLOCK`). Activity path stays for in-app「启动」/ foreground retry.
     */
    private fun startShizukuWithPort(host: String, port: Int) {
        if (starting) return
        starting = true
        pollJob?.cancel()
        adbMdns?.stop()
        startShizukuHeadless(host, port)
    }

    private fun startShizukuHeadless(host: String, port: Int) {
        Log.i(AppConstants.TAG, "Headless AdbClient start on $host:$port")
        adbWirelessHelper.startShizukuViaAdb(
            host = host,
            port = port,
            coroutineScope = appScope,
            onOutput = { },
            onError = { e ->
                Log.e(AppConstants.TAG, "SelfStarterService ADB start failed on $port", e)
                if (disableWirelessDebuggingWhenFinished && !Shizuku.pingBinder()) {
                    adbWirelessHelper.disableWirelessAdb(contentResolver)
                }
                // Stale / early-boot port: forget it so the next poll/mDNS can rediscover.
                if (ShizukuSettings.getLastAdbWirelessPort() == port) {
                    ShizukuSettings.setLastAdbWirelessPort(0)
                }
                starting = false
                if (!Shizuku.pingBinder()) {
                    Log.i(AppConstants.TAG, "Falling back to mDNS after port $port failed")
                    beginMdnsDiscovery()
                } else {
                    BootStartNotifications.dismiss(this)
                    stopSelf()
                }
            },
            onSuccess = {
                if (!Shizuku.pingBinder()) {
                    Log.w(AppConstants.TAG, "onSuccess without binder — falling back to mDNS")
                    if (ShizukuSettings.getLastAdbWirelessPort() == port) {
                        ShizukuSettings.setLastAdbWirelessPort(0)
                    }
                    starting = false
                    beginMdnsDiscovery()
                    return@startShizukuViaAdb
                }
                ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
                ShizukuSettings.setLastAdbWirelessPort(port)
                maybeStartWatchdog()
                BootStartNotifications.dismiss(this)
                if (disableWirelessDebuggingWhenFinished) {
                    adbWirelessHelper.disableWirelessAdb(contentResolver)
                }
                Log.i(AppConstants.TAG, "Headless AdbClient start succeeded")
                stopSelf()
            }
        )
    }

    private fun beginMdnsDiscovery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            BootStartNotifications.showWaitingForNetwork(this)
            stopSelf()
            return
        }
        BootStartNotifications.showConnecting(this)
        Log.i(AppConstants.TAG, "Starting mDNS discovery + port poll for wireless ADB.")
        // Ensure wireless ADB is on before we burn the long poll window.
        if (Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) != 1) {
            runCatching {
                adbWirelessHelper.validateThenEnableWirelessAdb(contentResolver, this, true)
            }.onFailure {
                Log.w(AppConstants.TAG, "Failed to enable wireless ADB before mDNS poll", it)
            }
        }
        if (Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) != 1) {
            BootStartNotifications.showWaitingForNetwork(this)
            stopSelf()
            return
        }
        portLive.removeObserver(portObserver)
        portLive.observeForever(portObserver)
        if (adbMdns == null) {
            adbMdns = AdbMdns(
                context = this,
                serviceType = AdbMdns.TLS_CONNECT,
                observer = portObserver
            )
        }
        adbMdns?.start()
        pollJob?.cancel()
        pollJob = appScope.launch {
            var elapsed = 0L
            while (elapsed < PORT_WAIT_TIMEOUT_MS && !starting && !Shizuku.pingBinder()) {
                val port = adbWirelessHelper.getStartableAdbPort()
                if (port != null) {
                    Log.i(AppConstants.TAG, "Port poll found ADB TCP port: $port")
                    startShizukuWithPort("127.0.0.1", port)
                    return@launch
                }
                delay(PORT_POLL_INTERVAL_MS)
                elapsed += PORT_POLL_INTERVAL_MS
            }
            if (!starting && !Shizuku.pingBinder()) {
                Log.e(AppConstants.TAG, "Timed out waiting for wireless ADB port")
                BootStartNotifications.showWaitingForNetwork(this@SelfStarterService)
                stopSelf()
            }
        }
    }

    private fun maybeStartWatchdog() {
        if (!ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.WATCHDOG_ENABLED_ADB, false)) {
            return
        }
        WatchdogService.start(this)
    }

    private fun startServiceNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    AppConstants.NOTIFICATION_CHANNEL_STATUS,
                    getString(R.string.notification_channel_service_status),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = Notification.Builder(this, AppConstants.NOTIFICATION_CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setColor(getColor(R.color.notification))
            .setContentTitle(getString(R.string.notification_service_starting))
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AppConstants.NOTIFICATION_ID_STATUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AppConstants.NOTIFICATION_ID_STATUS, notification)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.i(AppConstants.TAG, "SelfStarterService destroying")
            adbMdns?.stop()
        }
        pollJob?.cancel()
        portLive.removeObserver(portObserver)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
