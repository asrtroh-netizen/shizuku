package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.worker.AdbStartWorker

/**
 * HSSkyBoy-aligned: resume wireless boot start after unlock.
 */
class UserPresentRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT != intent.action) return
        setEnabled(context, false)
        AdbStartWorker.enqueue(context, replaceStuck = true)
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, UserPresentRestartReceiver::class.java)
            context.packageManager.setComponentEnabled(component, enabled)
        }
    }
}
