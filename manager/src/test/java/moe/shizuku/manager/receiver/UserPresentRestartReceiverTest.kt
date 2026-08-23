package moe.shizuku.manager.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPresentRestartReceiverTest {

    @Test
    fun userPresentRetriesSelectedRootBootModeAndKeepsRecoveryArmed() {
        var recoveryEnabled = false
        var rootStarts = 0
        var wirelessStarts = 0

        val failure = dispatchUserPresentRestart(
            rootBoot = true,
            wirelessBoot = false,
            setRecoveryEnabled = { recoveryEnabled = it },
            startRoot = { rootStarts++ },
            startWireless = { wirelessStarts++ },
        )

        assertEquals(null, failure)
        assertTrue(recoveryEnabled)
        assertEquals(1, rootStarts)
        assertEquals(0, wirelessStarts)
    }

    @Test
    fun rootEnqueueFailureKeepsUnlockRecoveryArmed() {
        var recoveryEnabled = false

        val failure = dispatchUserPresentRestart(
            rootBoot = true,
            wirelessBoot = false,
            setRecoveryEnabled = { recoveryEnabled = it },
            startRoot = { throw IllegalStateException("WorkManager unavailable") },
            startWireless = { error("wireless must not mask a Root failure") },
        )

        assertNotNull(failure)
        assertTrue(recoveryEnabled)
    }

    @Test
    fun wirelessModeDisarmsRootRecoveryAndStartsWireless() {
        var recoveryEnabled = true
        var rootStarts = 0
        var wirelessStarts = 0

        dispatchUserPresentRestart(
            rootBoot = false,
            wirelessBoot = true,
            setRecoveryEnabled = { recoveryEnabled = it },
            startRoot = { rootStarts++ },
            startWireless = { wirelessStarts++ },
        )

        assertFalse(recoveryEnabled)
        assertEquals(0, rootStarts)
        assertEquals(1, wirelessStarts)
    }

    @Test
    fun disabledAutostartDisarmsUnlockRecovery() {
        var recoveryEnabled = true
        var startCalls = 0

        dispatchUserPresentRestart(
            rootBoot = false,
            wirelessBoot = false,
            setRecoveryEnabled = { recoveryEnabled = it },
            startRoot = { startCalls++ },
            startWireless = { startCalls++ },
        )

        assertFalse(recoveryEnabled)
        assertEquals(0, startCalls)
    }
}
