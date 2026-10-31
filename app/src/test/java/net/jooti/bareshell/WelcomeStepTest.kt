package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WelcomeStepTest {
    @Test fun leftThenRightReturnsToAppsAndShowsTheMap() {
        assertEquals(Page.FAV_APPS, WelcomeStep.APPS.page)
        val contacts = WelcomeStep.APPS.navigate(Gesture.LEFT)
        assertEquals(WelcomeStep.CONTACTS, contacts)
        assertEquals(Page.FAV_CONTACTS, contacts.page)
        val map = contacts.navigate(Gesture.RIGHT)
        assertEquals(WelcomeStep.MAP, map)
        assertEquals(Page.FAV_APPS, map.page)
        assertNull(map.gesture)
    }

    @Test fun otherGesturesNeverSkipAStep() {
        WelcomeStep.entries.forEach { step ->
            Gesture.entries.filter { it != step.gesture }.forEach {
                assertEquals(step, step.navigate(it))
            }
        }
    }

    @Test fun backRevisitsThePreviousStep() {
        assertEquals(WelcomeStep.APPS, WelcomeStep.APPS.back())
        assertEquals(WelcomeStep.APPS, WelcomeStep.CONTACTS.back())
        assertEquals(WelcomeStep.CONTACTS, WelcomeStep.MAP.back())
    }

    @Test fun instructionsFollowTheRealContactShortcut() {
        val contacts = defaultConfiguration.bindings.getValue(Page.FAV_CONTACTS)
        assertEquals(contacts, WelcomeStep.APPS.gesture)
        assertEquals(contacts.reverse(), WelcomeStep.CONTACTS.gesture)
    }
}
