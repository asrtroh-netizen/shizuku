package moe.shizuku.manager.flutter

import android.app.Activity
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.home.HomeActions
import org.json.JSONObject

/**
 * 首页的 MethodChannel + EventChannel（RULEBOOK §1.1：常驻页允许 EventChannel）。
 * 界面在 Dart，动作全部走 [HomeActions]；`when` 分支自 `FlutterHostActivity.configureFlutterEngine` 逐条搬入，
 * 方法名 / 参数名 / 错误码 / `recreate()` 时机（`toggleTheme`、`setLocale` 先回复再重建）不变。
 *
 * `getState` / `checkUpdate` 与写操作后的整页快照在 [scope]（IO）取、主线程回；其余动作在主线程 `runCatching`。
 * binder 到达 / 死亡由宿主监听并调 [emitChanged]，宿主恢复时不推（Dart 的 resumed 会自己拉，见 [shouldEmitHomeEvent]）。
 */
class HomeChannel(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val actions: HomeActions,
) {

    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> scope.launch {
                    try {
                        val json = actions.snapshot().toString()
                        withContext(Dispatchers.Main) { result.success(json) }
                    } catch (t: Throwable) {
                        withContext(Dispatchers.Main) {
                            result.error("getState", t.message, null)
                        }
                    }
                }
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
                            activity.recreate()
                        },
                        { result.error("toggleTheme", it.message, null) },
                    )
                }
                "setLocale" -> {
                    val tag = call.argument<String>("tag") ?: "SYSTEM"
                    runCatching { actions.setLocale(tag) }.fold(
                        {
                            result.success(ok())
                            activity.recreate()
                        },
                        { result.error("setLocale", it.message, null) },
                    )
                }
                "copyText" -> {
                    val text = call.argument<String>("text").orEmpty()
                    runAction(result, "copyText") { actions.copyText(text) }
                }
                "checkUpdate" -> scope.launch {
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

        EventChannel(messenger, EVENTS).setStreamHandler(
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

    /**
     * binder 到达 / 死亡时由宿主调用（宿主传入 `dartExecutor.isExecutingDart` 与 `lifecycle` 是否 RESUMED）。
     * 主线程投递；宿主已销毁或 [shouldEmitHomeEvent] 门禁不通过则不推；任何异常吞掉。
     */
    fun emitChanged(dartExecuting: Boolean, hostResumed: Boolean) {
        val emit = Runnable {
            try {
                if (activity.isDestroyed) return@Runnable
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
        scope.launch {
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
        const val CHANNEL = "shizuku/home"
        const val EVENTS = "shizuku/home/events"
    }
}
