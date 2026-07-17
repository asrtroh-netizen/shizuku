package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku

class ShizukuApplication : Application() {

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }

        lateinit var application: ShizukuApplication
            private set

        lateinit var appContext: Context
            private set

    }

    private fun init(context: Context) {
        ShizukuSettings.initialize(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        AppCompatDelegate.setDefaultNightMode(ShizukuSettings.getNightMode())

        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
        // Paired once → keep Wi‑Fi auto-connect armed; nudge if already on Wi‑Fi.
        if (EnvironmentUtils.canWirelessAutostart(context) || ShizukuSettings.getStartOnBoot(context)) {
            EnvironmentUtils.enableAutostartAfterPair(context)
            moe.shizuku.manager.receiver.WifiReadyMonitor.ensureRegistered(context)
            if (!ShizukuStateMachine.isRunning() &&
                (!EnvironmentUtils.isWifiRequired() || EnvironmentUtils.isWifiClientConnected(context))
            ) {
                // Open-app nudge: same FGS path as boot (not WorkManager-only).
                moe.shizuku.manager.service.BootAdbStartService.enqueue(context, debounceMs = 0L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        appContext = applicationContext
        init(this)
    }

}
