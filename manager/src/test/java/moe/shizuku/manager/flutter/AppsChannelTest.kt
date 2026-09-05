package moe.shizuku.manager.flutter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsChannelTest {

    private val copy: Map<String, Any?> = linkedMapOf(
        "title" to "Application management",
        "empty" to "Nothing here",
        "ok" to "OK",
    )

    private fun rows(): List<AppRow> = listOf(
        AppRow(
            packageName = "a.b",
            uid = 10001,
            userId = 0,
            label = "A",
            requiresRoot = false,
            granted = true,
        ),
        AppRow(
            packageName = "c.d",
            uid = 1010002,
            userId = 10,
            label = "C - Work (10)",
            requiresRoot = true,
            granted = false,
        ),
    )

    @Test
    fun notRunning_forcesEmptyAppsEvenWhenRowsAreGiven() {
        val json = buildAppsStateJson(running = false, adbLimited = false, apps = rows(), copy = copy)

        assertEquals(true, json["ok"])
        assertEquals(false, json["running"])
        assertEquals(false, json["adbLimited"])
        assertEquals(emptyList<Any?>(), json["apps"])
    }

    @Test
    fun running_emitsRowsWithExactFieldNames() {
        val json = buildAppsStateJson(running = true, adbLimited = true, apps = rows(), copy = copy)

        assertEquals(setOf("ok", "running", "adbLimited", "apps", "copy"), json.keys)
        assertEquals(true, json["running"])
        assertEquals(true, json["adbLimited"])

        val apps = json["apps"] as List<*>
        assertEquals(2, apps.size)

        val first = apps[0] as Map<*, *>
        assertEquals(
            setOf("packageName", "uid", "userId", "label", "requiresRoot", "granted"),
            first.keys,
        )
        assertEquals("a.b", first["packageName"])
        assertEquals(10001, first["uid"])
        assertEquals(0, first["userId"])
        assertEquals("A", first["label"])
        assertEquals(false, first["requiresRoot"])
        assertEquals(true, first["granted"])

        val second = apps[1] as Map<*, *>
        assertEquals("c.d", second["packageName"])
        assertEquals(1010002, second["uid"])
        assertEquals(10, second["userId"])
        assertEquals("C - Work (10)", second["label"])
        assertEquals(true, second["requiresRoot"])
        assertEquals(false, second["granted"])
    }

    @Test
    fun copy_isPassedThroughUntouchedUnderCopyKey() {
        val json = buildAppsStateJson(running = false, adbLimited = false, apps = emptyList(), copy = copy)

        assertTrue(json["copy"] === copy)
        assertEquals(copy, json["copy"])
    }

    @Test
    fun toggleOutcome_matchesComposeOnTogglePackageSemantics() {
        assertEquals("adbLimited", toggleOutcome(securityException = true, shizukuUid = 2000))
        assertEquals("success", toggleOutcome(securityException = true, shizukuUid = 0))
        assertEquals("success", toggleOutcome(securityException = true, shizukuUid = null))
        assertEquals("success", toggleOutcome(securityException = false, shizukuUid = 2000))
        assertEquals("success", toggleOutcome(securityException = false, shizukuUid = 0))
        assertEquals("success", toggleOutcome(securityException = false, shizukuUid = null))
    }

    @Test
    fun toggleOutcome_constantsMatchDartContractStrings() {
        assertEquals("success", TOGGLE_RESULT_SUCCESS)
        assertEquals("adbLimited", TOGGLE_RESULT_ADB_LIMITED)
    }
}
