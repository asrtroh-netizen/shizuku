package moe.shizuku.manager.flutter

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import rikka.shizuku.Shizuku

/**
 * P0 Flutter 管家壳：首页 Hero（含点阵笑脸）走 Dart，服务是否在跑仍问原生 binder。
 */
class FlutterHostActivity : FlutterActivity() {

    private var eventSink: EventChannel.EventSink? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        eventSink?.success("changed")
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        eventSink?.success("changed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, HOME_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> result.success(stateJson())
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
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        eventSink = null
        super.onDestroy()
    }

    private fun stateJson(): String {
        val running = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        return JSONObject()
            .put("running", running)
            .put("state", if (running) "ready" else "inactive")
            .toString()
    }

    companion object {
        const val HOME_CHANNEL = "shizuku/home"
        const val HOME_EVENTS = "shizuku/home/events"
    }
}
