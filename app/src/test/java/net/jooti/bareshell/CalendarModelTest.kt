package net.jooti.bareshell

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarModelTest {
    @Test fun selectedSourcesAndWriteDefaultNeverSwitchAnExplicitChoice() {
        val first = DeviceCalendar(CalendarIdentity(1, "first", "LOCAL"), "First", true)
        val second = DeviceCalendar(CalendarIdentity(2, "second", "LOCAL"), "Second", true)
        val readOnly = DeviceCalendar(CalendarIdentity(3, "third", "LOCAL"), "Read only", false)
        val available = listOf(first, second, readOnly)
        val settings = LauncherSettings(eventCalendars = available.map { it.identity }, writeCalendar = second.identity)
        assertEquals(available, selectedCalendars(available, settings))
        assertEquals(second, defaultWriteCalendar(available, settings))
        assertNull(defaultWriteCalendar(listOf(first, readOnly), settings))
        assertNull(defaultWriteCalendar(available, settings.copy(writeCalendar = null)))
        assertEquals(first, defaultWriteCalendar(listOf(first, readOnly), settings.copy(writeCalendar = null)))
        assertTrue(selectedCalendars(available, LauncherSettings()).isEmpty())
        assertEquals(listOf(first), selectedCalendars(listOf(first), LauncherSettings()))
        assertTrue(selectedCalendars(available, LauncherSettings(eventCalendars = emptyList())).isEmpty())
        assertTrue(selectedCalendars(listOf(first), LauncherSettings(eventCalendars = listOf(second.identity))).isEmpty())
    }

    private val today = LocalDate.of(2026, 11, 21)
    private fun millis(day: LocalDate, hour: Int = 0, zone: ZoneId = ZoneOffset.UTC) =
        day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun event(day: LocalDate, id: Long = 1, allDay: Boolean = false) =
        CalendarOccurrence(id, "Event $id", millis(day, if (allDay) 0 else 12),
            millis(if (allDay) day.plusDays(1) else day, if (allDay) 0 else 13), allDay)

    @Test fun emptyTodayAndExactRangeAndSectionCap() {
        assertEquals(listOf(AgendaDay(today, emptyList())), calendarAgenda(emptyList(), today, ZoneOffset.UTC))
        val sparse = listOf(event(today.plusDays(14)), event(today.plusDays(15)))
        assertEquals(listOf(today, today.plusDays(14)), calendarAgenda(sparse, today, ZoneOffset.UTC).map { it.date })
        val everyDay = (1L..14L).map { event(today.plusDays(it), it) }
        assertEquals((0L..6L).map(today::plusDays), calendarAgenda(everyDay, today, ZoneOffset.UTC).map { it.date })
    }

    @Test fun overlappingEventsAndExclusiveEndsAndNoEventCap() {
        val ongoing = CalendarOccurrence(1, "Ongoing", millis(today.minusDays(1)), millis(today.plusDays(2)), false)
        val ended = ongoing.copy(eventId = 2, end = millis(today))
        val events = (3L..32L).map { event(today, it) }
        val agenda = calendarAgenda(events + ongoing + ended, today, ZoneOffset.UTC)
        assertEquals(31, agenda.first().events.size)
        assertEquals(listOf(today, today.plusDays(1)), agenda.map { it.date })
        assertFalse(agenda.flatMap { it.events }.any { it.eventId == 2L })
    }

    @Test fun allDayDatesAreUtcButTimedEventsFollowDeviceZone() {
        val zone = ZoneId.of("America/Los_Angeles")
        val allDay = event(today, 2, true)
        val timed = CalendarOccurrence(1, "Previous evening", millis(today, 1), millis(today, 2), false)
        assertEquals(listOf(allDay), calendarAgenda(listOf(timed, allDay), today, zone).first().events)
        val multi = allDay.copy(end = millis(today.plusDays(3)))
        assertEquals((0L..2L).map(today::plusDays), calendarAgenda(listOf(multi), today, zone).map { it.date })
    }

    @Test fun recurrenceInstancesRemainDistinctAndAllDaySortsFirst() {
        val timed = event(today)
        val second = timed.copy(begin = timed.begin + 7_200_000, end = timed.end + 7_200_000)
        val allDay = event(today, 2, true)
        assertEquals(listOf(allDay, timed, second),
            calendarAgenda(listOf(second, timed, allDay, timed), today, ZoneOffset.UTC).first().events)
    }

    @Test fun dstGapAndOverlapRequireExplicitResolution() {
        val zone = ZoneId.of("America/New_York")
        assertTrue(calendarLocalTime(LocalDate.of(2026, 3, 8), 2, 30, zone).isEmpty())
        assertEquals(2, calendarLocalTime(LocalDate.of(2026, 11, 1), 1, 30, zone).size)
        assertEquals(1, calendarLocalTime(today, 12, 0, zone).size)
    }

    private fun range(startHour: Int = 9, endHour: Int = 10) = CalendarEditorRange(
        today.atTime(startHour, 0), today.atTime(endHour, 0), false, ZoneOffset.UTC, ZoneOffset.UTC,
    )

    @Test fun creationStartDateResetsEndDayAndStartTimeDefaultsToAnHourLater() {
        val moved = range().copy(end = today.plusDays(5).atTime(12, 0))
            .startingOn(today.plusDays(2), creating = true)
        assertEquals(today.plusDays(2).atTime(9, 0), moved.start)
        assertEquals(today.plusDays(2).atTime(12, 0), moved.end)
        assertEquals(today.plusDays(2).atTime(16, 30), moved.startingAt(15 * 60 + 30, creating = true).end)
        val midnight = range().startingAt(23 * 60 + 30, creating = true)
        assertEquals(today.plusDays(1).atTime(0, 30), midnight.end)
        assertEquals(today.atTime(13, 0), midnight.startingAt(12 * 60, creating = true).end)
    }

    @Test fun existingStartsKeepValidEndsAndAdvanceOnlyInvalidEnds() {
        val initial = range().copy(end = today.plusDays(2).atTime(10, 0))
        assertEquals(initial.end, initial.startingOn(today.plusDays(1), creating = false).end)
        assertEquals(initial.end, initial.startingAt(11 * 60, creating = false).end)
        assertEquals(today.plusDays(3).atTime(10, 0),
            initial.startingOn(today.plusDays(3), creating = false).end)
        assertEquals(today.atTime(11, 0), range().startingAt(10 * 60, creating = false).end)
    }

    @Test fun invalidEndChoicesMoveForwardWithoutMovingStartOrClampingValidOvernightEnds() {
        val initial = range()
        val invalidDate = initial.endingOn(today.minusDays(1))
        assertEquals(initial, invalidDate)
        assertEquals(initial, initial.endingAt(8 * 60))
        assertEquals(initial, initial.endingAt(9 * 60))
        assertEquals(today.atTime(9, 1), initial.endingAt(9 * 60 + 1).end)
        val overnight = initial.endingOn(today.plusDays(1)).endingAt(2 * 60)
        assertEquals(today.plusDays(1).atTime(2, 0), overnight.end)
        assertEquals(initial.start, overnight.start)
        val movedBack = overnight.endingOn(today)
        assertEquals(initial, movedBack)
    }

    @Test fun allDayDateChangesAndTimedToggleKeepRangesOrdered() {
        val allDay = range(12, 9).copy(allDay = true, end = today.plusDays(2).atTime(9, 0))
        assertEquals(today.plusDays(1), allDay.startingOn(today.plusDays(1), creating = true).end.toLocalDate())
        assertEquals(today, allDay.endingOn(today.minusDays(1)).end.toLocalDate())
        assertEquals(today.plusDays(3), allDay.startingOn(today.plusDays(3), creating = false).end.toLocalDate())
        val sameDay = allDay.endingOn(today)
        assertEquals(today.atTime(13, 0), sameDay.copy(allDay = false).ordered().end)
        assertEquals(allDay, allDay.copy(allDay = false).ordered().copy(allDay = true))
    }

    @Test fun orderingAndOneHourDefaultsUseInstantsAcrossZonesAndDst() {
        val newYork = ZoneId.of("America/New_York")
        val spring = CalendarEditorRange(LocalDate.of(2026, 3, 8).atTime(1, 30),
            LocalDate.of(2026, 3, 8).atTime(1, 45), false, newYork, newYork)
        val next = spring.startingAt(90, creating = true)
        assertEquals(LocalDate.of(2026, 3, 8).atTime(3, 30), next.end)
        assertEquals(3_600, java.time.Duration.between(next.start.atZone(newYork), next.end.atZone(newYork)).seconds)
        val differentZones = range().copy(startZone = ZoneOffset.ofHours(-8), endZone = ZoneOffset.ofHours(3))
        val ordered = differentZones.ordered()
        assertEquals(today.atTime(21, 0), ordered.end)
        assertEquals(3_600, java.time.Duration.between(ordered.start.atZone(ordered.startZone),
            ordered.end.atZone(ordered.endZone)).seconds)
        assertEquals(ordered, ordered.endingAt(20 * 60))
    }

    @Test fun eventValidationRejectsBlankTitlesAndBadRangesAndAllDayBoundaries() {
        val event = CalendarEvent(1, 2, "Title", null, null, millis(today), millis(today.plusDays(1)),
            true, "UTC", null, null, null, null)
        assertTrue(validCalendarEvent(event))
        assertFalse(validCalendarEvent(event.copy(title = " ")))
        assertFalse(validCalendarEvent(event.copy(end = event.start)))
        assertFalse(validCalendarEvent(event.copy(start = event.start + 1)))
        assertTrue(validCalendarEvent(event.copy(allDay = false, start = event.start + 1)))
    }
}
