package moe.shizuku.manager.flutter

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import moe.shizuku.manager.adb.AdbPairingNotificationListener

/**
 * 通知监听（`enabled_notification_listeners`）探测与设置页跳转的唯一实现（R2 RULEBOOK §R1），
 * 取自 `PairingChannel`（与已删 `AdbPairingTutorialActivity` 逐行同义）；`SettingsChannel` / `PairingChannel`
 * 各自的私有实现已删除，改为调用这里。
 */
internal object NotificationListenerAccess {

    /**
     * 纯函数（零 Android 依赖，供 JUnit 直测）：[flat] 是 `Settings.Secure` 里 `enabled_notification_listeners`
     * 的原始值（`":"` 分隔的扁平 ComponentName 列表），判断其中是否有条目属于 [packageName]。
     * `flat == null` → false（Pairing 版 / 已删 Activity 的 `flat != null` 语义；空串 split 后没有合法条目，同样 false）。
     *
     * 每一项按 `ComponentName.unflattenFromString` 的规则取包名：第一个 `'/'` 之前是包名，且 `'/'` 之后必须非空，
     * 否则该项非法、跳过（对应原实现里 `cn == null` 的分支）。这里不直接调 `ComponentName`：JVM 单测里它是 Stub。
     */
    fun flatContainsPackage(flat: String?, packageName: String): Boolean {
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val sep = name.indexOf('/')
                if (sep < 0 || sep + 1 >= name.length) {
                    continue
                }
                if (packageName == name.substring(0, sep)) {
                    return true
                }
            }
        }
        return false
    }

    /** 同原 `isNotificationListenerEnabled()`：`Settings.Secure.getString(...)` + [flatContainsPackage]。 */
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flatContainsPackage(flat, context.packageName)
    }

    /**
     * 打开本应用的通知监听设置：R+ 走 `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`（带 [AdbPairingNotificationListener]
     * 组件名），否则 `ACTION_NOTIFICATION_LISTENER_SETTINGS`；跳转失败时回退到总列表页，再失败则静默。
     * 两份被合并的实现在 catch 类型上不同（设置页 `Exception`、配对页 `ActivityNotFoundException`），这里取
     * 更宽的 `Exception`：设置页行为完全不变；配对页极少见的非 ANFE 异常从"上抛给通道报错"变为"静默并尝试回退"（R2 OBSERVATIONS）。
     */
    fun openSettings(activity: Activity) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(activity, AdbPairingNotificationListener::class.java).flattenToString(),
                    )
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }
}
