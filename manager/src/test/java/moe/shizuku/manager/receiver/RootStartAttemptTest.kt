package moe.shizuku.manager.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootStartAttemptTest {

    @Test
    fun retriesLibsuShellAcquisitionBeforeFallingBack() {
        val shellStates = ArrayDeque(listOf(false, true))
        var resetCount = 0
        var nativeFallbackCalled = false

        val result = runRootStartAttempt(
            hasRootShell = { shellStates.removeFirst() },
            resetCachedShell = { resetCount++ },
            executeLibsu = { 0 },
            executeNativeSu = {
                nativeFallbackCalled = true
                RootStartResult.success(RootStartMethod.NATIVE_SU, 0)
            },
        )

        assertTrue(result is RootStartResult.Success)
        assertEquals(RootStartMethod.LIBSU, result.method)
        assertEquals(1, resetCount)
        assertFalse(nativeFallbackCalled)
    }

    @Test
    fun exposesLibsuCommandFailureInsteadOfTreatingItAsSuccess() {
        val result = runRootStartAttempt(
            hasRootShell = { true },
            resetCachedShell = {},
            executeLibsu = { 17 },
            executeNativeSu = { error("native fallback must not mask a command failure") },
        )

        assertTrue(result is RootStartResult.Failure)
        assertEquals(RootStartMethod.LIBSU, result.method)
        assertEquals(17, (result as RootStartResult.Failure).exitCode)
    }

    @Test
    fun usesNativeSuOnlyWhenLibsuCannotAcquireRoot() {
        val result = runRootStartAttempt(
            hasRootShell = { false },
            resetCachedShell = {},
            executeLibsu = { error("libsu command requires a root shell") },
            executeNativeSu = { RootStartResult.success(RootStartMethod.NATIVE_SU, 0) },
        )

        assertTrue(result is RootStartResult.Success)
        assertEquals(RootStartMethod.NATIVE_SU, result.method)
    }
}
