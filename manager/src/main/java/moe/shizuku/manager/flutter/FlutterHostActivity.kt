package moe.shizuku.manager.flutter

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.home.HomeActions
import org.json.JSONObject
import rikka.shizuku.Shizuku

/**
 * Flutter 换皮宿主：界面在 Dart，动作全部走 [HomeActions]（与原 Compose 首页同一套 Kotlin）。
 */
class FlutterHostActivity : FlutterActivity() {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var actions: HomeActions

    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        emitHomeChanged()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        emitHomeChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actions = HomeActions(this)
        actions.handleStartViaWadbIntent(intent)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!::actions.isInitialized) actions = HomeActions(this)
        actions.handleStartViaWadbIntent(intent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        if (!::actions.isInitialized) actions = HomeActions(this)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, HOME_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> ioScope.launch {
                    try {
                        val json = actions.snapshot().toString()
                        withContext(Dispatchers.Main) { result.success(json) }
                    } catch (t: Throwable) {
                        withContext(Dispatchers.Main) {
                            result.error("getState", t.message, null)
                        }
                    }
                }
                "openApps" -> runAction(result, "openApps") { actions.openApps() }
                "openTerminal" -> runAction(result, "openTerminal") { actions.openTerminal() }
                "openSettings" -> runAction(result, "openSettings") { actions.openSettings() }
                "openPairing" -> runAction(result, "openPairing") { actions.openPairing() }
                "openWirelessGuide" -> runAction(result, "openWirelessGuide") { actions.openWirelessGuide() }
                "openAdbPermissionHelp" -> runAction(result, "openAdbPermissionHelp") { actions.openAdbPermissionHelp() }
                "startRoot" -> runAction(result, "startRoot") { actions.startRoot() }
                "startWireless" -> runAction(result, "startWireless") { actions.startWirelessAdb() }
                "copyAdbCommand" -> runAction(result, "copyAdbCommand") { actions.copyAdbCommand() }
                "sendAdbCommand" -> runAction(result, "sendAdbCommand") { actions.sendAdbCommand() }
                "setBootRoot" -> {
                    val checked = call.argument<Boolean>("checked") == true
                    runCatching { actions.setBootRoot(checked) }.fold(
                        { replySnapshot(result) },
                        { result.error("setBootRoot", it.message, null) },
                    )
                }
                "setBootWireless" -> {
                    val checked = call.argument<Boolean>("checked") == true
                    runCatching {
                        val extra = actions.setBootWireless(checked)
                        extra.put("state", actions.snapshot())
                        extra.toString()
                    }.fold(
                        { result.success(it) },
                        { result.error("setBootWireless", it.message, null) },
                    )
                }
                "setWatchdog" -> {
                    val checked = call.argument<Boolean>("checked") == true
                    runCatching { actions.setWatchdog(checked) }.fold(
                        { replySnapshot(result) },
                        { result.error("setWatchdog", it.message, null) },
                    )
                }
                "toggleTheme" -> {
                    runCatching { actions.toggleTheme() }.fold(
                        {
                            result.success(ok())
                            recreate()
                        },
                        { result.error("toggleTheme", it.message, null) },
                    )
                }
                "setLocale" -> {
                    val tag = call.argument<String>("tag") ?: "SYSTEM"
                    runCatching { actions.setLocale(tag) }.fold(
                        {
                            result.success(ok())
                            recreate()
                        },
                        { result.error("setLocale", it.message, null) },
                    )
                }
                "copyText" -> {
                    val text = call.argument<String>("text").orEmpty()
                    runAction(result, "copyText") { actions.copyText(text) }
                }
                "checkUpdate" -> ioScope.launch {
                    try {
                        val json = actions.checkUpdateBlocking()
                        withContext(Dispatchers.Main) { result.success(json.toString()) }
                    } catch (t: Throwable) {
                        withContext(Dispatchers.Main) {
                            result.error("checkUpdate", t.message, null)
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(messenger, HOME_EVENTS).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            },
        )
    }

    override fun onDestroy() {
        ioScope.cancel()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        eventSink = null
        super.onDestroy()
    }

    private fun emitHomeChanged() {
        val emit = Runnable {
            try {
                if (isDestroyed) return@Runnable
                val dartExecuting = flutterEngine?.dartExecutor?.isExecutingDart == true
                val hostResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!shouldEmitHomeEvent(dartExecuting, hostResumed, triggeredByHostResume = false)) return@Runnable
                eventSink?.success("changed")
            } catch (_: Throwable) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            emit.run()
        } else {
            mainHandler.post(emit)
        }
    }

    private fun runAction(result: MethodChannel.Result, name: String, block: () -> Unit) {
        runCatching(block).fold(
            { result.success(ok()) },
            { result.error(name, it.message, null) },
        )
    }

    private fun replySnapshot(result: MethodChannel.Result) {
        ioScope.launch {
            try {
                val json = actions.snapshot().toString()
                withContext(Dispatchers.Main) { result.success(json) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    result.error("snapshot", t.message, null)
                }
            }
        }
    }

    private fun ok(): String = JSONObject().put("ok", true).toString()

    companion object {
        const val HOME_CHANNEL = "shizuku/home"
        const val HOME_EVENTS = "shizuku/home/events"
    }
}
