package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactScoringTest {
    @Test
    fun eachInteractionAddsOneAfterDecayAndKeepsRecencySeparate() {
        val score = ContactScore(5, 100, 1000)
        assertEquals(ContactScore(5, 101, 2000), score.interact(101, 2000))
        assertEquals(ContactScore(6, 101, 3000), score.interact(101, 2000).interact(101, 3000))
        assertEquals(1000, score.decay(101).lastInteraction.toInt())
    }

    @Test
    fun missingDaysDecayLazilyWithoutNegativePointsOrDoubleDecay() {
        val score = ContactScore(3, 100, 1000)
        assertEquals(ContactScore(1, 102, 1000), score.decay(102))
        assertEquals(score.decay(102), score.decay(102).decay(102))
        assertEquals(ContactScore(0, 200, 1000), score.decay(200))
        assertEquals(ContactScore(1, 200, 2000), score.interact(200, 2000))
    }

    @Test
    fun clockMovingBackwardDoesNotCreatePointsOrReapplyOldDays() {
        val score = ContactScore(3, 100, 1000)
        assertEquals(score, score.decay(90))
        val interacted = score.interact(90, 900)
        assertEquals(ContactScore(4, 100, 1000), interacted)
        assertEquals(ContactScore(3, 101, 1000), interacted.decay(101))
    }

    @Test
    fun mergingPreservesPointsAndRecencyWithoutOverflow() {
        assertEquals(ContactScore(7, 100, 2000), ContactScore(3, 100, 1000).merge(ContactScore(4, 100, 2000)))
        val max = ContactScore(Int.MAX_VALUE, 100, 1000)
        assertEquals(Int.MAX_VALUE, max.interact(100, 2000).points)
        assertEquals(Int.MAX_VALUE, max.merge(max).points)
    }
}
