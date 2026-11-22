package net.jooti.bareshell

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

internal data class CalendarIdentity(val id: Long, val account: String, val accountType: String)
internal data class DeviceCalendar(val identity: CalendarIdentity, val name: String, val writable: Boolean)

internal fun selectedCalendars(available: List<DeviceCalendar>, settings: LauncherSettings): List<DeviceCalendar> =
    settings.eventCalendars?.let { selected -> available.filter { it.identity in selected } }
        ?: available.singleOrNull()?.let { listOf(it) }.orEmpty()

internal fun defaultWriteCalendar(selected: List<DeviceCalendar>, settings: LauncherSettings): DeviceCalendar? =
    if (settings.writeCalendar != null) selected.firstOrNull { it.identity == settings.writeCalendar && it.writable }
    else selected.filter { it.writable }.singleOrNull()

internal data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val note: String?,
    val location: String?,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val timeZone: String,
    val endTimeZone: String?,
    val recurrence: String?,
    val recurrenceDates: String?,
    val originalId: Long?,
) {
    val externalEdit: Boolean get() = !recurrence.isNullOrEmpty() || !recurrenceDates.isNullOrEmpty() || originalId != null
}

internal data class CalendarOccurrence(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)
internal data class AgendaDay(val date: LocalDate, val events: List<CalendarOccurrence>)

internal fun calendarAgenda(events: List<CalendarOccurrence>, today: LocalDate, zone: ZoneId): List<AgendaDay> {
    val days = (0L..14L).map { offset ->
        val date = today.plusDays(offset)
        val matching = events.filter { event ->
            val eventZone = if (event.allDay) ZoneOffset.UTC else zone
            val start = date.atStartOfDay(eventZone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(eventZone).toInstant().toEpochMilli()
            event.begin < end && (event.end > start || event.end == event.begin && event.begin >= start)
        }.distinctBy { Triple(it.eventId, it.begin, it.end) }
            .sortedWith(compareBy<CalendarOccurrence> { !it.allDay }.thenBy { it.begin }.thenBy { it.title }.thenBy { it.eventId })
        AgendaDay(date, matching)
    }
    return days.filter { it.date == today || it.events.isNotEmpty() }.take(7)
}

internal fun calendarLocalTime(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): List<Instant> {
    val local = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
    return zone.rules.getValidOffsets(local).map { local.toInstant(it) }
}

internal fun validCalendarEvent(event: CalendarEvent): Boolean =
    event.title.isNotBlank() && event.end > event.start &&
        (!event.allDay || (event.start % 86_400_000L == 0L && event.end % 86_400_000L == 0L))
