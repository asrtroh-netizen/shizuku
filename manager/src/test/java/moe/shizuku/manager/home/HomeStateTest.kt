package moe.shizuku.manager.home

import moe.shizuku.manager.flutter.LocaleLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测 [buildHomeStateMap]（RULEBOOK §1.8 / R2 §R3、§R8）。
 * 字段名用字面量：同时钉住 Dart `HomeSnapshot.fromJson` 依赖的线上 key。
 * 末尾一条用例把 [LocaleLabels.rows] 的产物按 `HomeActions.localeArray()` 的形状喂进来，钉住 R2 改进 I-2。
 */
class HomeStateTest {

    private val texts = HomeTexts(
        appsWaiting = "Waiting for Shizuku",
        appsUnavailable = "Binder unavailable",
        appsCount = "Authorized 3 applications",
        rootUnavailable = "Root unavailable",
        rootRestart = "Restart",
        rootStart = "Start",
    )

    private val locales: List<Map<String, Any?>> = listOf(
        linkedMapOf<String, Any?>("tag" to "SYSTEM", "label" to "Follow System", "selected" to true),
        linkedMapOf<String, Any?>("tag" to "en", "label" to "English", "selected" to false),
    )

    private val copy: Map<String, Any?> = linkedMapOf(
        "appName" to "Shizuku",
        "tabApps" to "Apps",
        "ok" to "OK",
    )

    private fun facts(
        running: Boolean = true,
        uid: Int = 2000,
        permission: Boolean = true,
        grantedCount: Int = 3,
        rooted: Boolean = true,
        sdkAtLeastR: Boolean = true,
        adbTcpPort: Int = -1,
        bootRoot: Boolean = false,
        bootWireless: Boolean = false,
        watchdog: Boolean = false,
        dark: Boolean = false,
        adbCommand: String = "adb shell sh /sdcard/start.sh",
    ) = HomeFacts(
        running = running,
        uid = uid,
        permission = permission,
        grantedCount = grantedCount,
        rooted = rooted,
        sdkAtLeastR = sdkAtLeastR,
        adbTcpPort = adbTcpPort,
        bootRoot = bootRoot,
        bootWireless = bootWireless,
        watchdog = watchdog,
        dark = dark,
        adbCommand = adbCommand,
    )

    private fun state(facts: HomeFacts): Map<String, Any?> = buildHomeStateMap(facts, texts, locales, copy)

    // ───── 字段名与顺序 ─────

    @Test
    fun buildHomeStateMap_emitsExactFieldNamesInSnapshotOrder() {
        val json = state(facts())

        val expected = listOf(
            "running",
            "state",
            "uid",
            "permission",
            "grantedCount",
            "rooted",
            "rootRestart",
            "showWireless",
            "showPair",
            "bootRoot",
            "bootWireless",
            "watchdog",
            "dark",
            "adbLimited",
            "adbCommand",
            "appsSub",
            "rootSub",
            "locales",
            "copy",
        )
        assertEquals(expected.toSet(), json.keys)
        assertEquals(expected, json.keys.toList())
    }

    // ───── state ─────

    @Test
    fun state_isReadyWhenRunningAndInactiveOtherwise() {
        assertEquals("ready", state(facts(running = true))["state"])
        assertEquals("inactive", state(facts(running = false))["state"])
    }

    // ───── rootRestart 四象限 ─────

    @Test
    fun rootRestart_isTrueOnlyWhenRunningAsRoot() {
        assertEquals(true, state(facts(running = true, uid = 0))["rootRestart"])
        assertEquals(false, state(facts(running = true, uid = 2000))["rootRestart"])
        assertEquals(false, state(facts(running = false, uid = 0))["rootRestart"])
        assertEquals(false, state(facts(running = false, uid = -1))["rootRestart"])
    }

    // ───── showWireless / showPair ─────

    @Test
    fun showWireless_isSdkROrPositiveTcpPort() {
        assertEquals(true, state(facts(sdkAtLeastR = true, adbTcpPort = -1))["showWireless"])
        assertEquals(true, state(facts(sdkAtLeastR = false, adbTcpPort = 5555))["showWireless"])
        assertEquals(false, state(facts(sdkAtLeastR = false, adbTcpPort = -1))["showWireless"])
        // 边界：端口 0 不算“已开”（原式 `getAdbTcpPort() > 0`）。
        assertEquals(false, state(facts(sdkAtLeastR = false, adbTcpPort = 0))["showWireless"])
    }

    @Test
    fun showPair_followsSdkAtLeastRRegardlessOfTcpPort() {
        assertEquals(true, state(facts(sdkAtLeastR = true, adbTcpPort = -1))["showPair"])
        assertEquals(false, state(facts(sdkAtLeastR = false, adbTcpPort = 5555))["showPair"])
    }

