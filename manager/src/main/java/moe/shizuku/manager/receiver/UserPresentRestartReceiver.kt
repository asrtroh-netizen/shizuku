package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ktx.setComponentEnabled

class UserPresentRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT != intent.action) {
            return
        }

        setEnabled(context, false)
        val app = context.applicationContext
        Log.i(AppConstants.TAG, "USER_PRESENT: force wireless start (immediate + delayed)")
        // Immediate + delayed: Wi‑Fi / TLS port often appear a few seconds after unlock.
        ShizukuReceiverStarter.startWireless(app, force = true)
        Handler(Looper.getMainLooper()).postDelayed({
            ShizukuReceiverStarter.startWireless(app, force = true)
        }, 5_000L)
        Handler(Looper.getMainLooper()).postDelayed({
            ShizukuReceiverStarter.startWireless(app, force = true)
        }, 15_000L)
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, UserPresentRestartReceiver::class.java)
            context.packageManager.setComponentEnabled(component, enabled)
        }
    }
}
