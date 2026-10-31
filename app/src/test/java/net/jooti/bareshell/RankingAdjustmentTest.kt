package net.jooti.bareshell

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingAdjustmentTest {
    @Test fun signedBalancesDecayTowardZeroWithoutCrossingIt() {
        assertEquals(RankingAdjustment(7, 103), RankingAdjustment(10, 100).decay(103))
        assertEquals(RankingAdjustment(-7, 103), RankingAdjustment(-10, 100).decay(103))
        listOf(10, -10, 0, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertEquals(0, RankingAdjustment(it, 100).decay(100L + Int.MAX_VALUE + 1).points)
        }
        val current = RankingAdjustment(-7, 103)
        assertEquals(current, current.decay(103))
        assertEquals(current, current.decay(90))
    }

    @Test fun adjustmentsApplyMissedDecayAndAccumulateOrCancel() {
        assertEquals(RankingAdjustment(17, 103), RankingAdjustment(10, 100).adjust(10, 103))
        assertEquals(RankingAdjustment(-3, 103), RankingAdjustment(10, 100).adjust(-10, 103))
        assertEquals(RankingAdjustment(0, 100), RankingAdjustment(10, 100).adjust(-10, 100))
        assertEquals(RankingAdjustment(-20, 100), RankingAdjustment(-10, 100).adjust(-10, 90))
        assertEquals(RankingAdjustment(-19, 101), RankingAdjustment(-10, 100).adjust(-10, 90).decay(101))
        assertEquals(Int.MAX_VALUE, RankingAdjustment(Int.MAX_VALUE, 100).adjust(10, 100).points)
        assertEquals(Int.MIN_VALUE, RankingAdjustment(Int.MIN_VALUE, 100).adjust(-10, 100).points)
    }

    @Test fun contactDecayAndMergingKeepManualPointsSeparateFromInteractions() {
        val score = ContactScore(3, 100, 1000, RankingAdjustment(-10, 100))
        assertEquals(ContactScore(2, 102, 2000, RankingAdjustment(-8, 102)), score.interact(102, 2000))
        val merged = score.decay(102).merge(ContactScore(2, 102, 900, RankingAdjustment(3, 102)))
        assertEquals(ContactScore(3, 102, 1000, RankingAdjustment(-5, 102)), merged)
    }

    @Test fun screenTimePointsUseCompleteAccumulatedTenMinuteUnits() {
        val apps = listOf(AppEntry("z", "Zulu", "Activity"), AppEntry("a", "Alpha", "Activity"))
        fun order(time: Long) = rankApps(apps, listOf(ForegroundUsage("z", time)), "home", Locale.US)
        assertEquals(listOf("a", "z"), order(599_999).map { it.packageName })
        assertEquals(listOf("z", "a"), order(600_000).map { it.packageName })
        val usage = listOf(ForegroundUsage("z", 599_999), ForegroundUsage("z", 600_001))
        assertEquals(listOf("z", "a"), rankApps(apps, usage, "home", Locale.US,
            opens = mapOf("a" to 1L)).map { it.packageName })
        assertEquals(listOf("a", "z"), rankApps(apps, usage, "home", Locale.US,
            opens = mapOf("a" to 2L)).map { it.packageName })
    }

    @Test fun appManualBalancesWorkWithoutUsageAndNeverFilterEligibleApps() {
        val apps = listOf(AppEntry("z", "Zulu", "Activity"), AppEntry("a", "Alpha", "Activity"))
        assertEquals(listOf("z", "a"), rankApps(apps, emptyList(), "home", Locale.US,
            adjustments = mapOf("z" to RankingAdjustment(10, 100))).map { it.packageName })
        assertEquals(listOf("z", "a"), rankApps(apps, emptyList(), "home", Locale.US,
            adjustments = mapOf("a" to RankingAdjustment(-10, 100))).map { it.packageName })
        assertEquals(Long.MAX_VALUE, addUsage(Long.MAX_VALUE - 1, 10))
        assertEquals(1L, addUsage(1, -10))
        assertEquals(2, rankApps(apps, listOf(ForegroundUsage("z", Long.MAX_VALUE)), "home",
            opens = mapOf("z" to Long.MAX_VALUE),
            adjustments = mapOf("z" to RankingAdjustment(Int.MAX_VALUE, 100))).size)
    }

    @Test fun contactStarBonusIsNumericAndEveryNonpositiveTotalIsExcluded() {
        val star = ContactEntry(1, "Star", true, "s")
        val frequent = ContactEntry(2, "Frequent", false, "f")
        val manual = ContactEntry(3, "Manual", false, "m")
        val scores = mapOf("f" to ContactScore(51, 100, 50),
            "m" to ContactScore(0, 100, 0, RankingAdjustment(10, 100)))
        assertEquals(listOf(frequent, star, manual), favoriteContacts(listOf(star, manual, frequent),
            Locale.US, scores = scores))
        listOf(-50, -60).forEach {
            assertTrue(favoriteContacts(listOf(star), scores = mapOf("s" to
                ContactScore(0, 100, 0, RankingAdjustment(it, 100)))).isEmpty())
        }
        assertEquals(listOf(star), favoriteContacts(listOf(star), scores = mapOf("s" to
            ContactScore(1, 100, 1, RankingAdjustment(-50, 100)))))
    }

    @Test fun optionSelectionIsDistinctFromNormalDetailsAndEditMembershipIsValidated() {
        val app = ContactMenu(options = ItemTarget.App("app"))
        assertTrue(app.expanded)
        assertEquals("app:app", app.selectedKey)
        val contact = ContactMenu(ContactExpansion(7), options = ItemTarget.Contact(7))
        assertEquals("contact:7", contact.selectedKey)
        assertFalse(ContactMenu().expanded)
        val entry = ContactEntry(7, "Person", lookupKey = "key")
        val details = ContactDetails(emptyList(), emptyList(), entry)
        assertTrue(details.contains(ContactAction.Edit(entry)))
        assertFalse(details.contains(ContactAction.Edit(entry.copy(lookupKey = "stale"))))
    }
}
