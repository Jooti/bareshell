package net.jooti.bareshell

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSearchTest {
    private val apps = listOf(
        AppEntry("calendar", "Calendar", "calendar.Main"),
        AppEntry("camera", "Camera", "camera.Main"),
    )
    private val contacts = listOf(ContactEntry(1, "Cam Avery"), ContactEntry(2, "Cam Avery"))

    @Test
    fun matchesAppAndContactNamesIgnoringCaseAndOuterWhitespace() {
        val result = searchNames("  cAm  ", apps, contacts)
        assertEquals(listOf(apps[1]), result.apps)
        assertEquals(contacts, result.contacts)
    }

    @Test
    fun singleCharacterExactAppNameComesBeforeSubstringMatches() {
        val candidates = listOf(
            AppEntry("box", "Box", "box.Main"),
            AppEntry("firefox", "Firefox", "firefox.Main"),
            AppEntry("x", "X", "x.Main"),
            AppEntry("xbox", "Xbox", "xbox.Main"),
        )
        listOf("X", "x", "  x  ").forEach { query ->
            assertEquals(
                listOf(candidates[2], candidates[0], candidates[1], candidates[3]),
                searchNames(query, candidates, emptyList()).apps,
            )
        }
    }

    @Test
    fun longerExactAppNamesKeepTheirOrderAheadOfPartialMatches() {
        val candidates = listOf(
            AppEntry("camera.plus", "Camera Plus", "camera.plus.Main"),
            AppEntry("camera", "Camera", "camera.Main"),
            AppEntry("my.camera", "My Camera", "my.camera.Main"),
            AppEntry("other.camera", "cAMERA", "other.camera.Main"),
        )
        assertEquals(
            listOf(candidates[1], candidates[3], candidates[0], candidates[2]),
            searchNames("  CAMERA  ", candidates, emptyList()).apps,
        )
    }

    @Test
    fun partialOnlyMatchesKeepTheirIncomingOrder() {
        val candidates = apps.reversed()
        assertEquals(candidates, searchNames("ca", candidates, emptyList()).apps)
    }

    @Test
    fun exactAppPriorityDoesNotReorderContacts() {
        val candidates = listOf(
            AppEntry("box", "Box", "box.Main"),
            AppEntry("x", "X", "x.Main"),
        )
        val people = listOf(ContactEntry(1, "Max"), ContactEntry(2, "X"))
        val result = searchNames("x", candidates, people)
        assertEquals(candidates.reversed(), result.apps)
        assertEquals(people, result.contacts)
    }

    @Test
    fun emptyOrWhitespaceQueriesHaveNoResults() {
        listOf("", "  ").forEach { query ->
            assertEquals(SearchMatches(emptyList(), emptyList()), searchNames(query, apps, contacts))
        }
    }

    @Test
    fun noMatchHasNoResults() {
        assertEquals(SearchMatches(emptyList(), emptyList()), searchNames("unmatched", apps, contacts))
    }

    @Test
    fun appSearchWorksWithoutContactsAndDoesNotSearchPackageNames() {
        assertEquals(listOf(apps[0]), searchNames("endar", apps, emptyList()).apps)
        assertTrue(searchNames("Main", apps, emptyList()).apps.isEmpty())
    }

    @Test
    fun sameNamedContactsKeepTheirDistinctIdentities() {
        assertEquals(listOf(1L, 2L), searchNames("Avery", emptyList(), contacts).contacts.map { it.id })
    }

    @Test
    fun allAppsIsNotCappedAndKeepsSharedEligibilityAndOrdering() {
        val candidates = (1..12).map { index ->
            AppEntry("p$index", "App ${index.toString().padStart(2, '0')}", "p$index.Main")
        }
        val duplicate = candidates[0].copy(activityName = "p1.Alternate")
        val own = AppEntry("own", "Own", "own.Main")
        val catalog = allApps(candidates.reversed() + duplicate + own, "own", Locale.US)
        assertEquals(12, catalog.size)
        assertEquals(candidates.map { it.packageName }, catalog.map { it.packageName })
        assertEquals(candidates.last(), searchNames("12", catalog, emptyList()).apps.single())
    }

    @Test
    fun emptyContactsAreDifferentFromPermissionDenialAndQueryFailure() {
        val empty = ContactsResult.Loaded(emptyList())
        assertNotEquals(empty, ContactsResult.Denied)
        assertNotEquals(empty, ContactsResult.Unavailable)
        assertNotEquals(ContactsResult.Denied, ContactsResult.Unavailable)
    }
}
