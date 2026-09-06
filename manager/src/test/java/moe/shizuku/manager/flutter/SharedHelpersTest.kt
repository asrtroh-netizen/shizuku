package moe.shizuku.manager.flutter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测 R2 共享工具里的纯函数（RULEBOOK §1.8 / R2 §R8）：[NotificationListenerAccess.flatContainsPackage]
 * 与 [LocaleLabels]。包名用字面量传入，不依赖 `BuildConfig`；`applyBootToggle` / `parseTcpipPort` 的用例
 * 仍在 [SettingsChannelTest]（不重复）。
 */
class SharedHelpersTest {

    private val pkg = "moe.shizuku.privileged.api"

    // ───── NotificationListenerAccess.flatContainsPackage ─────

    @Test
    fun flatContainsPackage_nullFlatIsFalse() {
        assertFalse(NotificationListenerAccess.flatContainsPackage(null, pkg))
    }

    @Test
    fun flatContainsPackage_emptyFlatIsFalse() {
        assertFalse(NotificationListenerAccess.flatContainsPackage("", pkg))
    }

    @Test
    fun flatContainsPackage_findsOurPackageAmongOthers() {
        assertTrue(NotificationListenerAccess.flatContainsPackage("a/b:$pkg/x", pkg))
        assertTrue(NotificationListenerAccess.flatContainsPackage("$pkg/x", pkg))
        // 前后多余的分隔符与相对类名（".Listener"）都按 ComponentName.unflattenFromString 的规则接受。
        assertTrue(NotificationListenerAccess.flatContainsPackage(":$pkg/.Listener:", pkg))
    }

    @Test
    fun flatContainsPackage_onlyOtherPackagesIsFalse() {
        assertFalse(NotificationListenerAccess.flatContainsPackage("other/x", pkg))
        assertFalse(NotificationListenerAccess.flatContainsPackage("other/x:another/y", pkg))
    }

    @Test
    fun flatContainsPackage_malformedEntriesAreSkipped() {
        assertFalse(NotificationListenerAccess.flatContainsPackage("garbage::", pkg))
        // 没有 '/' 或 '/' 之后为空 → unflattenFromString 返回 null → 该项跳过。
        assertFalse(NotificationListenerAccess.flatContainsPackage(pkg, pkg))
        assertFalse(NotificationListenerAccess.flatContainsPackage("$pkg/", pkg))
        // 非法项不影响其后的合法项。
        assertTrue(NotificationListenerAccess.flatContainsPackage("garbage::$pkg/:$pkg/x", pkg))
    }

    @Test
    fun flatContainsPackage_matchesWholePackageNameNotPrefix() {
        assertFalse(NotificationListenerAccess.flatContainsPackage("$pkg.extra/x", pkg))
        assertFalse(NotificationListenerAccess.flatContainsPackage("moe.shizuku/x", pkg))
    }

    // ───── LocaleLabels.label ─────

    @Test
    fun label_knownTagsUseTheComposeTable() {
        assertEquals("简体中文", LocaleLabels.label("zh-CN"))
        // 首页 Lang 芯片改进 I-2 依赖这一条：`de` 不能再原样透出。
        assertEquals("Deutsch", LocaleLabels.label("de"))
    }

    @Test
    fun label_unknownTagIsReturnedVerbatim() {
        assertEquals("xx-YY", LocaleLabels.label("xx-YY"))
        assertEquals("", LocaleLabels.label(""))
        // 大小写敏感：表里只有 "zh-CN"。
        assertEquals("zh-cn", LocaleLabels.label("zh-cn"))
    }

    // ───── LocaleLabels.rows ─────

    @Test
    fun rows_firstIsSystemWithGivenLabelAndOnlyCurrentTagIsSelected() {
        val rows = LocaleLabels.rows(listOf("SYSTEM", "en", "de"), currentTag = "de", systemLabel = "Follow System")

        assertEquals(3, rows.size)
        assertEquals(LocaleRow("SYSTEM", "Follow System", selected = false), rows[0])
        assertEquals(LocaleRow("en", "English", selected = false), rows[1])
        assertEquals(LocaleRow("de", "Deutsch", selected = true), rows[2])
        assertEquals(1, rows.count { it.selected })
    }

    @Test
    fun rows_systemTagSelectedWhenCurrentIsSystem() {
        val rows = LocaleLabels.rows(listOf("SYSTEM", "en"), currentTag = "SYSTEM", systemLabel = "Follow System")
        assertEquals(listOf(true, false), rows.map { it.selected })
    }

    @Test
    fun rows_currentTagNotInListSelectsNothingAndKeepsOrder() {
        val rows = LocaleLabels.rows(listOf("SYSTEM", "en", "zh-CN"), currentTag = "fr", systemLabel = "System")
        assertEquals(listOf("SYSTEM", "en", "zh-CN"), rows.map { it.tag })
        assertEquals(listOf("System", "English", "简体中文"), rows.map { it.label })
        assertEquals(0, rows.count { it.selected })
    }
}
