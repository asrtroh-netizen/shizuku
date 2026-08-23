package moe.shizuku.manager.management

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantStatesTest {

    @Test
    fun buildGrantStateMap_replacesWholeMapSoStaleKeysDisappear() {
        val first = buildGrantStateMap(
            listOf(GrantQuery("old.app", 1), GrantQuery("keep.app", 2)),
        ) { pkg, _ -> pkg == "keep.app" }
        val second = buildGrantStateMap(
            listOf(GrantQuery("keep.app", 2)),
        ) { _, _ -> true }

        assertEquals(mapOf("old.app#1" to false, "keep.app#2" to true), first)
        assertEquals(mapOf("keep.app#2" to true), second)
        assertFalse(second.containsKey("old.app#1"))
    }

    @Test
    fun loadGrantStates_runsGrantedCallbackOffCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val queryThreads = mutableListOf<Thread>()

        loadGrantStates(listOf(GrantQuery("a.b", 99))) { _, _ ->
            queryThreads += Thread.currentThread()
            true
        }

        assertTrue(queryThreads.isNotEmpty())
        assertTrue(
            "granted() must not run on the UI/test caller thread, was ${queryThreads.map { it.name }}",
            queryThreads.none { it === caller },
        )
    }

    @Test
    fun resolveGrantedCount_usesCacheAndDoesNotScan() {
        var scanned = false
        val count = resolveGrantedCount(4) {
            scanned = true
            99
        }
        assertEquals(4, count)
        assertFalse(scanned)
    }

    @Test
    fun resolveGrantedCount_scansWhenCacheEmpty() {
        val count = resolveGrantedCount(-1) { 6 }
        assertEquals(6, count)
        assertEquals(6, GrantedCountCache.value)
        GrantedCountCache.value = -1
    }
}
