package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeStartWirelessAdbBinding
import moe.shizuku.manager.home.showAccessibilityDialog
import moe.shizuku.manager.receiver.NotifCancelReceiver
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.core.content.asActivity
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import rikka.shizuku.Shizuku

class StartWirelessAdbViewHolder(binding: HomeStartWirelessAdbBinding, root: View, private val scope: CoroutineScope) :
    BaseViewHolder<Any?>(root) {

    companion object {
        fun creator (scope: CoroutineScope): Creator<Any> {
            return Creator { inflater: LayoutInflater, parent: ViewGroup? ->
                val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
                val inner = HomeStartWirelessAdbBinding.inflate(inflater, outer.root, true)
                StartWirelessAdbViewHolder(inner, outer.root, scope)
            }
        }

        /**
         * Start Shizuku over an already-open ADB TCP port without leaving the home screen.
         * (Previously jumped to StarterActivity.)
         */
        fun startAdbInPlace(context: Context, scope: CoroutineScope, port: Int) {
            val app = context.applicationContext
            scope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, R.string.home_hero_action_activating, Toast.LENGTH_SHORT).show()
                    }
                    AdbStarter.startAdb(app, port)
                    Starter.waitForBinder()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            app,
                            app.getString(R.string.home_status_service_is_running, app.getString(R.string.app_name)),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ShizukuStateMachine.update()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            app,
                            e.message?.takeIf { it.isNotBlank() }
                                ?: app.getString(R.string.home_status_service_not_running, app.getString(R.string.app_name)),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }

        fun start (context: Context, scope: CoroutineScope) {
            if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
                // Real in-flight start: binder may still be coming. Stale STARTING (orphan
                // after cancel/REPLACE) must be cleared so the user can retry.
                if (Shizuku.pingBinder()) {
                    Toast.makeText(context, context.getString(R.string.toast_shizuku_already_starting), Toast.LENGTH_SHORT).show()
                    return
                }
                ShizukuStateMachine.update()
                if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
                    Toast.makeText(context, context.getString(R.string.toast_shizuku_already_starting), Toast.LENGTH_SHORT).show()
                    return
                }
            }

            context.sendBroadcast(Intent(context, NotifCancelReceiver::class.java))

            val cr = context.contentResolver
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            }
        
            val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
            if (adbEnabled == 0) {
                WadbEnableUsbDebuggingDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
                return
            }

            val tcpPort = EnvironmentUtils.getAdbTcpPort()
            val tcpMode = ShizukuSettings.getTcpMode()

            // If ADB is NOT listening to a TCP port and the device doesn't support TLS, inform the user
            if (tcpPort <= 0 && !EnvironmentUtils.isTlsSupported()) {
                WadbNotEnabledDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
            // If ADB IS NOT listening to a TCP port but the device supports TLS, start mDns discovery
            } else if (tcpPort <= 0) {
                AdbDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
            // If ADB IS listening to a TCP port but the user wants to close it and use TLS instead, close the TCP port and start mDns discovery
            } else if (!tcpMode) {
                scope.launch {
                    AdbStarter.stopTcp(context, tcpPort)
                }
                AdbDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
            // TCP already listening — start in place; do not jump to StarterActivity.
            } else {
                startAdbInPlace(context, scope, tcpPort)
            }
        }
    }

    init {
        binding.button1.setOnClickListener { v: View ->
            start(v.context, scope)
        }

        if (EnvironmentUtils.isTlsSupported()) {
            binding.button3.setOnClickListener { v: View ->
                CustomTabsHelper.launchUrlOrCopy(v.context, Helps.ADB_ANDROID11.get())
            }
            binding.button2.setOnClickListener { v: View ->
                onPairClicked(v.context)
            }
        } else {
            binding.button2.isVisible = false
            binding.button3.isVisible = false
        }
        // Long description removed per home layout wireframe.
        binding.text1.isVisible = false
    }

    override fun onBind(payloads: MutableList<Any>) {
        super.onBind(payloads)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun onPairClicked(context: Context) {
        if (EnvironmentUtils.isTelevision()) {
            context.showAccessibilityDialog()
        } else if ((context.display?.displayId ?: -1) > 0 || ShizukuSettings.getLegacyPairing()) {
            // Running in a multi-display environment (e.g., Windows Subsystem for Android),
            // pairing dialog can be displayed simultaneously with Shizuku.
            // Input from notification is harder to use under this situation.
            AdbPairDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
        } else {
            context.startActivity(Intent(context, AdbPairingTutorialActivity::class.java))
        }
    }
}
