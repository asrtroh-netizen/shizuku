package moe.shizuku.manager.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class RootBootStartWorkerTest {

    @Test
    fun staleRootWorkDoesNotStealWirelessRecoveryOwnership() {
        assertEquals(
            false,
            shouldDisarmRecoveryForInactiveRootWork(
                rootBootEnabled = false,
                wirelessBootEnabled = true,
                isPrimaryUser = true,
            ),
        )
    }

    @Test
    fun inactiveAutostartOrSecondaryUserDisarmsRecovery() {
        assertEquals(
            true,
            shouldDisarmRecoveryForInactiveRootWork(
                rootBootEnabled = false,
                wirelessBootEnabled = false,
                isPrimaryUser = true,
            ),
        )
        assertEquals(
            true,
            shouldDisarmRecoveryForInactiveRootWork(
                rootBootEnabled = false,
                wirelessBootEnabled = true,
                isPrimaryUser = false,
            ),
        )
    }

    @Test
    fun commandSuccessRequiresBinderReadiness() {
        val commandResult = RootStartResult.success(RootStartMethod.LIBSU)

        assertEquals(
            RootBootAttemptOutcome.RETRY,
            rootBootAttemptOutcome(commandResult, binderReady = false, runAttemptCount = 0),
        )
        assertEquals(
            RootBootAttemptOutcome.SUCCESS,
            rootBootAttemptOutcome(commandResult, binderReady = true, runAttemptCount = 0),
        )
    }

    @Test
    fun firstTwoFailuresRetry() {
        val commandResult = RootStartResult.failure(RootStartMethod.LIBSU, exitCode = 1)

        assertEquals(
            RootBootAttemptOutcome.RETRY,
            rootBootAttemptOutcome(commandResult, binderReady = false, runAttemptCount = 0),
        )
        assertEquals(
            RootBootAttemptOutcome.RETRY,
            rootBootAttemptOutcome(commandResult, binderReady = false, runAttemptCount = 1),
        )
    }

    @Test
    fun thirdFailureEndsWorkerGeneration() {
        val commandResult = RootStartResult.failure(RootStartMethod.NATIVE_SU, exitCode = 1)

        assertEquals(
            RootBootAttemptOutcome.FAILURE,
            rootBootAttemptOutcome(commandResult, binderReady = false, runAttemptCount = 2),
        )
    }
}
