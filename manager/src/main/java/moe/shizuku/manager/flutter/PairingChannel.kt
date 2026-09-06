package moe.shizuku.manager.flutter

import android.app.Activity
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingService
import org.json.JSONObject
import rikka.compatibility.DeviceCompatibility

// ───────────────────────── 纯函数（零 Android / org.json 依赖，供 JUnit 直测）─────────────────────────

/**
 * 与 `AdbPairingTutorialActivity` 的 `PairingTutorialState` 同形的状态四元组，默认值逐项相同
 * （本地网络权限默认视为已授，其余默认 false）。
 */
internal data class PairingFlags(
    val notificationEnabled: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val localNetworkPermissionGranted: Boolean = true,
    val pairingServiceStartFailed: Boolean = false,
)

/**
 * 复刻 `AdbPairingTutorialActivity.onResume` 的布尔条件：同步后通知已开且本地网络权限已授，
 * 且「同步前通知未开 / 同步前权限未授 / 上次服务启动失败」至少一项成立时补启动配对服务。
 *
 * `syncState()` 不改 `pairingServiceStartFailed`，所以 [new] 里的这一位就是沿用下来的旧值——
 * 与 Activity 在 `syncState()` 之后读 `state.pairingServiceStartFailed` 完全一致。
 */
internal fun shouldRestartPairingOnResume(old: PairingFlags, new: PairingFlags): Boolean {
    return new.notificationEnabled &&
        new.localNetworkPermissionGranted &&
        (!old.notificationEnabled || !old.localNetworkPermissionGranted || new.pairingServiceStartFailed)
}

/**
 * 整页快照（RULEBOOK §12）。`autoPairingEnabled` 是 §12 之外的加性字段：Compose
 * `AdbPairingTutorialComposeScreen` 只在「自动配对已开 且 通知监听未开」时才显示通知监听引导卡，
 * 这个开关 Dart 拿不到，只能由这里下发（见报告）。
 */
internal fun buildPairingStateMap(
    supported: Boolean,
    flags: PairingFlags,
    autoPairingEnabled: Boolean,
    showMiuiHint: Boolean,
    copy: Map<String, Any?>,
): Map<String, Any?> {
    return linkedMapOf(
        "ok" to true,
        "supported" to supported,
        "notificationEnabled" to flags.notificationEnabled,
        "notificationListenerEnabled" to flags.notificationListenerEnabled,
        "localNetworkPermissionGranted" to flags.localNetworkPermissionGranted,
        "pairingServiceStartFailed" to flags.pairingServiceStartFailed,
        "autoPairingEnabled" to autoPairingEnabled,
        "showMiuiHint" to showMiuiHint,
        "copy" to copy,
    )
}

// ───────────────────────────────────────── 通道类 ─────────────────────────────────────────

/**
 * 「无线配对」引导页的 MethodChannel + EventChannel（RULEBOOK §12 / GAP-6）。界面在 Dart（推入页），
 * 业务逻辑逐行复刻原 Compose `AdbPairingTutorialActivity`（已于 P4 删除）：
 * `syncState` / `startPairingIfReady` / `ensureLocalNetworkPermissionOrStartPairing` / `startPairingService`
 * 的失败回退 / `onResume` 条件补启动 / `onRequestPermissionsResult`。
 *
 * 方法：
 * - `getState` → 先 `syncState()` 再回整页快照。
 * - `start` → 页面进入，等价于 Activity `onCreate` 的 `syncState(); startPairingIfReady()`（先从一份全新的
 *   `PairingTutorialState()` 开始，与每次新开 Activity 一致）；回整页快照。
 * - `requestLocalNetworkPermission` → 同 Compose `onRequestLocalNetworkPermission`（清防重入位后
 *   `ensureLocalNetworkPermissionOrStartPairing()`），也是"服务启动失败"卡的重试；回整页快照。
 * - `openDeveloperOptions` / `openNotificationOptions` / `openNotificationAccessSettings` → 三个系统跳转，回 `{ok:true}`。
 *
 * 生命周期钩子（由协调者在 [FlutterHostActivity] 接线）：[onHostResumed]、[onRequestPermissionsResult]。
 * 状态变化后向 `shizuku/pairing/events` 推 `"changed"`（主线程投递），Dart 收到后自己拉 `getState`。
 *
 * 线程：Activity 版全部逻辑都在主线程（`onCreate` / `onResume` / 回调），这里同样把 [state] 限定在主线程
 * （MethodChannel 回调与宿主生命周期钩子都在主线程），保证与 `onHostResumed` 的先后顺序不会交错；
 * 涉及的系统调用（通知开关、通知监听设置、权限检查）都是 Activity 版在主线程做的轻量调用。
 * [scope] 只为保持 §1.6 的统一通道形状，本通道没有需要下放到 IO 的工作。
 *
 * SDK 门：Activity 版整类 `@RequiresApi(R)`，首页 `showPair` 也只在 R+ 露出入口；这里的通道在所有 SDK 上都注册
 * （快照 `supported = SDK >= R`），R 以下所有状态机动作都是空操作，只回 `supported:false` 的快照。
 *
 * copy key ↔ R.string（`adb_pairing_tutorial_content_*` 去前缀）：
 * - `title`                           = `adb_pairing_tutorial_title`
 * - `back`                            = `action_back`
 * - `permissionMissing`               = `permission_missing`
 * - `notification`                    = `adb_pairing_tutorial_content_notification`
 * - `notificationBlocked`             = `adb_pairing_tutorial_content_notification_blocked`
 * - `notificationSettings`            = `notification_settings`
 * - `autoPairingNotificationAccessTooltip` = `auto_pairing_notification_access_tooltip`
 * - `network`                         = `adb_pairing_tutorial_content_network`
 * - `networkLimitationNotForeground`  = `adb_pairing_tutorial_content_network_limation_not_foreground`（原 key 拼写如此）
 * - `networkBlocked`                  = `adb_pairing_tutorial_content_network_blocked`
 * - `retry`                           = `action_retry`
 * - `serviceStartFailed`              = `notification_service_start_failed`
 * - `pairingServiceFailed`            = `adb_pairing_tutorial_content_pairing_service_failed`
 * - `miui`                            = `adb_pairing_tutorial_content_miui`
 * - `miui2`                           = `adb_pairing_tutorial_content_miui_2`
 * - `steps`                           = `adb_pairing_tutorial_content_steps`
 * - `leftIsClickable`                 = `adb_pairing_tutorial_content_left_is_clickable`
 * - `developmentSettings`             = `development_settings`
 * - `enterPairingCode`                = `adb_pairing_tutorial_content_enter_pairing_code`
 * - `finish`                          = `adb_pairing_tutorial_content_finish`
 * - `ok`                              = `android.R.string.ok`
 */
