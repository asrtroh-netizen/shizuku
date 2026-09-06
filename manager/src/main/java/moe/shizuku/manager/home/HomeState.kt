package moe.shizuku.manager.home

// 首页快照的纯派生逻辑（RULEBOOK R2 §R3）：零 Android / JSON 库依赖，供 JUnit 直测。
// 字段名与顺序即线上 JSON（Dart `HomeSnapshot.fromJson` 依赖），一个都不能改。

/** 首页快照的原始事实，由 [HomeActions.snapshot] 在 Android 侧采集后传入。 */
internal data class HomeFacts(
    val running: Boolean,
    val uid: Int,
    val permission: Boolean,
    val grantedCount: Int,
    val rooted: Boolean,
    val sdkAtLeastR: Boolean,
    val adbTcpPort: Int,
    val bootRoot: Boolean,
    val bootWireless: Boolean,
    val watchdog: Boolean,
    val dark: Boolean,
    val adbCommand: String,
)

/**
 * `appsSub` / `rootSub` 的候选文案；`R.string` 与 `getQuantityString` 结果由调用方算好传入，
 * 这里只按事实三选一。
 */
internal data class HomeTexts(
    val appsWaiting: String,
    val appsUnavailable: String,
    val appsCount: String,
    val rootUnavailable: String,
    val rootRestart: String,
    val rootStart: String,
)

/**
 * 首页整页快照（不含 `ok`，与原 `HomeActions.snapshot()` 的 `JSONObject().put(...)` 链逐字同序）：
 * `state = running ? "ready" : "inactive"`、`rootRestart = running && uid == 0`、
 * `showWireless = sdkAtLeastR || adbTcpPort > 0`、`showPair = sdkAtLeastR`、`adbLimited = running && !permission`；
 * `appsSub`：未运行 → [HomeTexts.appsWaiting]，`grantedCount < 0` → [HomeTexts.appsUnavailable]，否则 [HomeTexts.appsCount]；
 * `rootSub`：未 root → [HomeTexts.rootUnavailable]，运行中且 uid 0 → [HomeTexts.rootRestart]，否则 [HomeTexts.rootStart]。
 * [locales]（每项 `tag / label / selected`）与 [copy] 原样透传。
 */
internal fun buildHomeStateMap(
    facts: HomeFacts,
    texts: HomeTexts,
    locales: List<Map<String, Any?>>,
    copy: Map<String, Any?>,
): Map<String, Any?> {
    val running = facts.running
    val uid = facts.uid
    val permission = facts.permission
    val grantedCount = facts.grantedCount
    val rooted = facts.rooted
    val showWireless = facts.sdkAtLeastR || facts.adbTcpPort > 0
    val appsSub = when {
        !running -> texts.appsWaiting
        grantedCount < 0 -> texts.appsUnavailable
        else -> texts.appsCount
    }
    val rootSub = when {
        !rooted -> texts.rootUnavailable
        running && uid == 0 -> texts.rootRestart
        else -> texts.rootStart
    }
    return linkedMapOf(
        "running" to running,
        "state" to if (running) "ready" else "inactive",
        "uid" to uid,
        "permission" to permission,
        "grantedCount" to grantedCount,
        "rooted" to rooted,
        "rootRestart" to (running && uid == 0),
        "showWireless" to showWireless,
        "showPair" to facts.sdkAtLeastR,
        "bootRoot" to facts.bootRoot,
        "bootWireless" to facts.bootWireless,
        "watchdog" to facts.watchdog,
        "dark" to facts.dark,
        "adbLimited" to (running && !permission),
        "adbCommand" to facts.adbCommand,
        "appsSub" to appsSub,
        "rootSub" to rootSub,
        "locales" to locales,
        "copy" to copy,
    )
}
