package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherConfigurationTest {
    private val swipes = Gesture.entries.toSet() - Gesture.SHAKE
    private fun configuration(home: Page, count: Int = 4) =
        LauncherConfiguration(home, home.remaining().take(count).zip(Gesture.entries).toMap())

    private fun wizard(home: Page, count: Int = 4) =
        WizardProgress(home, home.remaining().take(count)).start(Gesture.entries.toSet())

    private fun legacy(home: Page): Map<String, Any> = buildMap {
        put("version", 1)
        put("home", home.name)
        home.remaining().filter { it != Page.TASKS }.zip(Gesture.entries.take(4)).forEach { (page, gesture) ->
            put("gesture.${page.name}", gesture.name)
        }
    }

    @Test fun allHomesAndDestinationCountsRoundTrip() {
        Page.entries.forEach { home ->
            (0..5).forEach { count ->
                val config = configuration(home, count)
                assertEquals(ConfigurationRead.Ready(config), readConfiguration(config.storedValues()))
                assertTrue(config.enables(home))
                home.remaining().forEach { assertEquals(it in config.bindings, config.enables(it)) }
            }
        }
    }

    @Test fun legacySetupsKeepHomeAndAllOldBindingsWithoutEnablingTasks() {
        (Page.entries - Page.TASKS).forEach { home ->
            val values = legacy(home)
            val result = readConfiguration(values, swipes) as ConfigurationRead.Ready
            assertEquals(home, result.configuration.home)
            assertEquals(home.remaining().filter { it != Page.TASKS }, result.configuration.bindings.keys.toList())
            assertFalse(result.configuration.enables(Page.TASKS))
            assertEquals(ConfigurationRead.Ready(result.configuration), readConfiguration(result.configuration.storedValues()))
            result.configuration.bindings.forEach { (page, gesture) ->
                assertEquals(values["gesture.${page.name}"], gesture.name)
            }
        }
    }

    @Test fun legacyTasksAndIncompleteBindingsAreRejected() {
        assertEquals(ConfigurationRead.Invalid, readConfiguration(legacy(Page.FAV_APPS) + ("home" to "TASKS")))
        assertEquals(ConfigurationRead.Invalid, readConfiguration(legacy(Page.FAV_APPS) - "gesture.SEARCH"))
        assertEquals(ConfigurationRead.Invalid, readConfiguration(legacy(Page.FAV_APPS) + ("gesture.TASKS" to "SHAKE")))
    }

    @Test fun missingSettingsStartTheWizard() {
        assertEquals(ConfigurationRead.Missing, readConfiguration(emptyMap<String, Any>()))
    }

    @Test fun malformedSettingsNeverSilentlyDefault() {
        val values = configuration(Page.FAV_APPS).storedValues()
        listOf(
            values + ("version" to 3), values + ("version" to "2"), values + ("home" to 9),
            values + ("home" to "UNKNOWN"), values + ("gesture.SEARCH" to "UNKNOWN"),
            values + ("gesture.SEARCH" to "LEFT"), values + ("gesture.FAV_APPS" to "SHAKE"),
            values - "gesture.SEARCH", values - "destinations", values + ("destinations" to 4),
            values + ("destinations" to "UNKNOWN"), values + ("destinations" to "SEARCH,SEARCH"),
            values + ("destinations" to "FAV_APPS"), values + ("destinations" to "SEARCH,ALL_APPS"),
        ).forEach { assertEquals(it.toString(), ConfigurationRead.Invalid, readConfiguration(it)) }
    }

    @Test fun unavailableShakeIsRejectedButFourSwipesWork() {
        assertEquals(ConfigurationRead.Invalid, readConfiguration(configuration(Page.TASKS, 5).storedValues(), swipes))
        assertTrue(readConfiguration(configuration(Page.TASKS).storedValues(), swipes) is ConfigurationRead.Ready)
        assertTrue(readConfiguration(configuration(Page.TASKS, 0).storedValues(), swipes) is ConfigurationRead.Ready)
    }

    @Test fun eachGestureAndReverseNavigateWhileOtherDestinationGesturesDoNot() {
        Page.entries.forEach { home ->
            val config = configuration(home, 5)
            config.bindings.forEach { (page, gesture) ->
                assertEquals(page, config.navigate(home, gesture))
                assertEquals(home, config.navigate(page, gesture.reverse()))
                (Gesture.entries - gesture.reverse()).forEach { other ->
                    assertEquals(page, config.navigate(page, other))
                }
            }
        }
    }

    @Test fun unusedGesturesAndDisabledPagesDoNotCrashOrNavigate() {
        val config = configuration(Page.FAV_APPS, 1)
        assertEquals(Page.FAV_APPS, config.navigate(Page.FAV_APPS, Gesture.SHAKE))
        assertEquals(Page.TASKS, config.navigate(Page.TASKS, Gesture.RIGHT))
        assertEquals(Page.FAV_APPS, config.navigate(Page.FAV_CONTACTS, Gesture.RIGHT))
        Gesture.entries.forEach { assertEquals(it, it.reverse().reverse()) }
    }

    @Test fun selectionTogglesInStablePageOrder() {
        var progress = WizardProgress(Page.FAV_APPS)
        progress = progress.toggle(Page.TASKS, 4).toggle(Page.ALL_APPS, 4)
        assertEquals(listOf(Page.ALL_APPS, Page.TASKS), progress.destinations)
        assertEquals(listOf(Page.TASKS), progress.toggle(Page.ALL_APPS, 4).destinations)
        assertFalse(progress.complete)
        assertEquals(null, progress.next)
    }

    @Test fun allSelectedPagesAreAssignedBeforeCompletion() {
        Page.entries.forEach { home ->
            (0..5).forEach { count ->
                var progress = wizard(home, count)
                home.remaining().take(count).forEachIndexed { index, page ->
                    assertEquals(page, progress.next)
                    assertFalse(progress.complete)
                    progress = progress.assign(Gesture.entries[index])
                }
                assertTrue(progress.complete)
                assertEquals(configuration(home, count), progress.configuration())
            }
        }
    }

    @Test fun homeOnlySetupCompletesAfterExplicitContinue() {
        val choosing = WizardProgress(Page.TASKS)
        assertFalse(choosing.complete)
        val complete = choosing.start(swipes)
        assertTrue(complete.complete)
        assertEquals(LauncherConfiguration(Page.TASKS, emptyMap()), complete.configuration())
        assertEquals(choosing, complete.back())
    }

    @Test fun backTraversesAssignmentsSelectionThenHome() {
        var progress = wizard(Page.FAV_APPS).assign(Gesture.LEFT).assign(Gesture.RIGHT)
        progress = progress.back()
        assertEquals(Page.ALL_APPS, progress.next)
        assertFalse(Gesture.RIGHT in progress.bindings.values)
        progress = progress.back().back()
        assertFalse(progress.assigning)
        assertEquals(Page.FAV_APPS.remaining().take(4), progress.destinations)
        assertEquals(WizardProgress(), progress.back())
        assertEquals(WizardProgress(), WizardProgress().back())
    }

    @Test fun stepTotalsFollowSelectedPageCountWithoutAReviewStep() {
        assertEquals(1, WizardProgress().stepNumber)
        (0..5).forEach { count ->
            var progress = wizard(Page.TASKS, count)
            assertEquals(count + 2, progress.stepCount)
            assertEquals(2, progress.back().stepNumber)
            repeat(count) { index ->
                assertEquals(index + 3, progress.stepNumber)
                progress = progress.assign(Gesture.entries[index])
            }
            assertEquals(progress.stepCount, progress.stepNumber)
        }
    }

    @Test fun demonstrationsUseOnlyAvailableUnassignedGestures() {
        val progress = wizard(Page.TASKS).assign(Gesture.LEFT).assign(Gesture.SHAKE)
        assertEquals(Gesture.entries - setOf(Gesture.LEFT, Gesture.SHAKE), progress.unusedGestures(Gesture.entries.toSet()))
        assertEquals(Gesture.entries - Gesture.SHAKE, WizardProgress().unusedGestures(swipes))
    }

    @Test fun defaultsExcludeFullListsButAllowAddingThemForEveryHome() {
        Page.entries.forEach { home ->
            val progress = WizardProgress.choosing(home, swipes)
            val expected = home.remaining().filter { it != Page.ALL_APPS && it != Page.ALL_CONTACTS }
            assertEquals(expected, progress.destinations)
            assertFalse(progress.assigning)
            val removed = progress.toggle(expected.first(), swipes.size)
            assertEquals(removed, removed.start(swipes).back())
            assertEquals(expected.take(2),
                WizardProgress.choosing(home, Gesture.entries.take(2).toSet()).destinations)
            assertTrue(WizardProgress.choosing(home, emptySet()).destinations.isEmpty())
            var included = progress
            home.remaining().filter { it !in expected }.forEach { page ->
                included = included.toggle(page, swipes.size)
            }
            assertEquals(home.remaining(), included.destinations)
            assertEquals(included, included.start(swipes).back())
        }
    }

    @Test fun twoFingerBindingsRoundTripAndReverseWithSameFingerCount() {
        val config = LauncherConfiguration(Page.FAV_APPS, listOf(Page.ALL_APPS, Page.ALL_CONTACTS, Page.SEARCH, Page.TASKS)
            .zip(listOf(Gesture.TWO_LEFT, Gesture.TWO_RIGHT, Gesture.TWO_UP, Gesture.TWO_DOWN)).toMap())
        assertEquals(ConfigurationRead.Ready(config), readConfiguration(config.storedValues(), swipes))
        config.bindings.forEach { (page, gesture) ->
            assertEquals(page, config.navigate(config.home, gesture))
            assertEquals(config.home, config.navigate(page, gesture.reverse()))
        }
        assertEquals(Page.TASKS, config.navigate(Page.TASKS, Gesture.UP))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotOverselectCapacity() {
        WizardProgress(Page.TASKS, Page.TASKS.remaining().take(4)).toggle(Page.SEARCH, 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotStartOverCapacity() { wizard(Page.TASKS, 5).copy(assigning = false).start(Gesture.entries.take(4).toSet()) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotSelectHome() { WizardProgress(Page.TASKS).toggle(Page.TASKS, 4) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotDuplicateGesture() { wizard(Page.TASKS).assign(Gesture.LEFT).assign(Gesture.LEFT) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotBindHome() { LauncherConfiguration(Page.TASKS, mapOf(Page.TASKS to Gesture.LEFT)) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotRestoreOutOfOrderBindings() {
        WizardProgress(Page.FAV_APPS, listOf(Page.ALL_APPS, Page.SEARCH), true, mapOf(Page.SEARCH to Gesture.LEFT))
    }
}
