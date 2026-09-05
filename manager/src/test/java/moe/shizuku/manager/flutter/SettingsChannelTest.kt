package moe.shizuku.manager.flutter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测 [SettingsChannel] 同文件里的纯函数（RULEBOOK §1.8 / §7.2）。
 * key 用字面量而不是 `ShizukuSettings.*` 常量：同时钉住 Dart 侧 `setBool` 依赖的线上字符串。
 */
class SettingsChannelTest {

    private val bootRootKey = "start_on_boot"
    private val bootWirelessKey = "start_on_boot_wireless"

    private val copy: Map<String, Any?> = linkedMapOf(
        "title" to "Settings",
        "startup" to "Startup",
        "ok" to "OK",
    )

    // ───── applyBootToggle ─────

    @Test
    fun applyBootToggle_enablingRootForcesWirelessOff() {
        val next = applyBootToggle(BootPrefs(bootRoot = false, bootWireless = true), bootRootKey, checked = true)
        assertEquals(BootPrefs(bootRoot = true, bootWireless = false), next)
    }

    @Test
    fun applyBootToggle_enablingWirelessForcesRootOff() {
        val next = applyBootToggle(BootPrefs(bootRoot = true, bootWireless = false), bootWirelessKey, checked = true)
        assertEquals(BootPrefs(bootRoot = false, bootWireless = true), next)
    }

    @Test
    fun applyBootToggle_disablingEitherLeavesTheOtherUntouched() {
        assertEquals(
            BootPrefs(bootRoot = false, bootWireless = true),
            applyBootToggle(BootPrefs(bootRoot = true, bootWireless = true), bootRootKey, checked = false),
        )
        assertEquals(
            BootPrefs(bootRoot = true, bootWireless = false),
            applyBootToggle(BootPrefs(bootRoot = true, bootWireless = true), bootWirelessKey, checked = false),
        )
        assertEquals(
            BootPrefs(bootRoot = false, bootWireless = false),
            applyBootToggle(BootPrefs(bootRoot = false, bootWireless = false), bootRootKey, checked = false),
        )
    }

    @Test
    fun applyBootToggle_unknownKeyReturnsCurrentUnchanged() {
        val current = BootPrefs(bootRoot = true, bootWireless = false)
        assertEquals(current, applyBootToggle(current, "watchdog_enabled_adb", checked = true))
    }

    // ───── parseTcpipPort ─────

    @Test
    fun parseTcpipPort_blankMeansClear() {
        assertNull(parseTcpipPort(""))
        assertNull(parseTcpipPort("   "))
    }

    @Test
    fun parseTcpipPort_acceptsValidPortsAndTrims() {
        assertEquals(5555, parseTcpipPort("5555"))
        assertEquals(5555, parseTcpipPort(" 5555 "))
        // Compose 边界：R.string.dialog_adb_invalid_port 原文 "ranging from 10 to 65535"。
        assertEquals(10, parseTcpipPort("10"))
        assertEquals(65535, parseTcpipPort("65535"))
    }

    @Test
    fun parseTcpipPort_rejectsOutOfRangeAndNonNumeric() {
        assertThrows(IllegalArgumentException::class.java) { parseTcpipPort("0") }
        assertThrows(IllegalArgumentException::class.java) { parseTcpipPort("9") }
        assertThrows(IllegalArgumentException::class.java) { parseTcpipPort("65536") }
        assertThrows(IllegalArgumentException::class.java) { parseTcpipPort("abc") }
        assertThrows(IllegalArgumentException::class.java) { parseTcpipPort("-5555") }
        assertThrows(IllegalArgumentException::class.java) { parseTcpipPort("99999999999") }
    }

    // ───── localeLabel / buildLocaleRows ─────

    @Test
    fun localeLabel_matchesComposeTableAndFallsBackToTag() {
        assertEquals("简体中文", localeLabel("zh-CN"))
        assertEquals("繁體中文", localeLabel("zh-TW"))
        assertEquals("English", localeLabel("en"))
        assertEquals("日本語", localeLabel("ja"))
        assertEquals("한국어", localeLabel("ko"))
        assertEquals("Español (Latinoamérica)", localeLabel("es-419"))
        assertEquals("xx-YY", localeLabel("xx-YY"))
    }

    @Test
    fun buildLocaleRows_firstIsSystemLabelAndSelectionFollowsCurrentTag() {
        val rows = buildLocaleRows(listOf("SYSTEM", "en", "zh-CN"), currentTag = "zh-CN", systemLabel = "Follow System")

        assertEquals(3, rows.size)
        assertEquals(LocaleRow("SYSTEM", "Follow System", selected = false), rows[0])
        assertEquals(LocaleRow("en", "English", selected = false), rows[1])
        assertEquals(LocaleRow("zh-CN", "简体中文", selected = true), rows[2])
    }

