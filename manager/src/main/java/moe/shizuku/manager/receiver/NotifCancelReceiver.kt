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
        // Cancelling the worker must not leave STARTING sticky (Hero stuck on 激活中).
        if (!Shizuku.pingBinder() && ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
            ShizukuStateMachine.update()
        }
    }
}
