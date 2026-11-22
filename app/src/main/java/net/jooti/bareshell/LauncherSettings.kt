package net.jooti.bareshell

internal val listCountOptions = listOf(5, 10, 15, 20, 30, 50)

internal enum class AppearanceTheme { SYSTEM, DARK, LIGHT, GREEN, GRAY }
internal enum class AppearanceFont { MONOSPACE, SYSTEM }
internal enum class CalendarDateFormat(val pattern: String?) {
    MONTH_DAY("MM/dd"), DAY_MONTH("dd/MM"), MONTH_DAY_YEAR("MM/dd/yyyy"),
    DAY_MONTH_YEAR("dd/MM/yyyy"), YEAR_MONTH_DAY("yyyy-MM-dd"),
    SHORT(null), MEDIUM(null), LONG(null);
}
internal enum class CalendarWeekday { SHORT, FULL }

internal data class LauncherSettings(
    val topAppsCount: Int = 10,
    val closeContactsCount: Int = 10,
    val appearanceTheme: AppearanceTheme = AppearanceTheme.SYSTEM,
    val appearanceFont: AppearanceFont = AppearanceFont.MONOSPACE,
    val eventCalendars: List<CalendarIdentity>? = null,
    val dateSystem: String = "device",
    val writeCalendar: CalendarIdentity? = null,
    val dateFormat: CalendarDateFormat = CalendarDateFormat.MONTH_DAY,
    val weekday: CalendarWeekday = CalendarWeekday.SHORT,
) {
    init {
        require(topAppsCount in listCountOptions && closeContactsCount in listCountOptions)
        require(dateSystem.matches(Regex("[a-z][a-z0-9-]{0,39}")))
        require(eventCalendars == null || eventCalendars.all { it.id >= 0 } &&
            eventCalendars.map { it.id }.distinct().size == eventCalendars.size)
        require(writeCalendar == null || writeCalendar.id >= 0 &&
            (eventCalendars == null || writeCalendar in eventCalendars))
    }
}

internal sealed interface SettingsRead {
    data class Ready(val settings: LauncherSettings) : SettingsRead
    data object Invalid : SettingsRead
}

internal fun readSettings(values: Map<String, *>): SettingsRead {
    val apps = if ("top_apps_count" in values) values["top_apps_count"] else 10
    val contacts = if ("close_contacts_count" in values) values["close_contacts_count"] else 10
    if (apps !is Int || contacts !is Int || apps !in listCountOptions || contacts !in listCountOptions) {
        return SettingsRead.Invalid
    }
    val theme = if ("appearance_theme" !in values) AppearanceTheme.SYSTEM else
        AppearanceTheme.entries.firstOrNull { it.name == values["appearance_theme"] } ?: return SettingsRead.Invalid
    val font = if ("appearance_font" !in values) AppearanceFont.MONOSPACE else
        AppearanceFont.entries.firstOrNull { it.name == values["appearance_font"] } ?: return SettingsRead.Invalid
    val dateSystem = if ("date_system" in values) values["date_system"] else "device"
    if (dateSystem !is String || !dateSystem.matches(Regex("[a-z][a-z0-9-]{0,39}"))) return SettingsRead.Invalid
    val calendarKeys = setOf("calendar_id", "calendar_account", "calendar_account_type")
    val legacyCalendar = if (calendarKeys.any { it in values }) {
        val id = values["calendar_id"]
        val account = values["calendar_account"]
        val type = values["calendar_account_type"]
        if (id !is Long || id < 0 || account !is String || type !is String) return SettingsRead.Invalid
        CalendarIdentity(id, account, type)
    } else null
    val calendars = if ("calendar_count" in values) {
        val count = values["calendar_count"]
        if (count !is Int || count < 0 || count > values.size / 3) return SettingsRead.Invalid
        (0 until count).map { index ->
            readCalendarIdentity(values, "calendar.$index") ?: return SettingsRead.Invalid
        }
    } else legacyCalendar?.let { listOf(it) }
    if (calendars != null && calendars.map { it.id }.distinct().size != calendars.size) return SettingsRead.Invalid
    val writeKeys = setOf("write_calendar.id", "write_calendar.account", "write_calendar.type")
    val writeCalendar = if (writeKeys.any { it in values }) {
        readCalendarIdentity(values, "write_calendar") ?: return SettingsRead.Invalid
    } else legacyCalendar
    if (writeCalendar != null && calendars != null && writeCalendar !in calendars) return SettingsRead.Invalid
    val format = if ("date_format" !in values) CalendarDateFormat.MONTH_DAY else
        CalendarDateFormat.entries.firstOrNull { it.name == values["date_format"] } ?: return SettingsRead.Invalid
    val weekday = if ("calendar_weekday" !in values) CalendarWeekday.SHORT else
        CalendarWeekday.entries.firstOrNull { it.name == values["calendar_weekday"] } ?: return SettingsRead.Invalid
    return SettingsRead.Ready(LauncherSettings(apps, contacts, theme, font, calendars, dateSystem, writeCalendar, format, weekday))
}

private fun readCalendarIdentity(values: Map<String, *>, prefix: String): CalendarIdentity? {
    val id = values["$prefix.id"]
    val account = values["$prefix.account"]
    val type = values["$prefix.type"]
    return if (id is Long && id >= 0 && account is String && type is String) CalendarIdentity(id, account, type) else null
}

internal fun LauncherSettings.calendarValues(): Map<String, Any> = buildMap {
    put("date_system", dateSystem)
    put("date_format", dateFormat.name)
    put("calendar_weekday", weekday.name)
    fun identity(prefix: String, calendar: CalendarIdentity) {
        put("$prefix.id", calendar.id)
        put("$prefix.account", calendar.account)
        put("$prefix.type", calendar.accountType)
    }
    eventCalendars?.let { calendars ->
        put("calendar_count", calendars.size)
        calendars.forEachIndexed { index, calendar -> identity("calendar.$index", calendar) }
    }
    writeCalendar?.let { identity("write_calendar", it) }
}
