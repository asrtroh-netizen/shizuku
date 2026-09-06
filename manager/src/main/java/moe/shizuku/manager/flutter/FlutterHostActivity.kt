package moe.shizuku.manager.flutter

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import moe.shizuku.manager.home.HomeActions
import rikka.shizuku.Shizuku

/**
 * Flutter 换皮宿主：界面在 Dart，五个页面各一个 `*Channel`；首页动作全部走 [HomeActions]。
 * 本类只管生命周期、通道注册与系统回调转发。
 */
class FlutterHostActivity : FlutterActivity() {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var actions: HomeActions

    // 保存为字段：选择器 / 权限请求期间进程被杀，重建后结果回调紧随 onCreate 派发。
    private lateinit var homeChannel: HomeChannel
    private lateinit var terminalChannel: TerminalChannel
    private lateinit var pairingChannel: PairingChannel

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        emitHomeChanged()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        emitHomeChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::actions.isInitialized) actions = HomeActions(this)
        actions.handleStartViaWadb(intent?.getBooleanExtra(EXTRA_START_SERVICE_VIA_WADB, false) == true)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!::actions.isInitialized) actions = HomeActions(this)
        actions.handleStartViaWadb(intent.getBooleanExtra(EXTRA_START_SERVICE_VIA_WADB, false))
    }

    /**
     * 设置页改语言/主题后宿主 recreate，通道会先把目标 Tab 写进 intent；
     * 这里转成 Dart 初始路由 `/tab/<n>`，读完即删，免得后续配置变更重建也回到设置页。
     * 底栏只有首页(0) / 设置(1)。系统"应用设置"入口直达设置。
     * `/tab/3` 是两 Tab 之前的设置下标，仍映射到设置。
     */
    override fun getInitialRoute(): String? {
        if (intent?.action == Intent.ACTION_APPLICATION_PREFERENCES) return "/tab/1"
        val tab = intent?.getIntExtra(EXTRA_TAB, -1) ?: -1
        if (tab < 0) return super.getInitialRoute()
        intent.removeExtra(EXTRA_TAB)
        val mapped = if (tab == 1 || tab == 3) 1 else 0
        return "/tab/$mapped"
    }

    override fun onResume() {
        super.onResume()
        if (::pairingChannel.isInitialized) pairingChannel.onHostResumed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::terminalChannel.isInitialized) {
            terminalChannel.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::pairingChannel.isInitialized) {
            pairingChannel.onRequestPermissionsResult(requestCode, grantResults)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        if (!::actions.isInitialized) actions = HomeActions(this)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        homeChannel = HomeChannel(this, ioScope, actions)
        homeChannel.register(messenger)
        AppsChannel(this, ioScope).register(messenger)
        SettingsChannel(this, ioScope).register(messenger)
        terminalChannel = TerminalChannel(this, ioScope)
        terminalChannel.register(messenger)
        pairingChannel = PairingChannel(this, ioScope)
        pairingChannel.register(messenger)
    }

    override fun onDestroy() {
        ioScope.cancel()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun emitHomeChanged() {
        if (!::homeChannel.isInitialized) return
        homeChannel.emitChanged(
            dartExecuting = flutterEngine?.dartExecutor?.isExecutingDart == true,
            hostResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )
    }

    companion object {
        /** 与 SettingsChannel 里的字面量一致（RULEBOOK §10）。 */
        const val EXTRA_TAB = "moe.shizuku.manager.extra.TAB"
        /** 配对成功通知的"启动"动作带此 extra；字符串值沿用原 Compose `HomeActivity`，旧通知的 PendingIntent 仍有效。 */
        const val EXTRA_START_SERVICE_VIA_WADB = "moe.shizuku.manager.extra.START_SERVICE_VIA_WADB"
    }
}
