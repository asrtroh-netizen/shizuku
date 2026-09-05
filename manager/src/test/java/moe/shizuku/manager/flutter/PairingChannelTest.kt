package moe.shizuku.manager.flutter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测 [PairingChannel] 同文件里的纯函数（RULEBOOK §1.8 / §7.2）：
 * [shouldRestartPairingOnResume] 必须与 `AdbPairingTutorialActivity.onResume` 的布尔条件逐字等价，
 * [buildPairingStateMap] 钉住 Dart 侧依赖的字段名。
 */
class PairingChannelTest {

    private val ready = PairingFlags(
        notificationEnabled = true,
        notificationListenerEnabled = true,
        localNetworkPermissionGranted = true,
        pairingServiceStartFailed = false,
    )

    // ───── PairingFlags 默认值 = PairingTutorialState() ─────

    @Test
    fun pairingFlags_defaultsMatchPairingTutorialState() {
        val flags = PairingFlags()
        assertFalse(flags.notificationEnabled)
        assertFalse(flags.notificationListenerEnabled)
        assertTrue(flags.localNetworkPermissionGranted)
        assertFalse(flags.pairingServiceStartFailed)
    }

    // ───── shouldRestartPairingOnResume ─────

    @Test
    fun onResume_notificationJustEnabledRestarts() {
        val old = ready.copy(notificationEnabled = false)
        assertTrue(shouldRestartPairingOnResume(old, ready))
    }

    @Test
    fun onResume_localNetworkJustGrantedRestarts() {
        val old = ready.copy(localNetworkPermissionGranted = false)
        assertTrue(shouldRestartPairingOnResume(old, ready))
    }

    @Test
    fun onResume_previousStartFailureRetriesWhenReady() {
        val failed = ready.copy(pairingServiceStartFailed = true)
        // syncState 不改 pairingServiceStartFailed：old 与 new 都带着这一位。
        assertTrue(shouldRestartPairingOnResume(failed, failed))
    }

    @Test
    fun onResume_nothingChangedAndNotFailedDoesNotRestart() {
        assertFalse(shouldRestartPairingOnResume(ready, ready))
    }

    @Test
    fun onResume_neverRestartsWhenNotificationStillDisabled() {
        val off = ready.copy(notificationEnabled = false)
        assertFalse(shouldRestartPairingOnResume(off, off))
        assertFalse(shouldRestartPairingOnResume(ready, off))
        assertFalse(shouldRestartPairingOnResume(off.copy(pairingServiceStartFailed = true), off.copy(pairingServiceStartFailed = true)))
    }

    @Test
    fun onResume_neverRestartsWhenLocalNetworkStillDenied() {
        val denied = ready.copy(localNetworkPermissionGranted = false)
        assertFalse(shouldRestartPairingOnResume(denied, denied))
        assertFalse(shouldRestartPairingOnResume(ready, denied))
        assertFalse(shouldRestartPairingOnResume(PairingFlags(), denied))
    }

    @Test
    fun onResume_freshDefaultsThenReadyRestarts() {
        // Activity 首次 onResume 前 onCreate 已同步，这里覆盖"旧状态是默认值"的极端：默认通知未开 → 补启动。
        assertTrue(shouldRestartPairingOnResume(PairingFlags(), ready))
    }

    @Test
    fun onResume_listenerFlagIsIrrelevant() {
        val listenerOff = ready.copy(notificationListenerEnabled = false)
        assertFalse(shouldRestartPairingOnResume(ready, listenerOff))
        assertFalse(shouldRestartPairingOnResume(listenerOff, ready))
    }

    // ───── buildPairingStateMap ─────

    private val copy: Map<String, Any?> = linkedMapOf(
        "title" to "Pair Shizuku with your device",
        "retry" to "Retry",
    )

    @Test
    fun buildPairingStateMap_emitsExactTopLevelFieldNamesInContractOrder() {
        val json = buildPairingStateMap(
            supported = true,
            flags = ready.copy(pairingServiceStartFailed = true),
            autoPairingEnabled = true,
            showMiuiHint = false,
            copy = copy,
        )

        assertEquals(
            listOf(
                "ok",
                "supported",
                "notificationEnabled",
                "notificationListenerEnabled",
                "localNetworkPermissionGranted",
                "pairingServiceStartFailed",
                "autoPairingEnabled",
                "showMiuiHint",
                "copy",
            ),
            json.keys.toList(),
        )
        assertEquals(true, json["ok"])
        assertEquals(true, json["supported"])
        assertEquals(true, json["notificationEnabled"])
        assertEquals(true, json["notificationListenerEnabled"])
        assertEquals(true, json["localNetworkPermissionGranted"])
        assertEquals(true, json["pairingServiceStartFailed"])
        assertEquals(true, json["autoPairingEnabled"])
        assertEquals(false, json["showMiuiHint"])
    }

    @Test
    fun buildPairingStateMap_forwardsUnsupportedAndDefaultFlags() {
        val json = buildPairingStateMap(
            supported = false,
            flags = PairingFlags(),
            autoPairingEnabled = false,
            showMiuiHint = true,
            copy = copy,
        )
        assertEquals(false, json["supported"])
        assertEquals(false, json["notificationEnabled"])
        assertEquals(false, json["notificationListenerEnabled"])
        assertEquals(true, json["localNetworkPermissionGranted"])
        assertEquals(false, json["pairingServiceStartFailed"])
        assertEquals(false, json["autoPairingEnabled"])
        assertEquals(true, json["showMiuiHint"])
    }

    @Test
    fun buildPairingStateMap_copyIsPassedThroughUntouched() {
        val json = buildPairingStateMap(
            supported = true,
            flags = ready,
            autoPairingEnabled = false,
            showMiuiHint = false,
            copy = copy,
        )
        assertTrue(json["copy"] === copy)
        assertEquals(copy, json["copy"])
    }
}