    @Test
    fun buildLocaleRows_systemSelectedByDefaultTag() {
        val rows = buildLocaleRows(listOf("SYSTEM", "en"), currentTag = "SYSTEM", systemLabel = "Follow System")
        assertEquals(listOf(true, false), rows.map { it.selected })
    }

    // ───── buildSettingsStateMap ─────

    private fun state(tcpipPort: String?): Map<String, Any?> = buildSettingsStateMap(
        supportsStartOnBoot = true,
        boot = BootPrefs(bootRoot = true, bootWireless = false),
        autoPairing = true,
        watchdog = false,
        tcpipPort = tcpipPort,
        nightMode = -1,
        nightModeOptions = listOf(
            NightModeOption(1, "Off"),
            NightModeOption(2, "On"),
            NightModeOption(-1, "Follow system"),
        ),
        blackNightTheme = false,
        useSystemColor = true,
        locales = listOf(
            LocaleRow("SYSTEM", "Follow System", selected = true),
            LocaleRow("en", "English", selected = false),
        ),
        translationUrl = "https://rikka.app/contribute_translation/",
        copy = copy,
    )

    @Test
    fun buildSettingsStateMap_emitsExactTopLevelFieldNames() {
        val json = state(tcpipPort = "5555")

        assertEquals(
            setOf(
                "ok",
                "supportsStartOnBoot",
                "bootRoot",
                "bootWireless",
                "autoPairing",
                "watchdog",
                "tcpipPort",
                "nightMode",
                "nightModeOptions",
                "blackNightTheme",
                "useSystemColor",
                "locales",
                "translationUrl",
                "copy",
            ),
            json.keys,
        )
        assertEquals(true, json["ok"])
        assertEquals(true, json["supportsStartOnBoot"])
        assertEquals(true, json["bootRoot"])
        assertEquals(false, json["bootWireless"])
        assertEquals(true, json["autoPairing"])
        assertEquals(false, json["watchdog"])
        assertEquals("5555", json["tcpipPort"])
        assertEquals(-1, json["nightMode"])
        assertEquals(false, json["blackNightTheme"])
        assertEquals(true, json["useSystemColor"])
        assertEquals("https://rikka.app/contribute_translation/", json["translationUrl"])
    }

    @Test
    fun buildSettingsStateMap_unsetTcpipPortIsEmptyStringNotNull() {
        assertEquals("", state(tcpipPort = null)["tcpipPort"])
        assertEquals("", state(tcpipPort = "")["tcpipPort"])
    }

    @Test
    fun buildSettingsStateMap_nightModeOptionsAndLocalesUseContractFieldNames() {
        val json = state(tcpipPort = "")

        val options = json["nightModeOptions"] as List<*>
        assertEquals(3, options.size)
        val first = options[0] as Map<*, *>
        assertEquals(setOf("value", "label"), first.keys)
        assertEquals(1, first["value"])
        assertEquals("Off", first["label"])
        assertEquals(-1, (options[2] as Map<*, *>)["value"])

        val locales = json["locales"] as List<*>
        assertEquals(2, locales.size)
        val system = locales[0] as Map<*, *>
        assertEquals(setOf("tag", "label", "selected"), system.keys)
        assertEquals("SYSTEM", system["tag"])
        assertEquals("Follow System", system["label"])
        assertEquals(true, system["selected"])
        assertEquals(false, (locales[1] as Map<*, *>)["selected"])
    }

    @Test
    fun buildSettingsStateMap_copyIsPassedThroughUntouched() {
        val json = state(tcpipPort = "")
        assertTrue(json["copy"] === copy)
        assertEquals(copy, json["copy"])
    }

    @Test
    fun buildSettingsStateMap_supportsStartOnBootFalseIsForwarded() {
        val json = buildSettingsStateMap(
            supportsStartOnBoot = false,
            boot = BootPrefs(bootRoot = false, bootWireless = false),
            autoPairing = false,
            watchdog = false,
            tcpipPort = null,
            nightMode = 2,
            nightModeOptions = emptyList(),
            blackNightTheme = true,
            useSystemColor = false,
            locales = emptyList(),
            translationUrl = "",
            copy = copy,
        )
        assertEquals(false, json["supportsStartOnBoot"])
        assertEquals(2, json["nightMode"])
        assertEquals(emptyList<Any?>(), json["nightModeOptions"])
        assertEquals(emptyList<Any?>(), json["locales"])
    }
}
