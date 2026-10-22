package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactActionsTest {
    @Test
    fun superPrimaryThenPrimaryThenStableFirstNumberIsTheDefault() {
        val phones = listOf(
            ContactPhone(9, "999", "Work"),
            ContactPhone(5, "555", "Home", primary = true),
            ContactPhone(8, "888", "Mobile", superPrimary = true),
            ContactPhone(1, "111", "Other"),
        )
        assertEquals(listOf(8L, 5L, 1L, 9L), preferredPhones(phones).map { it.id })
    }

    @Test
    fun noDefaultFallsBackDeterministicallyWithoutChangingPhoneFlags() {
        val phones = listOf(ContactPhone(4, "444", "Custom"), ContactPhone(2, "222", "Work"))
        assertEquals(phones[1], preferredPhones(phones).first())
        assertFalse(preferredPhones(phones).any { it.primary || it.superPrimary })
        assertTrue(preferredPhones(emptyList()).isEmpty())
    }

    @Test
    fun equivalentFormattingIsDeduplicatedAndPreferredLabelIsRetained() {
        val ordinary = ContactPhone(1, "+1 (555) 123-4567", "Home")
        val preferred = ContactPhone(8, "+15551234567", "Mobile", superPrimary = true)
        assertEquals(listOf(preferred), preferredPhones(listOf(ordinary, preferred)))
    }

    @Test
    fun extensionsPausesAndWaitSequencesAreNotMergedIntoTheBaseNumber() {
        val phones = listOf("555", "555,1", "555;1", "555,2", "555 ext 1", "555 ext 2")
            .mapIndexed { index, number -> ContactPhone(index.toLong(), number, "Phone") }
        assertEquals(phones, preferredPhones(phones))
    }

    @Test
    fun tappingTheSameContactClosesAndAnotherContactResetsTheSubmenu() {
        assertEquals(ContactExpansion(1), ContactExpansion().toggle(1))
        assertEquals(ContactExpansion(), ContactExpansion(1, true).toggle(1))
        assertEquals(ContactExpansion(2), ContactExpansion(1, true).toggle(2))
    }

    @Test
    fun backClosesTheSubmenuThenTheContact() {
        assertEquals(ContactExpansion(1), ContactExpansion(1, true).back())
        assertEquals(ContactExpansion(), ContactExpansion(1).back())
        assertEquals(ContactExpansion(), ContactExpansion().back())
    }

    @Test
    fun focusDimmingIncludesOtherContactsAndAppResultsButNotTheSelectedContact() {
        assertFalse(ContactExpansion().dims(1))
        assertFalse(ContactExpansion().dims(null))
        assertFalse(ContactExpansion(1).dims(1))
        assertTrue(ContactExpansion(1).dims(2))
        assertTrue(ContactExpansion(1).dims(null))
    }

    @Test
    fun staleNumbersAndMessagingTargetsDoNotBelongToRefreshedDetails() {
        val phone = ContactPhone(1, "555", "Home")
        val target = VerifiedContactAction(2, MessengerAction.TELEGRAM_MESSAGE, "Activity", "Target")
        val details = ContactDetails(listOf(phone), listOf(target))
        assertTrue(details.contains(ContactAction.Dial(phone)))
        assertTrue(details.contains(ContactAction.Sms(phone)))
        assertTrue(details.contains(ContactAction.Messenger(target)))
        assertFalse(details.contains(ContactAction.Dial(phone.copy(number = "777"))))
        assertFalse(details.contains(ContactAction.Messenger(target.copy(dataId = 3))))
        val contact = ContactEntry(1, "Person", lookupKey = "lookup")
        assertTrue(details.copy(contact = contact).contains(ContactAction.View(contact)))
        assertFalse(details.copy(contact = contact).contains(ContactAction.View(contact.copy(id = 2))))
    }

    @Test
    fun multipleVerifiedTargetsRemainDistinctAndOnlyRequestedAppsAreSupported() {
        val targets = listOf(
            VerifiedContactAction(1, MessengerAction.WHATSAPP_MESSAGE, "Activity", "One"),
            VerifiedContactAction(2, MessengerAction.WHATSAPP_MESSAGE, "Activity", "Two"),
        )
        assertEquals(2, ContactDetails(emptyList(), targets).messaging.size)
        assertEquals(setOf("org.telegram.messenger", "com.whatsapp"), MessengerAction.entries.map { it.packageName }.toSet())
        assertEquals(3, MessengerAction.entries.size)
    }

    @Test
    fun appIndexUsesOnlyPresentBucketsAndHasNoFavoritesShortcut() {
        val labels = listOf("123", "Alpha", "Another", "Beta", "Xray")
        assertEquals(
            listOf(ContactSection("#", 0), ContactSection("A", 1), ContactSection("B", 3), ContactSection("X", 4)),
            alphabetSections(labels) { if (it.first().isDigit()) "#" else it.first().toString() },
        )
        assertTrue(alphabetSections(emptyList()) { "A" }.isEmpty())
    }
}
