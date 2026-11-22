package net.jooti.bareshell

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.provider.CalendarContract
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Executors

internal sealed interface CalendarRead<out T> {
    data class Loaded<T>(val value: T) : CalendarRead<T>
    data object Denied : CalendarRead<Nothing>
    data object Unavailable : CalendarRead<Nothing>
}

internal sealed interface CalendarSave {
    data class Saved(val id: Long) : CalendarSave
    data object Conflict : CalendarSave
    data object Denied : CalendarSave
    data object ReadOnly : CalendarSave
    data object Invalid : CalendarSave
    data object Unavailable : CalendarSave
    data object Uncertain : CalendarSave
}

internal val calendarWorker = Executors.newSingleThreadExecutor()

internal class CalendarRepository(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    fun canRead() = app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun canWrite() = app.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private class ProviderUnavailable : RuntimeException()

    private fun <T> read(operation: () -> T): CalendarRead<T> {
        if (!canRead()) return CalendarRead.Denied
        return try {
            val value = operation()
            if (canRead()) CalendarRead.Loaded(value) else CalendarRead.Denied
        } catch (error: SecurityException) {
            Log.e(TAG, "Calendar access denied", error)
            CalendarRead.Denied
        } catch (error: SQLiteException) {
            Log.e(TAG, "Calendar query failed", error)
            CalendarRead.Unavailable
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Calendar provider rejected query", error)
            CalendarRead.Unavailable
        } catch (error: ProviderUnavailable) {
            Log.e(TAG, "Calendar provider returned no cursor", error)
            CalendarRead.Unavailable
        }
    }

    fun calendars(): CalendarRead<List<DeviceCalendar>> = read {
        resolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        ), "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_READ.toString()), null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(DeviceCalendar(CalendarIdentity(cursor.getLong(0), cursor.getString(1).orEmpty(),
                        cursor.getString(2).orEmpty()), cursor.getString(3).orEmpty(),
                        cursor.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR))
                }
            }.sortedWith(compareBy<DeviceCalendar> { it.name }.thenBy { it.identity.account }.thenBy { it.identity.id })
        } ?: throw ProviderUnavailable()
    }

    fun occurrences(calendar: CalendarIdentity, today: LocalDate, zone: ZoneId): CalendarRead<List<CalendarOccurrence>> =
        occurrences(listOf(calendar), today, zone)

    fun occurrences(calendars: List<CalendarIdentity>, today: LocalDate, zone: ZoneId): CalendarRead<List<CalendarOccurrence>> = read {
        require(calendars.isNotEmpty()) { "Select at least one calendar" }
        val begin = minOf(today.atStartOfDay(zone).toInstant().toEpochMilli(),
            today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val end = maxOf(today.plusDays(15).atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, begin)
            ContentUris.appendId(this, end)
        }.build()
        resolver.query(uri, arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
        ), "${CalendarContract.Instances.CALENDAR_ID} IN (${calendars.joinToString(",") { "?" }}) AND ${CalendarContract.Events.DELETED}=0 AND " +
            "(${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS}!=?)",
            (calendars.map { it.id.toString() } + CalendarContract.Events.STATUS_CANCELED.toString()).toTypedArray(), null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(CalendarOccurrence(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2),
                        cursor.getLong(3), cursor.getInt(4) != 0))
                }
            }
        } ?: throw ProviderUnavailable()
    }

    fun event(id: Long): CalendarRead<CalendarEvent?> = read { eventRecord(id) }

    private fun eventRecord(id: Long): CalendarEvent? {
        val cursor = resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), projection,
            "${CalendarContract.Events.DELETED}=0 AND (${CalendarContract.Events.STATUS} IS NULL OR " +
                "${CalendarContract.Events.STATUS}!=?)",
            arrayOf(CalendarContract.Events.STATUS_CANCELED.toString()), null) ?: throw ProviderUnavailable()
        return cursor.use {
            if (!cursor.moveToFirst()) null else CalendarEvent(
                cursor.getLong(0), cursor.getLong(1), cursor.getString(2).orEmpty(), cursor.getString(3), cursor.getString(4),
                cursor.getLong(5), cursor.getLong(6), cursor.getInt(7) != 0, cursor.getString(8).orEmpty(),
                cursor.getString(9), cursor.getString(10), cursor.getString(11),
                if (cursor.isNull(12)) null else cursor.getLong(12),
            )
        }
    }

    fun save(source: CalendarIdentity, original: CalendarEvent?, draft: CalendarEvent): CalendarSave {
        if (!canRead() || !canWrite()) return CalendarSave.Denied
        if (!validCalendarEvent(draft) || draft.externalEdit) return CalendarSave.Invalid
        val calendars = calendars()
        if (calendars == CalendarRead.Denied) return CalendarSave.Denied
        if (calendars !is CalendarRead.Loaded) return CalendarSave.Unavailable
        val calendar = calendars.value.firstOrNull { it.identity == source } ?: return CalendarSave.Conflict
        if (!calendar.writable) return CalendarSave.ReadOnly
        if (draft.calendarId != source.id || original != null && original.calendarId != source.id) return CalendarSave.Conflict
        var attempted = false
        return try {
            if (original != null && (original.externalEdit || eventRecord(original.id) != original)) return CalendarSave.Conflict
            val values = values(draft)
            val id: Long
            if (original == null) {
                values.put(CalendarContract.Events.CALENDAR_ID, source.id)
                attempted = true
                val inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return CalendarSave.Uncertain
                id = ContentUris.parseId(inserted)
            } else {
                val baseline = values(original).apply {
                    put(CalendarContract.Events.CALENDAR_ID, original.calendarId)
                    put(CalendarContract.Events.RRULE, original.recurrence)
                    put(CalendarContract.Events.RDATE, original.recurrenceDates)
                    if (original.originalId == null) putNull(CalendarContract.Events.ORIGINAL_ID)
                    else put(CalendarContract.Events.ORIGINAL_ID, original.originalId)
                }
                val args = mutableListOf(original.id.toString())
                val selection = "${CalendarContract.Events._ID}=? AND " + baseline.keySet().joinToString(" AND ") { key ->
                    val value = baseline.getAsString(key)
                    if (value == null) "$key IS NULL" else "$key=?".also { args.add(value) }
                } + " AND ${CalendarContract.Events.DELETED}=0 AND (${CalendarContract.Events.STATUS} IS NULL OR " +
                    "${CalendarContract.Events.STATUS}!=${CalendarContract.Events.STATUS_CANCELED})"
                attempted = true
                val changed = resolver.update(CalendarContract.Events.CONTENT_URI,
                    values, selection, args.toTypedArray())
                if (changed == 0) return CalendarSave.Conflict
                if (changed != 1) return CalendarSave.Uncertain
                id = original.id
            }
            val stored = eventRecord(id)
            if (stored != draft.copy(id = id)) CalendarSave.Uncertain else CalendarSave.Saved(id)
        } catch (error: SecurityException) {
            Log.e(TAG, "Calendar write denied", error)
            if (attempted) CalendarSave.Uncertain else CalendarSave.Denied
        } catch (error: SQLiteException) {
            Log.e(TAG, "Calendar write failed", error)
            if (attempted) CalendarSave.Uncertain else CalendarSave.Unavailable
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Calendar provider rejected write", error)
            if (attempted) CalendarSave.Uncertain else CalendarSave.Unavailable
        } catch (error: ProviderUnavailable) {
            Log.e(TAG, "Cannot verify calendar write", error)
            if (attempted) CalendarSave.Uncertain else CalendarSave.Unavailable
        }
    }

    private fun values(event: CalendarEvent) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DESCRIPTION, event.note)
        put(CalendarContract.Events.EVENT_LOCATION, event.location)
        put(CalendarContract.Events.DTSTART, event.start)
        put(CalendarContract.Events.DTEND, event.end)
        put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
        put(CalendarContract.Events.EVENT_END_TIMEZONE, event.endTimeZone)
    }

    companion object {
        private const val TAG = "BareShellCalendar"
        private val projection = arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_END_TIMEZONE, CalendarContract.Events.RRULE, CalendarContract.Events.RDATE,
            CalendarContract.Events.ORIGINAL_ID,
        )
    }
}
