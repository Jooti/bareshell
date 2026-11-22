package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsTest {
    @Test fun calendarPreferencesAreIndependentAndOldSettingsRemainCompatible() {
        val source = CalendarIdentity(12, "local", "LOCAL")
        assertEquals(SettingsRead.Ready(LauncherSettings(eventCalendars = listOf(source), dateSystem = "persian", writeCalendar = source)),
            readSettings(mapOf("calendar_id" to 12L, "calendar_account" to "local",
                "calendar_account_type" to "LOCAL", "date_system" to "persian")))
        assertEquals(SettingsRead.Ready(LauncherSettings(dateSystem = "chinese")),
            readSettings(mapOf("date_system" to "chinese")))
        assertEquals(SettingsRead.Ready(LauncherSettings()), readSettings(emptyMap<String, Any>()))
    }

    @Test fun incompleteCalendarIdentityAndMalformedDateChoicesAreRejected() {
        listOf(
            mapOf("calendar_id" to 1L), mapOf("calendar_id" to -1L, "calendar_account" to "", "calendar_account_type" to ""),
            mapOf("calendar_id" to 1, "calendar_account" to "", "calendar_account_type" to ""),
            mapOf("date_system" to ""), mapOf("date_system" to 1), mapOf("date_system" to "Gregorian"),
        ).forEach { assertEquals(SettingsRead.Invalid, readSettings(it)) }
    }

    @Test fun multipleCalendarsAndDatePreferencesRoundTripIndependently() {
        val sources = listOf(CalendarIdentity(1, "local", "LOCAL"), CalendarIdentity(2, "other", "LOCAL"))
        CalendarDateFormat.entries.forEach { format ->
            CalendarWeekday.entries.forEach { weekday ->
                val settings = LauncherSettings(eventCalendars = sources, writeCalendar = sources[1],
                    dateSystem = "persian", dateFormat = format, weekday = weekday)
                assertEquals(SettingsRead.Ready(settings), readSettings(settings.calendarValues()))
            }
        }
        val empty = LauncherSettings(eventCalendars = emptyList())
        assertEquals(SettingsRead.Ready(empty), readSettings(empty.calendarValues()))
        assertEquals(SettingsRead.Ready(LauncherSettings()), readSettings(LauncherSettings().calendarValues()))
    }

    @Test fun invalidMultiCalendarAndFormatPreferencesAreRejected() {
        val sources = listOf(CalendarIdentity(1, "local", "LOCAL"), CalendarIdentity(2, "other", "LOCAL"))
        val values = LauncherSettings(eventCalendars = sources, writeCalendar = sources[1]).calendarValues()
        listOf(
            values - "calendar.1.account", values + ("calendar_count" to -1),
            values + ("calendar_count" to "2"), values + ("calendar.1.id" to 1L),
            values + ("write_calendar.id" to 9L), values - "write_calendar.type",
            values + ("date_format" to "OTHER"), values + ("calendar_weekday" to 1),
        ).forEach { assertEquals(SettingsRead.Invalid, readSettings(it)) }
    }
    @Test
    fun appearanceDefaultsAndEveryStoredChoiceAreReadWithoutChangingCounts() {
        assertEquals(AppearanceTheme.SYSTEM, LauncherSettings().appearanceTheme)
        assertEquals(AppearanceFont.MONOSPACE, LauncherSettings().appearanceFont)
        AppearanceTheme.entries.forEach { theme ->
            AppearanceFont.entries.forEach { font ->
                assertEquals(
                    SettingsRead.Ready(LauncherSettings(20, 5, theme, font)),
                    readSettings(mapOf("top_apps_count" to 20, "close_contacts_count" to 5,
                        "appearance_theme" to theme.name, "appearance_font" to font.name)),
                )
            }
        }
    }

    @Test
    fun invalidAppearanceIsRejectedExplicitly() {
        listOf("UNKNOWN", "", 1, true, null).forEach { value ->
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("appearance_theme" to value)))
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("appearance_font" to value)))
        }
    }
    @Test
    fun defaultsAreIndependentAndAllSixChoicesAreSupported() {
        assertEquals(SettingsRead.Ready(LauncherSettings(10, 10)), readSettings(emptyMap<String, Any>()))
        assertEquals(listOf(5, 10, 15, 20, 30, 50), listCountOptions)
        listCountOptions.forEach { count ->
            assertEquals(SettingsRead.Ready(LauncherSettings(count, 10)), readSettings(mapOf("top_apps_count" to count)))
            assertEquals(SettingsRead.Ready(LauncherSettings(10, count)), readSettings(mapOf("close_contacts_count" to count)))
        }
    }

    @Test
    fun invalidCountsAndTypesAreExplicitlyRejected() {
        listOf(0, -1, 7, 100, "10", 10L, true, null).forEach { invalid ->
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("top_apps_count" to invalid)))
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("close_contacts_count" to invalid)))
        }
    }
}
