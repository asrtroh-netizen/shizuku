package moe.shizuku.manager.flutter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEventGateTest {

    @Test
    fun hostResumeMustNotEmitBecauseDartLifecycleAlreadyRefreshes() {
        assertFalse(shouldEmitHomeEvent(dartExecuting = true, hostResumed = true, triggeredByHostResume = true))
    }

    @Test
    fun binderChangeMayEmitOnlyWhenDartIsUpAndHostResumed() {
        assertTrue(shouldEmitHomeEvent(dartExecuting = true, hostResumed = true, triggeredByHostResume = false))
        assertFalse(shouldEmitHomeEvent(dartExecuting = false, hostResumed = true, triggeredByHostResume = false))
        assertFalse(shouldEmitHomeEvent(dartExecuting = true, hostResumed = false, triggeredByHostResume = false))
    }
}
