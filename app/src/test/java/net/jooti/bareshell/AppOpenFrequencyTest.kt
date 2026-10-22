package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenFrequencyTest {
    @Test
    fun internalActivityChangesAreNotExtraOpensButReturningFromAnotherPackageIs() {
        val events = listOf(
            ForegroundEvent(1, "a"), ForegroundEvent(2, "a"), ForegroundEvent(3, "launcher"),
            ForegroundEvent(4, "a"), ForegroundEvent(5, "b"),
        )
        assertEquals(mapOf("a" to 2L, "launcher" to 1L, "b" to 1L), countForegroundEntries(events, 0, 10))
    }

    @Test
    fun screenOrDeviceSessionEndAllowsTheSamePackageToOpenAgain() {
        val events = listOf(ForegroundEvent(1, "a"), ForegroundEvent(2, null), ForegroundEvent(3, "a"))
        assertEquals(mapOf("a" to 2L), countForegroundEntries(events, 0, 10))
    }

    @Test
    fun timestampWindowIsStartInclusiveEndExclusiveAndInputCanBeUnsorted() {
        val events = listOf(ForegroundEvent(10, "end"), ForegroundEvent(0, "old"),
            ForegroundEvent(5, "included"), ForegroundEvent(6, "included"))
        assertEquals(mapOf("included" to 1L), countForegroundEntries(events, 5, 10))
        assertTrue(countForegroundEntries(emptyList(), 0, 10).isEmpty())
    }
}
