package moe.shizuku.manager.flutter

import android.app.Activity
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.util.Base64
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.management.GrantedCountCache
import moe.shizuku.manager.management.packageGrantKey
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/** 一行授权应用：label 已按 Compose 规则拼好（他用户加 ` - <userName> (<userId>)`）。 */
internal data class AppRow(
    val packageName: String,
    val uid: Int,
    val userId: Int,
    val label: String,
    val requiresRoot: Boolean,
    val granted: Boolean,
)

internal const val TOGGLE_RESULT_SUCCESS = "success"
internal const val TOGGLE_RESULT_ADB_LIMITED = "adbLimited"

/**
 * 整页快照（纯函数，零 Android 依赖，供 JUnit 直测）。
 * 服务未运行时 `apps` 强制为空列表（GAP-3 的服务端保证），即使调用方传了非空列表。
 */
internal fun buildAppsStateJson(
    running: Boolean,
    adbLimited: Boolean,
    apps: List<AppRow>,
    copy: Map<String, Any?>,
): Map<String, Any?> {
    val rows: List<Map<String, Any?>> = if (running) {
        apps.map { row ->
            linkedMapOf<String, Any?>(
                "packageName" to row.packageName,
                "uid" to row.uid,
                "userId" to row.userId,
                "label" to row.label,
                "requiresRoot" to row.requiresRoot,
                "granted" to row.granted,
            )
        }
    } else {
        emptyList()
    }
    return linkedMapOf(
        "ok" to true,
        "running" to running,
        "adbLimited" to adbLimited,
        "apps" to rows,
        "copy" to copy,
    )
}

/**
 * 切换授权的结果判定（纯函数），与 `AppsManagementActivity.onTogglePackage` 的 catch 分支逐行同义：
 * 没有 SecurityException → success；有且 `Shizuku.getUid()` 抛异常（`shizukuUid == null`）→ success；
 * 有且 uid != 0 → adbLimited；有且 uid == 0 → success。
 */
internal fun toggleOutcome(securityException: Boolean, shizukuUid: Int?): String {
    if (!securityException) return TOGGLE_RESULT_SUCCESS
    if (shizukuUid == null) return TOGGLE_RESULT_SUCCESS
    return if (shizukuUid != 0) TOGGLE_RESULT_ADB_LIMITED else TOGGLE_RESULT_SUCCESS
}

/**
 * 「授权应用」Tab 的 MethodChannel（RULEBOOK §9）。界面在 Dart，业务逻辑与
 * 原 Compose `AppsManagementActivity`（已于 P4 删除）同一套；所有 binder / 包管理 / 图标
 * 工作在 [scope]（IO）执行，主线程回复。
 */
class AppsChannel(private val activity: Activity, private val scope: CoroutineScope) {

    /** 最近一次 getState 得到的列表，按 `packageName#uid` 索引，供 getIcon 找 ApplicationInfo。 */
    @Volatile
    private var cachedPackages: Map<String, PackageInfo> = emptyMap()

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> runIo(result, "getState") { snapshotJson() }
                "toggle" -> {
                    val packageName = call.argument<String>("packageName").orEmpty()
                    val uid = call.argument<Number>("uid")?.toInt() ?: -1
                    runIo(result, "toggle") { toggle(packageName, uid) }
                }
                "getIcon" -> {
                    val packageName = call.argument<String>("packageName").orEmpty()
                    val uid = call.argument<Number>("uid")?.toInt() ?: -1
                    val sizePx = call.argument<Number>("sizePx")?.toInt() ?: 0
                    runIo(result, "getIcon") { iconJson(packageName, uid, sizePx) }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun runIo(result: MethodChannel.Result, name: String, block: () -> String) {
        scope.launch {
            try {
                val json = block()
                withContext(Dispatchers.Main) { result.success(json) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { result.error(name, t.message, null) }
            }
        }
    }

    private fun snapshotJson(extra: Map<String, Any?> = emptyMap()): String {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val adbLimited = running && runCatching { Shizuku.getUid() != 0 }.getOrDefault(false)
        val rows = ArrayList<AppRow>()
        val cache = LinkedHashMap<String, PackageInfo>()
        if (running) {
            val pm = activity.packageManager
            val myUserId = UserHandleCompat.myUserId()
            for (packageInfo in AuthorizationManager.getPackages()) {
                val applicationInfo = packageInfo.applicationInfo ?: continue
                val uid = applicationInfo.uid
                val userId = UserHandleCompat.getUserId(uid)
                val baseLabel = applicationInfo.loadLabel(pm).toString()
                val label = if (userId != myUserId) {
                    val userInfo = ShizukuSystemApis.getUserInfo(userId)
                    "$baseLabel - ${userInfo.name} ($userId)"
                } else {
                    baseLabel
                }
                val requiresRoot =
                    applicationInfo.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
                val granted = AuthorizationManager.granted(packageInfo.packageName, uid)
                rows.add(AppRow(packageInfo.packageName, uid, userId, label, requiresRoot, granted))
                cache[packageGrantKey(packageInfo.packageName, uid)] = packageInfo
            }
        }
        cachedPackages = cache
        val map = LinkedHashMap(buildAppsStateJson(running, adbLimited, rows, copyMap()))
        map.putAll(extra)
        return JSONObject(map).toString()
    }

    private fun toggle(packageName: String, uid: Int): String {
        val outcome = try {
            if (AuthorizationManager.granted(packageName, uid)) {
                AuthorizationManager.revoke(packageName, uid)
            } else {
                AuthorizationManager.grant(packageName, uid)
            }
            GrantedCountCache.value = -1
            TOGGLE_RESULT_SUCCESS
        } catch (_: SecurityException) {
            val shizukuUid: Int? = try {
                Shizuku.getUid()
            } catch (_: Throwable) {
                null
            }
            toggleOutcome(securityException = true, shizukuUid = shizukuUid)
        }
        return snapshotJson(mapOf("result" to outcome))
    }

    private fun iconJson(packageName: String, uid: Int, sizePx: Int): String {
        val applicationInfo = cachedPackages[packageGrantKey(packageName, uid)]?.applicationInfo
        if (applicationInfo == null || sizePx <= 0) {
            return JSONObject().put("ok", false).toString()
        }
        val userId = UserHandleCompat.getUserId(uid)
        val bitmap = AppIconCache.getOrLoadBitmap(activity, applicationInfo, userId, sizePx)
            ?: return JSONObject().put("ok", false).toString()
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val png = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return JSONObject().put("ok", true).put("png", png).toString()
    }

    private fun copyMap(): Map<String, Any?> {
        val c = activity
        return linkedMapOf(
            "title" to c.getString(R.string.home_app_management_title),
            "empty" to c.getString(R.string.home_app_management_empty),
            "requiresRoot" to c.getString(R.string.app_management_item_summary_requires_root),
            "adbLimitedTitle" to c.getString(R.string.app_management_dialog_adb_is_limited_title),
            "adbLimitedMessage" to htmlToPlainText(
                c.getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get()),
            ),
            "notRunning" to c.getString(R.string.home_status_service_not_running, c.getString(R.string.app_name)),
            "back" to c.getString(R.string.action_back),
            "ok" to c.getString(android.R.string.ok),
        )
    }

    companion object {
        const val CHANNEL = "shizuku/apps"
    }
}
