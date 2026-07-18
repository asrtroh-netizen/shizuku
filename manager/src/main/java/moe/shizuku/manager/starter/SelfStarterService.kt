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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * HSSkyBoy-aligned wireless start FGS: enable path already decided by WorkManager,
 * then mDNS + [AdbStarter] without boot-time Wi‑Fi busy-wait / settle delays.
 */
class SelfStarterService : Service(), LifecycleOwner {

    companion object {
        const val EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING =
            "moe.shizuku.manager.extra.AUTO_ENABLE_WIRELESS_DEBUGGING"
        const val EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED =
            "moe.shizuku.manager.extra.DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val portLive = MutableLiveData<Int>()
    private var adbMdns: AdbMdns? = null
    private var disableWirelessWhenFinished = false
    private var starting = false

    private val portObserver = Observer<Int> { p ->
        if (p in 1..65535) {
            Log.i(AppConstants.TAG, "SelfStarterService: mDNS port=$p")
            startViaAdb(p)
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        startServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (ShizukuStateMachine.isRunning()) {
            stopSelf()
            return START_NOT_STICKY
        }

        disableWirelessWhenFinished =
            intent?.getBooleanExtra(EXTRA_DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED, false) == true
        val autoEnable =
            intent?.getBooleanExtra(EXTRA_AUTO_ENABLE_WIRELESS_DEBUGGING, false) == true

        if (autoEnable) {
            runCatching {
                Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
                Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(contentResolver, "adb_allowed_connection_time", 0L)
            }
        }

        val wirelessEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        val tcpPort = EnvironmentUtils.getAdbTcpPort()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wirelessEnabled) {
            portLive.removeObserver(portObserver)
            portLive.observeForever(portObserver)
            if (adbMdns == null) {
                adbMdns = AdbMdns(this, AdbMdns.TLS_CONNECT) { pair ->
                    if (pair.second > 0) portLive.postValue(pair.second)
                }
            }
            adbMdns?.start()
            Log.i(AppConstants.TAG, "SelfStarterService: mDNS discovery started")
        } else if (tcpPort > 0) {
            startViaAdb(tcpPort)
        } else {
            Log.e(AppConstants.TAG, "SelfStarterService: no wireless ADB / TCP port")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startViaAdb(port: Int) {
        if (starting || ShizukuStateMachine.isRunning()) return
        starting = true
        adbMdns?.stop()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AdbStarter.startAdb(this@SelfStarterService, port)
                Starter.waitForBinder(timeoutMs = 20_000L)
                if (ShizukuSettings.getWatchdog()) {
                    WatchdogService.start(this@SelfStarterService)
                }
                Log.i(AppConstants.TAG, "SelfStarterService: binder ready")
            } catch (t: Throwable) {
                Log.w(AppConstants.TAG, "SelfStarterService: start failed", t)
                if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
                    ShizukuStateMachine.update()
                }
            } finally {
                if (disableWirelessWhenFinished) {
                    runCatching {
                        Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 0)
                    }
                }
                stopSelf()
            }
        }
    }

    private fun startServiceNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
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
            adbMdns?.stop()
        }
        portLive.removeObserver(portObserver)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