class PairingChannel(private val activity: Activity, private val scope: CoroutineScope) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var eventSink: EventChannel.EventSink? = null

    /** 同 Activity 的 `state`；只在主线程读写。 */
    private var state = PairingFlags()
    private var localNetworkPermissionRequested = false

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> runAction(result, "getState") {
                    if (supported()) syncState()
                    snapshotJson()
                }
                "start" -> runAction(result, "start") {
                    if (supported()) enterPage()
                    snapshotJson()
                }
                "requestLocalNetworkPermission" -> runAction(result, "requestLocalNetworkPermission") {
                    if (supported()) {
                        localNetworkPermissionRequested = false
                        ensureLocalNetworkPermissionOrStartPairing()
                    }
                    snapshotJson()
                }
                "openDeveloperOptions" -> runAction(result, "openDeveloperOptions") {
                    openDeveloperOptions()
                    ok()
                }
                "openNotificationOptions" -> runAction(result, "openNotificationOptions") {
                    if (supported()) openNotificationOptions()
                    ok()
                }
                "openNotificationAccessSettings" -> runAction(result, "openNotificationAccessSettings") {
                    NotificationListenerAccess.openSettings(activity)
                    ok()
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

    // ───── 生命周期钩子（宿主转发）─────

    /**
     * 宿主 `onResume` 转发到此，复刻 `AdbPairingTutorialActivity.onResume`：重同步，满足
     * [shouldRestartPairingOnResume] 时补启动配对服务，然后推 `"changed"`。
     *
     * Activity 版只在配对页存在时才有 `onResume`；本通道随宿主活一辈子，所以以「Dart 配对页正在监听事件流」
     * （[eventSink] 非空）作为"页面在场"的判据——否则宿主每次回到前台都会把配对服务拉起来。
     */
    fun onHostResumed() {
        if (!supported() || eventSink == null) return
        val oldState = state
        syncState()
        if (shouldRestartPairingOnResume(oldState, state)) {
            startPairingService()
        }
        emitChanged()
    }

    /**
     * 宿主 `onRequestPermissionsResult` 先 `super` 再转发到此；只处理 [REQUEST_LOCAL_NETWORK_PERMISSION]。
     * 逐行同 Activity：清防重入位 → 同步 → 已授权则启动配对服务。
     */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_LOCAL_NETWORK_PERMISSION) {
            return
        }
        if (!supported()) return
        localNetworkPermissionRequested = false
        syncState()
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startPairingService()
        }
        emitChanged()
    }

    // ───── 状态机（逐行同 AdbPairingTutorialActivity 私有函数）─────

    /** 页面进入 = Activity `onCreate`：全新 `PairingTutorialState()`，然后 `syncState(); startPairingIfReady()`。 */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun enterPage() {
        state = PairingFlags()
        localNetworkPermissionRequested = false
        syncState()
        startPairingIfReady()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isNotificationEnabled(): Boolean {
        val context = activity

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun hasLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < ANDROID_17_API) {
            return true
        }
        return activity.checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ensureLocalNetworkPermissionOrStartPairing() {
        if (hasLocalNetworkPermission()) {
            syncState()
            startPairingService()
            return
        }
        if (localNetworkPermissionRequested) {
            syncState()
            return
        }
        localNetworkPermissionRequested = true
        activity.requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK), REQUEST_LOCAL_NETWORK_PERMISSION)
        syncState()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startPairingIfReady() {
        if (state.notificationEnabled) {
            ensureLocalNetworkPermissionOrStartPairing()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(activity)
        try {
            activity.startForegroundService(intent)
            state = state.copy(pairingServiceStartFailed = false)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = activity.getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), activity.packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(activity, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                activity.startService(intent)
                state = state.copy(pairingServiceStartFailed = false)
            } else {
                state = state.copy(pairingServiceStartFailed = true)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun syncState() {
        state = state.copy(
            notificationEnabled = isNotificationEnabled(),
            notificationListenerEnabled = NotificationListenerAccess.isEnabled(activity),
            localNetworkPermissionGranted = hasLocalNetworkPermission(),
        )
    }

    // ───── 系统跳转（逐行同 AdbPairingTutorialActivity.onCreate 里的回调；通知监听设置见 NotificationListenerAccess）─────

    private fun openDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun openNotificationOptions() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    // ───── 事件 / 回复 / 快照 ─────

    /**
     * 主线程投递（同宿主 `emitHomeChanged` 的做法）。这里一律 `post` 而不就地执行：
     * `onHostResumed` 发生在宿主 `onResume` 栈内，延后一拍让 `FlutterActivity` 先完成自己的恢复。
     */
    private fun emitChanged() {
        mainHandler.post {
            try {
                if (activity.isDestroyed) return@post
                eventSink?.success("changed")
            } catch (_: Throwable) {
            }
        }
    }

    private fun runAction(result: MethodChannel.Result, name: String, block: () -> String) {
        runCatching(block).fold(
            { result.success(it) },
            { result.error(name, it.message, null) },
        )
    }

    private fun snapshotJson(): String {
        // 同 Compose：autoPairingEnabled 直接读偏好。
        val autoPairingEnabled =
            ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.AUTO_PAIRING_ENABLED, false)
        return JSONObject(
            buildPairingStateMap(
                supported = supported(),
                flags = state,
                autoPairingEnabled = autoPairingEnabled,
                showMiuiHint = DeviceCompatibility.isMiui(),
                copy = copyMap(),
            ),
        ).toString()
    }

    private fun ok(): String = JSONObject().put("ok", true).toString()

    private fun copyMap(): Map<String, Any?> {
        val c = activity
        return linkedMapOf(
            "title" to c.getString(R.string.adb_pairing_tutorial_title),
            "back" to c.getString(R.string.action_back),
            "permissionMissing" to c.getString(R.string.permission_missing),
            "notification" to c.getString(R.string.adb_pairing_tutorial_content_notification),
            "notificationBlocked" to c.getString(R.string.adb_pairing_tutorial_content_notification_blocked),
            "notificationSettings" to c.getString(R.string.notification_settings),
            "autoPairingNotificationAccessTooltip" to c.getString(R.string.auto_pairing_notification_access_tooltip),
            "network" to c.getString(R.string.adb_pairing_tutorial_content_network),
            "networkLimitationNotForeground" to c.getString(
                R.string.adb_pairing_tutorial_content_network_limation_not_foreground,
            ),
            "networkBlocked" to c.getString(R.string.adb_pairing_tutorial_content_network_blocked),
            "retry" to c.getString(R.string.action_retry),
            "serviceStartFailed" to c.getString(R.string.notification_service_start_failed),
            "pairingServiceFailed" to c.getString(R.string.adb_pairing_tutorial_content_pairing_service_failed),
            "miui" to c.getString(R.string.adb_pairing_tutorial_content_miui),
            "miui2" to c.getString(R.string.adb_pairing_tutorial_content_miui_2),
            "steps" to c.getString(R.string.adb_pairing_tutorial_content_steps),
            "leftIsClickable" to c.getString(R.string.adb_pairing_tutorial_content_left_is_clickable),
            "developmentSettings" to c.getString(R.string.development_settings),
            "enterPairingCode" to c.getString(R.string.adb_pairing_tutorial_content_enter_pairing_code),
            "finish" to c.getString(R.string.adb_pairing_tutorial_content_finish),
            "ok" to c.getString(android.R.string.ok),
        )
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    companion object {
        const val CHANNEL = "shizuku/pairing"
        const val EVENTS = "shizuku/pairing/events"

        /** 本地网络权限请求码，同 Activity 版；宿主内唯一（RULEBOOK §1.6：终端 0x5254、配对 1001）。 */
        const val REQUEST_LOCAL_NETWORK_PERMISSION = 1001

        private const val ANDROID_17_API = 37
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
