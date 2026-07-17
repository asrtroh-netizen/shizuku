package moe.shizuku.manager.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

class NotifCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).cancelUniqueWork("adb_start_worker")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)
        // Only clear sticky STARTING when cancelling a background worker path — and only if
        // binder is down. Avoid clearing a live in-place TCP start (would flash the hero card).
        if (!Shizuku.pingBinder() &&
            ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING &&
            ShizukuStateMachine.isStartingStale(3_000L)
        ) {
            ShizukuStateMachine.update()
        }
    }
}
