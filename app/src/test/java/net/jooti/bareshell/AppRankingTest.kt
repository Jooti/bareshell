package net.jooti.bareshell

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRankingTest {
    private val ownPackage = "net.jooti.bareshell"
    private val apps = listOf(
        app("foxtrot", "Foxtrot"),
        app("echo", "Echo"),
        app("delta", "Delta"),
        app("charlie", "Charlie"),
        app("bravo", "Bravo"),
        app("alpha", "Alpha"),
    )

    @Test
    fun highestForegroundTimeComesFirst() {
        val usage = listOf(
            ForegroundUsage("foxtrot", 1_800_000),
            ForegroundUsage("delta", 1_200_000),
            ForegroundUsage("bravo", 600_000),
        )
        assertEquals(
            listOf("foxtrot", "delta", "bravo", "alpha", "charlie", "echo"),
            rankedPackages(apps, usage),
        )
    }

    @Test
    fun allDailyRecordsForTheSamePackageAreSummed() {
        val usage = listOf(
            ForegroundUsage("alpha", 600_000),
            ForegroundUsage("bravo", 350_000),
            ForegroundUsage("bravo", 900_000),
        )
        assertEquals(listOf("bravo", "alpha"), rankedPackages(apps.takeLast(2), usage))
    }

    @Test
    fun absentHistoryOrDeniedAccessGivesAlphabeticalApps() {
        assertEquals(
            listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot"),
            rankedPackages(apps),
        )
    }

    @Test
    fun tiedUsageIsAlphabetical() {
        val usage = listOf(ForegroundUsage("echo", 600_000), ForegroundUsage("bravo", 600_000))
        assertEquals(listOf("bravo", "echo"), rankedPackages(apps, usage).take(2))
    }

    @Test
    fun ownPackageIsExcludedEvenWithTheHighestUsage() {
        val candidates = apps + app(ownPackage, "BareShell")
        val usage = listOf(ForegroundUsage(ownPackage, 1000))
        assertEquals(
            listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot"),
            rankedPackages(candidates, usage),
        )
    }

    @Test
    fun multipleLauncherActivitiesDoNotProduceDuplicateAppRows() {
        val duplicate = app("alpha", "Alpha").copy(activityName = "alpha.AnotherActivity")
        val ranked = rankApps(apps + duplicate, emptyList(), ownPackage, Locale.US)
        assertEquals(6, ranked.size)
        assertEquals(1, ranked.count { it.packageName == "alpha" })
        assertEquals("alpha.AnotherActivity", ranked.first().activityName)
        assertEquals(ranked, rankApps((apps + duplicate).reversed(), emptyList(), ownPackage, Locale.US))
    }

    @Test
    fun sameLabelOnDifferentPackagesUsesPackageTieBreakWithoutHidingEitherApp() {
        val candidates = listOf(app("z.package", "Same"), app("a.package", "Same"))
        assertEquals(listOf("a.package", "z.package"), rankedPackages(candidates))
    }

    @Test
    fun usageForNonLaunchableOrUninstalledPackagesCannotAddRows() {
        val usage = listOf(ForegroundUsage("not.a.candidate", 100000))
        assertEquals(listOf("alpha"), rankedPackages(listOf(app("alpha", "Alpha")), usage))
    }

    @Test
    fun emptyCandidatesStayEmptyEvenWhenUsageExists() {
        assertTrue(rankedPackages(emptyList(), listOf(ForegroundUsage("alpha", 10))).isEmpty())
    }

    @Test
    fun fewerThanTenEligibleAppsAreAllShown() {
        assertEquals(listOf("echo", "foxtrot"), rankedPackages(apps.take(2)))
    }

    @Test
    fun tenAppLimitAppliesAfterRankingAndAlphabeticalFallback() {
        val candidates = (1..12).map {
            app("package.$it", "App ${it.toString().padStart(2, '0')}")
        }
        assertEquals(candidates.take(10).map { it.packageName }, rankedPackages(candidates.reversed()))
        val usage = listOf(ForegroundUsage("package.12", 600_000))
        assertEquals(
            listOf("package.12") + candidates.take(9).map { it.packageName },
            rankedPackages(candidates, usage),
        )
        assertEquals(12, allApps(candidates, ownPackage, Locale.US).size)
    }

    @Test
    fun alphabeticalOrderingUsesTheChosenLocale() {
        val candidates = listOf(app("z", "Zulu"), app("a", "\u00c5ngstr\u00f6m"))
        assertEquals(
            listOf("a", "z"),
            rankApps(candidates, emptyList(), ownPackage, Locale.US).map { it.packageName },
        )
        assertEquals(
            listOf("z", "a"),
            rankApps(candidates, emptyList(), ownPackage, Locale.forLanguageTag("sv-SE")).map { it.packageName },
        )
    }

    @Test
    fun foregroundScoresAreNotTruncatedToInt() {
        val usage = listOf(
            ForegroundUsage("alpha", Int.MAX_VALUE.toLong() + 600_000),
            ForegroundUsage("bravo", Int.MAX_VALUE.toLong()),
        )
        assertEquals(listOf("alpha", "bravo"), rankedPackages(apps.takeLast(2), usage))
    }

    @Test
    fun opensAndCompleteTenMinuteUnitsAddTogether() {
        val candidates = listOf(app("frequent", "Frequent"), app("long", "Long"), app("other", "Other"))
        val usage = listOf(ForegroundUsage("frequent", 20), ForegroundUsage("long", 6_000_000), ForegroundUsage("other", 1))
        val opens = mapOf("frequent" to 100L, "long" to 1L, "other" to 5L)
        assertEquals(
            listOf("frequent", "long", "other"),
            rankApps(candidates, usage, ownPackage, Locale.US, opens = opens).map { it.packageName },
        )
    }

    @Test
    fun tiedNumericTotalsAreAlphabetical() {
        val candidates = listOf(app("a", "Alpha"), app("z", "Zulu"))
        val opens = mapOf("a" to 1L, "z" to 10L)
        val usage = listOf(ForegroundUsage("a", 5_400_000), ForegroundUsage("z", 1))
        val result = rankApps(candidates, usage, ownPackage, Locale.US, opens = opens)
        assertEquals(listOf("a", "z"), result.map { it.packageName })
    }

    @Test
    fun everyConfiguredLimitAppliesToRankingAndPermissionFallbackButNotAllApps() {
        val candidates = (1..60).map { app("package.$it", "App ${it.toString().padStart(2, '0')}") }
        listCountOptions.forEach { count ->
            assertEquals(count, rankApps(candidates, emptyList(), ownPackage, Locale.US, limit = count).size)
            assertEquals(count, rankApps(candidates, emptyList(), ownPackage, Locale.US,
                limit = count, opens = mapOf("package.60" to 1L)).size)
        }
        assertEquals(60, allApps(candidates, ownPackage, Locale.US).size)
    }

    @Test
    fun numericRankingIgnoresIneligiblePackagesAndDuplicateActivities() {
        val candidates = listOf(app("a", "Alpha"), app("b", "Bravo"), app("c", "Charlie"))
        val usage = listOf(ForegroundUsage("a", 1), ForegroundUsage("b", 100))
        val opens = mapOf("a" to 100L, "b" to 1L, "c" to 20L)
        val expected = rankApps(candidates, usage, ownPackage, Locale.US, opens = opens)
        assertEquals(listOf("a", "c", "b"), expected.map { it.packageName })
        val irrelevant = (1..25).map { ForegroundUsage("uninstalled.$it", 10000) }
        assertEquals(expected, rankApps(
            candidates + app(ownPackage, "Home") + candidates[0].copy(activityName = "a.ZActivity"),
            usage + irrelevant + ForegroundUsage(ownPackage, 10000),
            ownPackage, Locale.US, opens = opens + (ownPackage to 10000L),
        ))
    }

    @Test
    fun combinedRankingIsStableWhenOnlyTheDisplayLimitChanges() {
        val candidates = (1..60).map { app("package.$it", "App ${it.toString().padStart(2, '0')}") }
        val usage = candidates.mapIndexed { index, entry -> ForegroundUsage(entry.packageName, (index % 7).toLong()) }
        val opens = candidates.mapIndexed { index, entry -> entry.packageName to (index % 11).toLong() }.toMap()
        val large = rankApps(candidates, usage, ownPackage, Locale.US, 50, opens)
        listCountOptions.forEach { count ->
            assertEquals(large.take(count), rankApps(candidates.reversed(), usage, ownPackage, Locale.US, count, opens))
        }
    }

    private fun rankedPackages(
        candidates: List<AppEntry>,
        usage: List<ForegroundUsage> = emptyList(),
    ): List<String> = rankApps(candidates, usage, ownPackage, Locale.US).map { it.packageName }

    private fun app(packageName: String, label: String) =
        AppEntry(packageName, label, "$packageName.MainActivity")
}
