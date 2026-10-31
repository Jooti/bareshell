package net.jooti.bareshell

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCatalogTest {
    @Test
    fun allContactsPutStarredFirstAndSortEachGroupAlphabetically() {
        val contacts = listOf(
            ContactEntry(1, "Bob"),
            ContactEntry(2, "Zoe", true),
            ContactEntry(3, "Alice", true),
            ContactEntry(4, "Alex"),
        )
        assertEquals(listOf(3L, 2L, 4L, 1L), orderContacts(contacts, true, Locale.US).map { it.id })
    }

    @Test
    fun searchOrderingRemainsAlphabeticalRegardlessOfStars() {
        val contacts = listOf(ContactEntry(1, "Zoe", true), ContactEntry(2, "Alice"))
        assertEquals(listOf(2L, 1L), orderContacts(contacts, locale = Locale.US).map { it.id })
    }

    @Test
    fun favoritesAreAlphabeticalAndCappedAtTenWhileAllContactsAreUncapped() {
        val contacts = (1..12).map { ContactEntry(it.toLong(), "Fav ${it.toString().padStart(2, '0')}", true) }
        assertEquals(contacts.take(10), favoriteContacts(contacts.reversed(), Locale.US))
        assertEquals(contacts, orderContacts(contacts.reversed(), locale = Locale.US))
    }

    @Test
    fun fewerThanTenFavoritesAreNotFilledWithUnstarredContacts() {
        val contacts = listOf(ContactEntry(1, "Alice"), ContactEntry(2, "Zoe", true))
        assertEquals(listOf(contacts[1]), favoriteContacts(contacts, Locale.US))
        assertTrue(favoriteContacts(listOf(contacts[0]), Locale.US).isEmpty())
    }

    @Test
    fun closeContactsPutAlphabeticalStarsAheadOfScoredPeopleWithinTheTotalCap() {
        val contacts = listOf(ContactEntry(1, "Zoe", true, "z"), ContactEntry(2, "Alice", true, "a"),
            ContactEntry(3, "Recent", false, "r"), ContactEntry(4, "Frequent", false, "f"),
            ContactEntry(5, "Expired", false, "e"), ContactEntry(6, "Unknown", false, "u"))
        val scores = mapOf("r" to ContactScore(1, 1, 100), "f" to ContactScore(4, 1, 20),
            "e" to ContactScore(0, 1, 50))
        assertEquals(listOf(2L, 1L, 4L, 3L), favoriteContacts(contacts, Locale.US, 5, scores).map { it.id })
        assertEquals(listOf(2L, 5L, 4L, 3L, 6L, 1L), orderContacts(contacts, locale = Locale.US).map { it.id })
    }

    @Test
    fun starsUseAllSlotsBeforeScoredPeopleAndEqualScoresUseRecencyThenName() {
        val stars = (1..6).map { ContactEntry(it.toLong(), "Star $it", true, "$it") }
        val people = listOf(ContactEntry(7, "Zoe", false, "z"), ContactEntry(8, "Alice", false, "a"),
            ContactEntry(9, "Bob", false, "b"))
        val scores = mapOf("z" to ContactScore(4, 1, 100), "a" to ContactScore(4, 1, 100),
            "b" to ContactScore(4, 1, 200))
        assertEquals(stars.take(5), favoriteContacts(stars + people, Locale.US, 5, scores))
        assertEquals(listOf(9L, 8L, 7L), favoriteContacts(people, Locale.US, 10, scores).map { it.id })
    }

    @Test
    fun everyConfiguredCountCapsTheCombinedListRatherThanEachGroup() {
        val stars = (1..12).map { ContactEntry(it.toLong(), "Star ${it.toString().padStart(2, '0')}", true, "s$it") }
        val people = (1..60).map { ContactEntry((it + 12).toLong(), "Person $it", false, "p$it") }
        val scores = people.associate { it.lookupKey to ContactScore(it.id.toInt(), 100, it.id) }
        val expected = people.filter { it.id >= 50 }.reversed() + stars + people.filter { it.id < 50 }.reversed()
        listCountOptions.forEach { count ->
            val result = favoriteContacts((stars + people).reversed(), Locale.US, count, scores)
            assertEquals(count, result.size)
            assertEquals(expected.take(count), result)
        }
        assertEquals(72, orderContacts(stars + people, true, Locale.US).size)
    }

    @Test
    fun duplicateNamesStayDistinctButDuplicateIdentitiesAreRemoved() {
        val contacts = listOf(ContactEntry(2, "Alex"), ContactEntry(1, "Alex"), ContactEntry(2, "Alex"))
        assertEquals(listOf(1L, 2L), orderContacts(contacts, true, Locale.US).map { it.id })
    }

    @Test
    fun changingSystemStarsChangesOrdering() {
        val contacts = listOf(ContactEntry(1, "Alice"), ContactEntry(2, "Zoe"))
        assertEquals(listOf(1L, 2L), orderContacts(contacts, true, Locale.US).map { it.id })
        assertEquals(listOf(2L, 1L), orderContacts(listOf(contacts[0], contacts[1].copy(starred = true)), true, Locale.US).map { it.id })
    }

    @Test
    fun alphabetIndexHasFavShortcutThenOnlyUnstarredLetterPositions() {
        val contacts = listOf(
            ContactEntry(1, "Xavier", true),
            ContactEntry(2, "Bob"),
            ContactEntry(3, "Brian"),
            ContactEntry(4, "Xena"),
        )
        assertEquals(
            listOf(ContactSection("Fav", 0), ContactSection("B", 1), ContactSection("X", 3)),
            contactSections(contacts, "Fav") { it.first().toString() },
        )
    }

    @Test
    fun noFavoritesMeansNoFavShortcutAndNoDuplicateLetterEntries() {
        val contacts = listOf(ContactEntry(1, "Alice"), ContactEntry(2, "Anna"), ContactEntry(3, "Zoe"))
        assertEquals(
            listOf(ContactSection("A", 0), ContactSection("Z", 2)),
            contactSections(contacts, "Fav") { it.first().toString() },
        )
    }

    @Test
    fun onlyFavoritesHaveJustTheFavShortcutAndEmptyContactsHaveNoIndex() {
        assertEquals(
            listOf(ContactSection("Fav", 0)),
            contactSections(listOf(ContactEntry(1, "Bob", true)), "Fav") { "B" },
        )
        assertTrue(contactSections(emptyList(), "Fav") { "A" }.isEmpty())
    }

    @Test
    fun platformBucketsCanCombineAccentsAndNonalphabeticNames() {
        val contacts = listOf(
            ContactEntry(1, "123"),
            ContactEntry(2, "456"),
            ContactEntry(3, "Alice"),
            ContactEntry(4, "\u00c1lvaro"),
            ContactEntry(5, "Bob"),
        )
        assertEquals(
            listOf(ContactSection("#", 0), ContactSection("A", 2), ContactSection("B", 4)),
            contactSections(contacts, "Fav") {
                when {
                    it.first().isDigit() -> "#"
                    it.startsWith("\u00c1") -> "A"
                    else -> it.first().toString()
                }
            },
        )
    }
}
