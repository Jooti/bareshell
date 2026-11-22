package net.jooti.bareshell

import android.app.Instrumentation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.os.SystemClock
import android.provider.CalendarContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

internal class CalendarInstrumentationChecks(private val instrumentation: Instrumentation) {
    private val context = instrumentation.targetContext

    fun dates() {
        val english = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.US)
        })
        val gregorian = CalendarDates(english, "gregorian")
        val sample = LocalDate.of(2026, 11, 22)
        check(gregorian.heading(sample, LauncherSettings()) == "Sun 11/22")
        check(gregorian.heading(sample, LauncherSettings(weekday = CalendarWeekday.FULL)) == "Sunday 11/22")
        check(gregorian.heading(sample, LauncherSettings(dateFormat = CalendarDateFormat.DAY_MONTH)) == "Sun 22/11")
        check(gregorian.heading(sample, LauncherSettings(dateFormat = CalendarDateFormat.MONTH_DAY_YEAR)) == "Sun 11/22/2026")
        check(gregorian.heading(sample, LauncherSettings(dateFormat = CalendarDateFormat.DAY_MONTH_YEAR)) == "Sun 22/11/2026")
        check(gregorian.heading(sample, LauncherSettings(dateFormat = CalendarDateFormat.YEAR_MONTH_DAY)) == "Sun 2026-11-22")
        check(CalendarDates(english, "persian").heading(LocalDate.of(2026, 3, 21), LauncherSettings()) == "Sat 01/01")
        listOf(CalendarDateFormat.SHORT, CalendarDateFormat.MEDIUM, CalendarDateFormat.LONG).forEach {
            check(gregorian.heading(sample, LauncherSettings(dateFormat = it)).startsWith("Sun "))
        }
        val locale = ULocale.forLocale(context.resources.configuration.locales[0])
        val types = CalendarDates.available(locale)
        check(types.containsAll(listOf("gregorian", "persian", "chinese", "islamic", "islamic-civil", "islamic-umalqura"))) { types }
        val persian = CalendarDates(context, "persian")
        val newYear = LocalDate.of(2026, 3, 21)
        check(persian.calendar(newYear).get(Calendar.YEAR) == 1405)
        check(persian.calendar(newYear).get(Calendar.MONTH) == 0)
        check(persian.calendar(newYear).get(Calendar.DAY_OF_MONTH) == 1)
        val chinese = CalendarDates(context, "chinese")
        val leap = LocalDate.of(2023, 3, 22)
        check(chinese.calendar(leap).get(Calendar.IS_LEAP_MONTH) == 1)
        check(chinese.monthDays(leap).first() == leap)
        types.forEach { type ->
            val dates = CalendarDates(context, type)
            check(dates.supported)
            listOf(newYear, leap, LocalDate.of(2024, 2, 29)).forEach { day ->
                check(dates.format(day).isNotBlank())
                val days = dates.monthDays(day)
                check(day in days)
                check(days.zipWithNext().all { (a, b) -> a.plusDays(1) == b })
                val first = days.first()
                val next = dates.move(first, Calendar.MONTH, 1)
                val previous = dates.move(next, Calendar.MONTH, -1)
                check(previous == first) { "$type: $first -> $next -> $previous" }
            }
        }
        check(!CalendarDates(context, "unsupported-calendar").supported)
        val old = LauncherConfiguration(Page.FAV_APPS,
            mapOf(Page.FAV_CONTACTS to Gesture.LEFT, Page.TASKS to Gesture.RIGHT, Page.SEARCH to Gesture.DOWN))
        instrumentation.runOnMainSync {
            val overlay = WelcomeOverlay(android.view.ContextThemeWrapper(context, R.style.Appearance_Light), WelcomeStep.MAP, {}, old)
            fun boxes(view: View): List<String> = if (view.id == R.id.welcome_map_box) {
                listOf((view as TextView).text.toString())
            } else if (view is ViewGroup) (0 until view.childCount).flatMap { boxes(view.getChildAt(it)) } else emptyList()
            check(boxes(overlay.view).none { it == "Calendar" })
        }
    }

    fun providerAndEditor() {
        val repository = CalendarRepository(context)
        check(repository.canRead() && repository.canWrite()) { "Grant calendar permissions on an isolated emulator first" }
        val resolver = context.contentResolver
        val account = "bareshell-test-${UUID.randomUUID()}"
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val inserted = requireNotNull(resolver.insert(uri, ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, account)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, account)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Calendar fixture")
            put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.VISIBLE, 1); put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
        }))
        val id = ContentUris.parseId(inserted)
        val identity = CalendarIdentity(id, account, CalendarContract.ACCOUNT_TYPE_LOCAL)
        val preferences = context.getSharedPreferences("launcher_configuration", Context.MODE_PRIVATE)
        val settings = SettingsStore(context)
        val oldSettings = (settings.load() as SettingsRead.Ready).settings
        var secondId: Long? = null
        try {
            val second = requireNotNull(resolver.insert(uri, ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "$account-second")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Second fixture")
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.VISIBLE, 1); put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
            }))
            secondId = ContentUris.parseId(second)
            val writeIdentity = CalendarIdentity(secondId, account, CalendarContract.ACCOUNT_TYPE_LOCAL)
            val today = LocalDate.now()
            val start = today.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val draft = CalendarEvent(0, id, "Fixture", "Notes", "Place", start, start + 3_600_000,
                false, ZoneId.systemDefault().id, null, null, null, null)
            val result = repository.save(identity, null, draft)
            check(result is CalendarSave.Saved) { result }
            val original = (repository.event(result.id) as CalendarRead.Loaded).value!!
            check(repository.save(identity, original, original.copy(title = "Edited")) is CalendarSave.Saved)
            check(repository.save(identity, original, original.copy(title = "Stale")) == CalendarSave.Conflict)
            val allDay = draft.copy(title = "All day", note = null, location = null, allDay = true, timeZone = "UTC",
                start = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                end = today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            check(repository.save(identity, null, allDay) is CalendarSave.Saved)
            val recurring = requireNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, id); put(CalendarContract.Events.TITLE, "Recurring")
                put(CalendarContract.Events.DTSTART, start); put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
                put(CalendarContract.Events.DURATION, "PT3600S"); put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=3")
            }))
            check((repository.event(ContentUris.parseId(recurring)) as CalendarRead.Loaded).value!!.externalEdit)
            val occurrences = (repository.occurrences(identity, today, ZoneId.systemDefault()) as CalendarRead.Loaded).value
            check(occurrences.count { it.title == "Recurring" } == 3)
            check(calendarAgenda(occurrences, today, ZoneId.systemDefault()).map { it.date } ==
                listOf(today, today.plusDays(1), today.plusDays(2)))
            check(repository.save(writeIdentity, null, draft.copy(calendarId = writeIdentity.id, title = "Second event")) is CalendarSave.Saved)
            val merged = (repository.occurrences(listOf(identity, writeIdentity), today, ZoneId.systemDefault()) as CalendarRead.Loaded).value
            check(merged.size == occurrences.size + 1)
            check(merged.any { it.title == "Edited" } && merged.any { it.title == "Second event" })
            val nextSettings = oldSettings.copy(eventCalendars = listOf(identity, writeIdentity), writeCalendar = writeIdentity,
                dateSystem = "persian", dateFormat = CalendarDateFormat.DAY_MONTH, weekday = CalendarWeekday.FULL)
            check(settings.save(nextSettings))
            check(settings.load() == SettingsRead.Ready(nextSettings))
            check(ConfigurationStore(context).save(LauncherConfiguration(Page.CALENDAR, emptyMap())))
            editor(repository, writeIdentity, identity)
            check((repository.occurrences(identity, today, ZoneId.systemDefault()) as CalendarRead.Loaded).value
                .none { it.title == "Created in launcher" })
            val fixtureUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id).buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
            check(resolver.update(fixtureUri, ContentValues().apply {
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ)
            }, null, null) == 1)
            check(repository.save(identity, null, draft) == CalendarSave.ReadOnly)
        } finally {
            secondId?.let { check(resolver.delete(uri, "${CalendarContract.Calendars._ID}=?", arrayOf(it.toString())) == 1) }
            check(resolver.delete(uri, "${CalendarContract.Calendars._ID}=?", arrayOf(id.toString())) == 1)
            check(settings.save(oldSettings))
            check(preferences.edit().clear().commit())
        }
    }

    private fun editor(repository: CalendarRepository, identity: CalendarIdentity, originalCalendar: CalendarIdentity) {
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitFor {
                activity.findViewById<View>(R.id.calendar_add) != null
            }
            instrumentation.runOnMainSync {
                check(activity.findViewById<TextView>(R.id.calendar_heading).text.isNotBlank())
                activity.findViewById<View>(R.id.calendar_add).performClick()
                activity.findViewById<EditText>(R.id.calendar_title).setText("Created in launcher")
                activity.findViewById<View>(R.id.calendar_save).performClick()
            }
            waitFor { activity.findViewById<View>(R.id.calendar_title) == null }
            val events = (repository.occurrences(identity, LocalDate.now(), ZoneId.systemDefault()) as CalendarRead.Loaded).value
            check(events.count { it.title == "Created in launcher" } == 1)
            waitFor { textViews(activity.findViewById(R.id.calendar_page)).any { it.id == R.id.calendar_row && it.text.endsWith("Edited") } }
            instrumentation.runOnMainSync {
                textViews(activity.findViewById(R.id.calendar_page)).first { it.id == R.id.calendar_row && it.text.endsWith("Edited") }
                    .performClick()
            }
            waitFor { textViews(activity.findViewById(R.id.calendar_page)).any { it.text.toString() == context.getString(R.string.task_edit) } }
            instrumentation.runOnMainSync {
                textViews(activity.findViewById(R.id.calendar_page)).first { it.text.toString() == context.getString(R.string.task_edit) }
                    .performClick()
                activity.findViewById<EditText>(R.id.calendar_title).setText("Updated original")
                activity.findViewById<View>(R.id.calendar_save).performClick()
            }
            waitFor { activity.findViewById<View>(R.id.calendar_title) == null }
            check((repository.occurrences(originalCalendar, LocalDate.now(), ZoneId.systemDefault()) as CalendarRead.Loaded).value
                .any { it.title == "Updated original" })
            check((repository.occurrences(identity, LocalDate.now(), ZoneId.systemDefault()) as CalendarRead.Loaded).value
                .none { it.title == "Updated original" })
            instrumentation.runOnMainSync {
                activity.findViewById<View>(R.id.calendar_add).performClick()
                activity.findViewById<EditText>(R.id.calendar_title).setText("Unsaved draft")
                val state = android.os.Bundle()
                instrumentation.callActivityOnSaveInstanceState(activity, state)
                val page = CalendarPage(activity, { LauncherSettings(eventCalendars = listOf(identity), dateSystem = "chinese") },
                    {}, {}, {}, state.getBundle("calendar_state"))
                check(page.view.findViewById<EditText>(R.id.calendar_title).text.toString() == "Unsaved draft")
                var left = false
                page.requestLeave { left = true }
                check(!left)
                page.dispose()
                val cleanState = android.os.Bundle(requireNotNull(state.getBundle("calendar_state")))
                cleanState.putBundle("calendar_draft", android.os.Bundle(requireNotNull(cleanState.getBundle("calendar_initial"))))
                val cleanPage = CalendarPage(activity, { LauncherSettings(eventCalendars = listOf(identity)) }, {}, {}, {}, cleanState)
                cleanPage.requestLeave { left = true }
                check(left)
                cleanPage.dispose()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(100) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        }
        error("Calendar UI did not settle")
    }
}
