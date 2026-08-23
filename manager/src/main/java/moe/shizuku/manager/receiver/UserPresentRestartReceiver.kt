package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ktx.setComponentEnabled

internal fun dispatchUserPresentRestart(
    rootBoot: Boolean,
    wirelessBoot: Boolean,
    setRecoveryEnabled: (Boolean) -> Unit,
    startRoot: () -> Unit,
    startWireless: () -> Unit,
): Throwable? {
    if (rootBoot) {
        // The Root worker owns this recovery entry point. Keep it armed until the
        // worker observes a live Binder; enqueue failures must remain recoverable.
        setRecoveryEnabled(true)
        return runCatching(startRoot).exceptionOrNull()?.also {
            setRecoveryEnabled(true)
        }
    }

    setRecoveryEnabled(false)
    if (wirelessBoot) {
        startWireless()
    }
    return null
}

class UserPresentRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT != intent.action) {
            return
        }

        val app = context.applicationContext
        if (ShizukuSettings.getPreferences() == null) {
            ShizukuSettings.initialize(app)
        }
        val preferences = ShizukuSettings.getPreferences()
        val rootBoot =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val wirelessBoot =
            preferences.getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        if (!rootBoot && !wirelessBoot) {
            Log.i(AppConstants.TAG, "USER_PRESENT: autostart no longer enabled")
        }

        val rootStartFailure = dispatchUserPresentRestart(
            rootBoot = rootBoot,
            wirelessBoot = wirelessBoot,
            setRecoveryEnabled = { enabled -> setEnabled(app, enabled) },
            startRoot = {
                Log.i(AppConstants.TAG, "USER_PRESENT: retry selected root boot mode")
                ShizukuReceiverStarter.startOnBoot(app)
            },
            startWireless = {
                Log.i(AppConstants.TAG, "USER_PRESENT: force wireless start (immediate + delayed)")
                // Immediate + delayed: Wi‑Fi / TLS port often appear a few seconds after unlock.
                ShizukuReceiverStarter.startWireless(app, force = true)
                Handler(Looper.getMainLooper()).postDelayed({
                    ShizukuReceiverStarter.startWireless(app, force = true)
                }, 5_000L)
                Handler(Looper.getMainLooper()).postDelayed({
                    ShizukuReceiverStarter.startWireless(app, force = true)
                }, 15_000L)
            },
        )
        if (rootStartFailure != null) {
            Log.w(AppConstants.TAG, "USER_PRESENT: failed to enqueue root boot", rootStartFailure)
        }
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, UserPresentRestartReceiver::class.java)
            context.packageManager.setComponentEnabled(component, enabled)
        }
    }
}