    // ───── adbLimited ─────

    @Test
    fun adbLimited_isRunningWithoutPermission() {
        assertEquals(true, state(facts(running = true, permission = false))["adbLimited"])
        assertEquals(false, state(facts(running = true, permission = true))["adbLimited"])
        assertEquals(false, state(facts(running = false, permission = false))["adbLimited"])
    }

    // ───── appsSub / rootSub 三分支 ─────

    @Test
    fun appsSub_picksWaitingThenUnavailableThenCount() {
        // 未运行优先于计数：即使缓存里有 3 个也显示“等待”。
        assertEquals(texts.appsWaiting, state(facts(running = false, grantedCount = 3))["appsSub"])
        assertEquals(texts.appsUnavailable, state(facts(running = true, grantedCount = -1))["appsSub"])
        // 0 是合法计数，不是“不可用”。
        assertEquals(texts.appsCount, state(facts(running = true, grantedCount = 0))["appsSub"])
        assertEquals(texts.appsCount, state(facts(running = true, grantedCount = 3))["appsSub"])
    }

    @Test
    fun rootSub_picksUnavailableThenRestartThenStart() {
        // 未 root 优先：哪怕服务正以 root 运行。
        assertEquals(texts.rootUnavailable, state(facts(rooted = false, running = true, uid = 0))["rootSub"])
        assertEquals(texts.rootRestart, state(facts(rooted = true, running = true, uid = 0))["rootSub"])
        assertEquals(texts.rootStart, state(facts(rooted = true, running = true, uid = 2000))["rootSub"])
        assertEquals(texts.rootStart, state(facts(rooted = true, running = false, uid = -1))["rootSub"])
    }

    // ───── 事实透传 ─────

    @Test
    fun factsAreForwardedVerbatim() {
        val json = state(
            facts(
                running = true,
                uid = 2000,
                permission = false,
                grantedCount = 7,
                rooted = false,
                bootRoot = true,
                bootWireless = false,
                watchdog = true,
                dark = true,
                adbCommand = "adb shell sh /sdcard/start.sh",
            ),
        )
        assertEquals(true, json["running"])
        assertEquals(2000, json["uid"])
        assertEquals(false, json["permission"])
        assertEquals(7, json["grantedCount"])
        assertEquals(false, json["rooted"])
        assertEquals(true, json["bootRoot"])
        assertEquals(false, json["bootWireless"])
        assertEquals(true, json["watchdog"])
        assertEquals(true, json["dark"])
        assertEquals("adb shell sh /sdcard/start.sh", json["adbCommand"])
    }

    @Test
    fun uidStaysMinusOneWhenNotRunning() {
        val json = state(facts(running = false, uid = -1, grantedCount = -1))
        assertEquals(-1, json["uid"])
        assertEquals(-1, json["grantedCount"])
    }

    @Test
    fun localesAndCopyArePassedThroughUntouched() {
        val json = state(facts())

        assertTrue(json["locales"] === locales)
        assertEquals(locales, json["locales"])
        val system = (json["locales"] as List<*>)[0] as Map<*, *>
        assertEquals(setOf("tag", "label", "selected"), system.keys)
        assertEquals("SYSTEM", system["tag"])
        assertEquals(true, system["selected"])

        assertTrue(json["copy"] === copy)
        assertEquals(copy, json["copy"])
    }

    // ───── I-2：Lang 芯片改用 LocaleLabels 全表（R2 SPEC §6） ─────

    @Test
    fun locales_builtFromLocaleLabelsRows_showFullLanguageNames() {
        // 与 HomeActions.localeArray() 同构：LocaleLabels.rows → 每项恰好 tag / label / selected。
        val rows = LocaleLabels.rows(
            listOf("SYSTEM", "en", "de", "zh-CN"),
            currentTag = "de",
            systemLabel = "Follow System",
        ).map { linkedMapOf<String, Any?>("tag" to it.tag, "label" to it.label, "selected" to it.selected) }

        val locales = buildHomeStateMap(facts(), texts, rows, copy)["locales"] as List<*>
        val items = locales.map { it as Map<*, *> }

        assertEquals(listOf("SYSTEM", "en", "de", "zh-CN"), items.map { it["tag"] })
        // 旧首页 5 条 when 表对 de 只会原样吐出 "de"；全表下必须是 Deutsch，首项仍用系统标签。
        assertEquals(listOf("Follow System", "English", "Deutsch", "简体中文"), items.map { it["label"] })
        assertEquals(listOf(false, false, true, false), items.map { it["selected"] })
        items.forEach { assertEquals(setOf("tag", "label", "selected"), it.keys) }
    }
}
