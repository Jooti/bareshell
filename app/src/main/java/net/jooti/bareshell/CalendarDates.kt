package net.jooti.bareshell

import android.content.Context
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

internal class CalendarDates(context: Context, choice: String) {
    private val locale = ULocale.forLocale(context.resources.configuration.locales[0])
    val type: String = if (choice == "device") Calendar.getInstance(locale).type else choice
    val supported: Boolean = type in available(locale) && calendar(LocalDate.now()).type == type
    val name: String get() = name(locale, type)

    fun calendar(date: LocalDate): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone("UTC"), locale.setKeywordValue("calendar", type),
    ).apply { timeInMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

    fun format(date: LocalDate, skeleton: String = "EEEEMMMMdyyyy"): String {
        check(supported) { "Unavailable calendar system: $type" }
        return DateFormat.getInstanceForSkeleton(skeleton, locale.setKeywordValue("calendar", type)).apply {
            calendar = this@CalendarDates.calendar(date)
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
    }

    fun heading(date: LocalDate, settings: LauncherSettings): String {
        val weekday = pattern(date, if (settings.weekday == CalendarWeekday.FULL) "EEEE" else "EEE")
        val format = settings.dateFormat
        val dateText = if (format.pattern != null) pattern(date, format.pattern) else {
            val style = when (format) {
                CalendarDateFormat.SHORT -> DateFormat.SHORT
                CalendarDateFormat.MEDIUM -> DateFormat.MEDIUM
                CalendarDateFormat.LONG -> DateFormat.LONG
                else -> error("Expected localized date format")
            }
            DateFormat.getDateInstance(style, locale.setKeywordValue("calendar", type)).apply {
                calendar = this@CalendarDates.calendar(date)
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
        }
        return "$weekday $dateText"
    }

    private fun pattern(date: LocalDate, pattern: String): String {
        check(supported) { "Unavailable calendar system: $type" }
        return SimpleDateFormat(pattern, locale.setKeywordValue("calendar", type)).apply {
            calendar = this@CalendarDates.calendar(date)
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
    }

    fun move(date: LocalDate, field: Int, amount: Int): LocalDate {
        if (field != Calendar.MONTH) return calendar(date).apply { add(field, amount) }
            .let { LocalDate.ofEpochDay(Math.floorDiv(it.timeInMillis, 86_400_000L)) }
        // Some ICU calendars inherit Gregorian MONTH addition rather than their own month boundaries.
        var target = date
        repeat(kotlin.math.abs(amount)) {
            val days = monthDays(target)
            val dayIndex = days.indexOf(target)
            val adjacent = monthDays(if (amount > 0) days.last().plusDays(1) else days.first().minusDays(1))
            target = adjacent[dayIndex.coerceAtMost(adjacent.lastIndex)]
        }
        return target
    }

    fun monthDays(date: LocalDate): List<LocalDate> {
        val month = calendar(date).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val count = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (0 until count).map { offset ->
            (month.clone() as Calendar).apply { add(Calendar.DATE, offset) }
                .let { LocalDate.ofEpochDay(Math.floorDiv(it.timeInMillis, 86_400_000L)) }
        }
    }

    companion object {
        fun available(locale: ULocale): List<String> =
            Calendar.getKeywordValuesForLocale("calendar", locale, false).distinct().filter { type ->
                Calendar.getInstance(locale.setKeywordValue("calendar", type)).type == type
            }

        fun name(locale: ULocale, type: String): String {
            val localized = locale.setKeywordValue("calendar", type).getDisplayKeywordValue("calendar", locale)
            return if (type == "persian") "$localized / Jalali" else localized
        }
    }
}
