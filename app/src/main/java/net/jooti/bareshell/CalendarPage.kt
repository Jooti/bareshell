package net.jooti.bareshell

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

internal class CalendarPage(
    private val activity: Activity,
    private val settings: () -> LauncherSettings,
    private val allowRead: () -> Unit,
    private val allowWrite: () -> Unit,
    private val openSettings: () -> Unit,
    state: Bundle? = null,
) {
    private val repository = CalendarRepository(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val view = ScrollView(activity).apply {
        id = R.id.calendar_page
        isSaveEnabled = false
        isFillViewport = true
        addView(body)
    }
    private var active = true
    private var foreground = false
    private var observing = false
    private var generation = 0
    private var loading = false
    private var sources = emptyList<DeviceCalendar>()
    private var loadedSources = emptyList<CalendarIdentity>()
    private var occurrences = emptyList<CalendarOccurrence>()
    private var status = R.string.calendar_loading
    private var selected: CalendarOccurrence? = null
    private var detail: CalendarEvent? = null
    private var editor: LinearLayout? = null
    private var editorDateSystem = settings().dateSystem
    private var original: CalendarEvent? = null
    private var draftSource: CalendarIdentity? = null
    private var title = ""
    private var note = ""
    private var location = ""
    private var startDate = LocalDate.now()
    private var endDate = startDate
    private var startMinute = 9 * 60
    private var endMinute = 10 * 60
    private var allDay = false
    private var startZone = ZoneId.systemDefault()
    private var endZone = startZone
    private var initialDraft: Bundle? = null
    private var pending = state?.getString("calendar_pending")
    private var writeStatus = 0
    private var dialog: AlertDialog? = null
    private var picker: android.app.Dialog? = null
    private var restoredScroll = state?.getInt("calendar_scroll")
    private val refreshWork = Runnable { refresh() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refreshWork)
            if (active && foreground) handler.postDelayed(refreshWork, 200)
        }
    }

    init {
        if (repository.canRead() && state != null) {
            state.getBundle("calendar_selected")?.let {
                selected = CalendarOccurrence(it.getLong("id"), it.getString("title").orEmpty(),
                    it.getLong("begin"), it.getLong("end"), it.getBoolean("all_day"))
            }
            if (state.getBoolean("calendar_editing")) {
                original = state.getBundle("calendar_original")?.let(::readEvent)
                draftSource = state.getBundle("calendar_source")?.let(::readIdentity)
                restoreDraft(requireNotNull(state.getBundle("calendar_draft")))
                initialDraft = state.getBundle("calendar_initial")
                makeEditor()
            }
        }
        render()
        refresh()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value && repository.canRead() && !observing) {
            try {
                activity.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
                observing = true
            } catch (error: SecurityException) {
                Log.e(TAG, "Calendar observation denied", error)
                status = R.string.calendar_allow_read
                clearPrivateState()
                render()
            }
        } else if (!value && observing) {
            activity.contentResolver.unregisterContentObserver(observer)
            observing = false
        }
        if (!value) handler.removeCallbacks(refreshWork)
    }

    fun refresh() {
        if (!active) return
        if (!repository.canRead()) {
            generation++
            loading = false
            if (editor != null) Toast.makeText(activity, R.string.calendar_access_removed, Toast.LENGTH_LONG).show()
            clearPrivateState()
            sources = emptyList()
            status = R.string.calendar_allow_read
            setForeground(false)
            render()
            return
        }
        if (foreground) setForeground(true)
        val request = ++generation
        val preference = settings()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val selectedId = selected?.eventId
        val pendingId = pending
        loading = true
        calendarWorker.execute {
            val calendars = repository.calendars()
            val chosen = (calendars as? CalendarRead.Loaded)?.value?.let { selectedCalendars(it, preference) }.orEmpty()
            val events = if (chosen.isNotEmpty()) repository.occurrences(chosen.map { it.identity }, today, zone) else null
            val details = selectedId?.let(repository::event)
            val saved = pendingId?.let { synchronized(writes) { writes[it] ?: CalendarSave.Uncertain } }
            handler.post {
                if (!active || request != generation) return@post
                loading = false
                if (preference != settings() || today != LocalDate.now() || zone != ZoneId.systemDefault()) {
                    refresh()
                    return@post
                }
                if (!repository.canRead() || calendars == CalendarRead.Denied || events == CalendarRead.Denied) {
                    clearPrivateState()
                    sources = emptyList()
                    status = R.string.calendar_allow_read
                    render()
                    return@post
                }
                sources = chosen
                val missing = preference.eventCalendars?.any { identity -> chosen.none { it.identity == identity } } == true
                status = when {
                    calendars !is CalendarRead.Loaded -> R.string.calendar_load_failed
                    calendars.value.isEmpty() -> R.string.calendar_no_sources
                    missing -> R.string.calendar_selected_unavailable
                    chosen.isEmpty() -> R.string.calendar_choose_source
                    events !is CalendarRead.Loaded -> R.string.calendar_load_failed
                    else -> 0
                }
                val identities = chosen.map { it.identity }
                if (loadedSources.isNotEmpty() && loadedSources != identities) {
                    occurrences = emptyList()
                    selected = null
                    detail = null
                }
                if (events is CalendarRead.Loaded) {
                    loadedSources = identities
                    occurrences = events.value
                }
                if (details is CalendarRead.Loaded && chosen.any { it.identity.id == details.value?.calendarId }) detail = details.value
                else if (selected != null && editor == null) {
                    detail = null
                    if (details is CalendarRead.Loaded) selected = null
                    else writeStatus = R.string.calendar_detail_failed
                }
                if (saved != null) settleSave(requireNotNull(pendingId), saved)
                render()
            }
        }
    }

    private fun clearPrivateState() {
        if (pending != null) writeStatus = R.string.calendar_save_uncertain
        pending = null
        occurrences = emptyList()
        loadedSources = emptyList()
        selected = null
        detail = null
        closeEditor()
    }

    @Suppress("DEPRECATION")
    private val dirty: Boolean
        get() {
            val draft = draftBundle()
            val initial = initialDraft ?: return true
            return draft.keySet().any { draft.get(it) != initial.get(it) }
        }

    fun requestLeave(action: () -> Unit) {
        if (pending != null && writeStatus != R.string.calendar_save_uncertain) {
            Toast.makeText(activity, R.string.settings_saving, Toast.LENGTH_SHORT).show()
            return
        }
        if (editor == null || (!dirty && pending == null)) {
            action()
        } else {
            dialog?.dismiss()
            dialog = AlertDialog.Builder(activity)
                .setMessage(if (pending == null) R.string.calendar_discard_question else R.string.calendar_save_uncertain)
                .setPositiveButton(R.string.task_discard) { _, _ ->
                    closeEditor()
                    pending = null
                    writeStatus = 0
                    action()
                }.setNegativeButton(R.string.task_keep_editing, null).show()
        }

    }

    fun back(): Boolean {
        if (editor == null && selected == null) return false
        requestLeave {
            if (editor != null) closeEditor() else { selected = null; detail = null }
            render()
        }
        return true
    }

    fun dispose() {
        active = false
        generation++
        setForeground(false)
        dialog?.dismiss()
        picker?.dismiss()
        handler.removeCallbacks(refreshWork)
    }

    private fun label(text: String, id: Int = R.id.calendar_action, primary: Boolean = false, action: (() -> Unit)? = null): TextView =
        activity.textLabel(text, id, primary).also { textView ->
            if (action != null) textView.onTextClick(action)
        }

    private fun render() {
        if (editor != null && editorDateSystem != settings().dateSystem) makeEditor()
        val scroll = restoredScroll ?: view.scrollY
        restoredScroll = null
        (editor?.parent as? ViewGroup)?.removeView(editor)
        body.removeAllViews()
        val dates = CalendarDates(activity, settings().dateSystem)
        if (!dates.supported) {
            body.addView(label(activity.getString(R.string.calendar_system_unavailable), action = {
                requestLeave(openSettings)
            }))
            return
        }
        val canRead = repository.canRead()
        if (canRead && sources.isNotEmpty()) body.addView(label(activity.getString(R.string.calendar_add), R.id.calendar_add, action = {
            requestLeave { startEditor(null) }
        }).apply { isEnabled = pending == null && sources.any { it.writable } })
        if (editor != null) body.addView(editor)
        if (writeStatus != 0) body.addView(label(activity.getString(writeStatus), R.id.status_message).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        val today = LocalDate.now()
        val days = calendarAgenda(if (canRead) occurrences else emptyList(), today, ZoneId.systemDefault())
        days.forEach { day ->
            body.addView(label(dates.heading(day.date, settings()), R.id.calendar_heading, true).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
            })
            if (day.date == today && status != 0) {
                val action: () -> Unit = when (status) {
                    R.string.calendar_allow_read -> allowRead
                    R.string.calendar_choose_source, R.string.calendar_no_sources, R.string.calendar_selected_unavailable -> {
                        { requestLeave(openSettings) }
                    }
                    else -> ::refresh
                }
                body.addView(label(activity.getString(status), R.id.status_message, action = action))
            } else if (day.events.isEmpty()) {
                body.addView(label(activity.getString(R.string.calendar_no_events)).apply {
                    setTextColor(activity.appearanceColor(R.color.foreground_muted))
                })
            }
            day.events.forEach { occurrence ->
                val open = selected == occurrence
                val text = activity.getString(R.string.calendar_event_row, occurrenceTime(occurrence), occurrence.title)
                body.addView(label(text, R.id.calendar_row, action = {
                    requestLeave {
                        closeEditor()
                        selected = if (open) null else occurrence
                        detail = null
                        writeStatus = 0
                        render()
                        if (!open) refresh()
                    }
                }).apply {
                    if (selected != null && !open) setTextColor(activity.appearanceColor(R.color.foreground_muted))
                })
                if (open && editor == null) showDetails()
            }
        }
        view.post { if (active) view.scrollTo(0, scroll) }
    }

    private fun occurrenceTime(event: CalendarOccurrence): String {
        if (event.allDay) return activity.getString(R.string.calendar_all_day)
        return activity.getString(R.string.calendar_time_range, time(event.begin), time(event.end))
    }

    private fun time(millis: Long): String = DateFormat.getTimeFormat(activity).format(java.util.Date(millis))

    private fun showDetails() {
        val record = detail
        if (record == null) {
            body.addView(label(activity.getString(if (loading) R.string.calendar_loading else R.string.calendar_detail_failed),
                action = ::refresh))
            return
        }
        val source = sources.firstOrNull { it.identity.id == record.calendarId } ?: return
        if (source.writable) {
            body.addView(label(activity.getString(if (record.externalEdit) R.string.calendar_edit_external else R.string.task_edit),
                action = { if (record.externalEdit) externalEdit(record) else startEditor(record) }))
        } else body.addView(label(activity.getString(R.string.calendar_read_only)))
        listOfNotNull(record.location?.takeIf(String::isNotBlank), record.note?.takeIf(String::isNotBlank)).forEach {
            body.addView(label(it).apply { setTextColor(activity.appearanceColor(R.color.foreground_muted)) })
        }
        val occurrence = selected ?: return
        val dates = CalendarDates(activity, settings().dateSystem)
        val zone = if (occurrence.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val first = Instant.ofEpochMilli(occurrence.begin).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(if (occurrence.allDay) occurrence.end - 1 else occurrence.end).atZone(zone).toLocalDate()
        if (first != last) body.addView(label(activity.getString(R.string.calendar_time_range, dates.format(first), dates.format(last))))
    }

    private fun externalEdit(event: CalendarEvent) {
        val occurrence = selected ?: return
        try {
            activity.startActivity(Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, occurrence.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, occurrence.end))
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Calendar editor unavailable", error)
            Toast.makeText(activity, R.string.calendar_external_unavailable, Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.e(TAG, "Calendar editor denied", error)
            Toast.makeText(activity, R.string.calendar_external_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun startEditor(event: CalendarEvent?) {
        val calendar = if (event == null) defaultWriteCalendar(sources, settings())
        else sources.firstOrNull { it.identity.id == event.calendarId }
        if (calendar == null) {
            Toast.makeText(activity, R.string.calendar_choose_write, Toast.LENGTH_SHORT).show()
            openSettings()
            return
        }
        if (!calendar.writable) {
            writeStatus = R.string.calendar_read_only
            render()
            return
        }
        if (!repository.canWrite()) {
            allowWrite()
            return
        }
        if (event != null && event.externalEdit) { externalEdit(event); return }
        original = event
        draftSource = calendar.identity
        title = event?.title.orEmpty()
        note = event?.note.orEmpty()
        location = event?.location.orEmpty()
        allDay = event?.allDay == true
        try {
            startZone = if (allDay) ZoneOffset.UTC else if (event == null) ZoneId.systemDefault() else ZoneId.of(event.timeZone)
            endZone = if (allDay) ZoneOffset.UTC else event?.endTimeZone?.let(ZoneId::of) ?: startZone
        } catch (error: DateTimeException) {
            Log.e(TAG, "Unsupported event time zone", error)
            writeStatus = R.string.calendar_zone_unavailable
            original = null
            render()
            if (event != null) externalEdit(event)
            return
        }
        val start = event?.let { Instant.ofEpochMilli(it.start).atZone(startZone) }
        val end = event?.let { Instant.ofEpochMilli(if (allDay) it.end - 1 else it.end).atZone(endZone) }
        startDate = start?.toLocalDate() ?: LocalDate.now()
        endDate = end?.toLocalDate() ?: startDate
        startMinute = start?.let { it.hour * 60 + it.minute } ?: 9 * 60
        endMinute = end?.let { it.hour * 60 + it.minute } ?: 10 * 60
        initialDraft = draftBundle()
        writeStatus = 0
        makeEditor()
        render()
    }

    private fun makeEditor() {
        editorDateSystem = settings().dateSystem
        editor = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(input(R.string.task_title, R.id.calendar_title, title) { title = it })
            addView(input(R.string.task_note, R.id.calendar_note, note, true) { note = it })
            addView(input(R.string.calendar_location, R.id.calendar_location, location) { location = it })
            addView(label(activity.getString(R.string.calendar_all_day_setting,
                activity.getString(if (allDay) R.string.calendar_yes else R.string.calendar_no)), action = {
                allDay = !allDay
                if (!allDay) { startZone = ZoneId.systemDefault(); endZone = startZone }
                applyRange(editorRange().ordered())
                makeEditor(); render()
            }))
            val dates = CalendarDates(activity, settings().dateSystem)
            if (dates.supported) {
                addView(label(activity.getString(R.string.calendar_start_date, dates.format(startDate)), action = {
                    pickDate(startDate) {
                        applyRange(editorRange().startingOn(it, original == null))
                        makeEditor(); render()
                    }
                }))
                addView(label(activity.getString(R.string.calendar_end_date, dates.format(endDate)), action = {
                    pickDate(endDate) {
                        applyRange(editorRange().endingOn(it))
                        makeEditor(); render()
                    }
                }))
            }
            if (!allDay) {
                fun minuteText(minute: Int): String {
                    val locale = activity.resources.configuration.locales[0]
                    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(activity)) "Hm" else "hm")
                    return java.time.LocalTime.of(minute / 60, minute % 60)
                        .format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
                }
                addView(label(activity.getString(R.string.calendar_start_time, minuteText(startMinute)), action = {
                    pickTime(startMinute) {
                        applyRange(editorRange().startingAt(it, original == null))
                        makeEditor(); render()
                    }
                }))
                addView(label(activity.getString(R.string.calendar_end_time, minuteText(endMinute)), action = {
                    pickTime(endMinute) {
                        applyRange(editorRange().endingAt(it))
                        makeEditor(); render()
                    }
                }))
                if (startZone != ZoneId.systemDefault() || endZone != startZone) {
                    addView(label(activity.getString(R.string.calendar_zone, startZone.id, endZone.id)))
                }
            }
            addView(label(activity.getString(R.string.task_save), R.id.calendar_save, action = ::save).apply { isEnabled = pending == null })
            addView(label(activity.getString(R.string.task_discard), action = { requestLeave { closeEditor(); render() } }))
        }
    }

    private fun editorRange() = CalendarEditorRange(
        startDate.atTime(startMinute / 60, startMinute % 60),
        endDate.atTime(endMinute / 60, endMinute % 60), allDay, startZone, endZone,
    )

    private fun applyRange(range: CalendarEditorRange) {
        val adjusted = range.end.toLocalDate() != endDate ||
            range.end.hour * 60 + range.end.minute != endMinute
        startDate = range.start.toLocalDate()
        startMinute = range.start.hour * 60 + range.start.minute
        endDate = range.end.toLocalDate()
        endMinute = range.end.hour * 60 + range.end.minute
        if (adjusted) Toast.makeText(activity, R.string.calendar_end_adjusted, Toast.LENGTH_SHORT).show()
        writeStatus = 0
    }

    private fun input(hint: Int, id: Int, value: String, multiline: Boolean = false, changed: (String) -> Unit): EditText =
        EditText(activity).apply {
            this.id = id
            isSaveEnabled = false
            setHint(hint)
            setTextColor(activity.appearanceColor(R.color.foreground))
            setHintTextColor(activity.appearanceColor(R.color.foreground_muted))
            inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minHeight = activity.dp(56)
            setText(value)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s.toString()) }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

    private fun pickDate(date: LocalDate, accept: (LocalDate) -> Unit) {
        val dates = CalendarDates(activity, settings().dateSystem)
        var month = date
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun renderMonth() {
            list.removeAllViews()
            list.addView(label(dates.format(month, "MMMMyyyyG"), R.id.calendar_heading, true))
            fun navigation(previous: Int, next: Int, field: Int) {
                list.addView(LinearLayout(activity).apply {
                    listOf(previous to -1, next to 1).forEach { (resource, amount) ->
                        addView(label(activity.getString(resource), action = {
                            month = dates.move(month, field, amount)
                            renderMonth()
                        }), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                })
            }
            navigation(R.string.calendar_previous_year, R.string.calendar_next_year, Calendar.YEAR)
            navigation(R.string.calendar_previous_month, R.string.calendar_next_month, Calendar.MONTH)
            dates.monthDays(month).forEach { day ->
                list.addView(label(dates.format(day), R.id.calendar_date_choice, action = {
                    picker?.dismiss()
                    accept(day)
                }))
            }
        }
        renderMonth()
        picker?.dismiss()
        picker = AlertDialog.Builder(activity).setView(ScrollView(activity).apply { addView(list) })
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun pickTime(minute: Int, accept: (Int) -> Unit) {
        picker?.dismiss()
        picker = TimePickerDialog(activity, { _, hour, minutes -> accept(hour * 60 + minutes) },
            minute / 60, minute % 60, DateFormat.is24HourFormat(activity)).apply { show() }
    }

    private fun save() {
        if (pending != null) return
        if (!repository.canRead()) { refresh(); return }
        if (!repository.canWrite()) { allowWrite(); return }
        if (title.isBlank()) { writeStatus = R.string.task_title_required; render(); return }
        val source = draftSource ?: return
        if (sources.none { it.identity == source } ||
            settings().eventCalendars?.let { source !in it } == true ||
            original == null && defaultWriteCalendar(sources, settings())?.identity != source) {
            writeStatus = R.string.calendar_conflict; render(); return
        }
        if (allDay) {
            submit(source, startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        } else resolveTime(startDate, startMinute, startZone, original?.start) { start ->
            resolveTime(endDate, endMinute, endZone, original?.end) { end ->
                val orderedEnd = if (end <= start) start + 3_600_000 else end
                if (orderedEnd != end) {
                    val adjusted = Instant.ofEpochMilli(orderedEnd).atZone(endZone)
                    applyRange(editorRange().copy(end = adjusted.toLocalDateTime()))
                    makeEditor(); render()
                }
                submit(source, start, orderedEnd)
            }
        }
    }

    private fun resolveTime(date: LocalDate, minute: Int, zone: ZoneId, old: Long?, accept: (Long) -> Unit) {
        val instants = calendarLocalTime(date, minute / 60, minute % 60, zone)
        val previous = old?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val unchanged = previous != null && previous.toLocalDate() == date && previous.hour * 60 + previous.minute == minute
        when {
            unchanged -> accept(requireNotNull(old))
            instants.isEmpty() -> { writeStatus = R.string.calendar_invalid_time; render() }
            instants.size == 1 -> accept(instants.single().toEpochMilli())
            else -> {
                dialog?.dismiss()
                val labels = instants.map { instant ->
                    "${time(instant.toEpochMilli())} ${instant.atZone(zone).offset}"
                }.toTypedArray()
                dialog = AlertDialog.Builder(activity).setTitle(R.string.calendar_ambiguous_time)
                    .setItems(labels) { _, which -> accept(instants[which].toEpochMilli()) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
    }

    private fun submit(source: CalendarIdentity, start: Long, end: Long) {
        val record = CalendarEvent(original?.id ?: 0, source.id, title, note.takeIf(String::isNotEmpty),
            location.takeIf(String::isNotEmpty), start, end, allDay,
            if (allDay) "UTC" else startZone.id,
            if (allDay || endZone == startZone && original?.endTimeZone == null) null else endZone.id,
            null, null, null)
        if (!validCalendarEvent(record)) { writeStatus = R.string.calendar_invalid_range; render(); return }
        val baseline = original
        val request = UUID.randomUUID().toString()
        pending = request
        writeStatus = R.string.settings_saving
        makeEditor()
        render()
        calendarWorker.execute {
            val result = repository.save(source, baseline, record)
            synchronized(writes) {
                writes[request] = result
                if (writes.size > 64) writes.remove(writes.keys.first())
            }
            handler.post {
                if (active) {
                    settleSave(request, result)
                    refresh()
                }
            }
        }
    }

    private fun settleSave(request: String, result: CalendarSave) {
        if (pending != request) return
        writeStatus = when (result) {
            is CalendarSave.Saved -> {
                val outside = startDate > LocalDate.now().plusDays(14) || endDate < LocalDate.now()
                closeEditor()
                selected = null
                detail = null
                Toast.makeText(activity, if (outside) R.string.calendar_saved_outside else R.string.calendar_saved, Toast.LENGTH_LONG).show()
                0
            }
            CalendarSave.Conflict -> R.string.calendar_conflict
            CalendarSave.Denied -> R.string.calendar_allow_write
            CalendarSave.ReadOnly -> R.string.calendar_read_only
            CalendarSave.Invalid -> R.string.calendar_invalid_range
            CalendarSave.Unavailable -> R.string.calendar_save_failed
            CalendarSave.Uncertain -> R.string.calendar_save_uncertain
        }
        if (result != CalendarSave.Uncertain) pending = null
        if (editor != null) makeEditor()
    }

    private fun closeEditor() {
        editor = null
        original = null
        draftSource = null
        initialDraft = null
        title = ""; note = ""; location = ""
    }

    private fun draftBundle() = Bundle().apply {
        putString("title", title); putString("note", note); putString("location", location)
        putLong("start_date", startDate.toEpochDay()); putLong("end_date", endDate.toEpochDay())
        putInt("start_minute", startMinute); putInt("end_minute", endMinute)
        putBoolean("all_day", allDay); putString("start_zone", startZone.id); putString("end_zone", endZone.id)
    }

    private fun restoreDraft(bundle: Bundle) {
        title = bundle.getString("title").orEmpty(); note = bundle.getString("note").orEmpty()
        location = bundle.getString("location").orEmpty()
        startDate = LocalDate.ofEpochDay(bundle.getLong("start_date")); endDate = LocalDate.ofEpochDay(bundle.getLong("end_date"))
        startMinute = bundle.getInt("start_minute"); endMinute = bundle.getInt("end_minute")
        allDay = bundle.getBoolean("all_day")
        startZone = ZoneId.of(requireNotNull(bundle.getString("start_zone")))
        endZone = ZoneId.of(requireNotNull(bundle.getString("end_zone")))
    }

    fun saveState(state: Bundle) {
        state.putInt("calendar_scroll", view.scrollY)
        state.putString("calendar_pending", pending)
        if (!repository.canRead()) return
        selected?.let { occurrence ->
            state.putBundle("calendar_selected", Bundle().apply {
                putLong("id", occurrence.eventId); putString("title", occurrence.title)
                putLong("begin", occurrence.begin); putLong("end", occurrence.end); putBoolean("all_day", occurrence.allDay)
            })
        }
        state.putBoolean("calendar_editing", editor != null)
        if (editor != null) {
            state.putBundle("calendar_draft", draftBundle())
            state.putBundle("calendar_initial", initialDraft)
            original?.let { state.putBundle("calendar_original", writeEvent(it)) }
            draftSource?.let { source ->
                state.putBundle("calendar_source", Bundle().apply {
                    putLong("id", source.id); putString("account", source.account); putString("type", source.accountType)
                })
            }
        }
    }

    companion object {
        private const val TAG = "BareShellCalendar"
        private val writes = linkedMapOf<String, CalendarSave>()
        private fun readIdentity(state: Bundle) =
            CalendarIdentity(state.getLong("id"), state.getString("account").orEmpty(), state.getString("type").orEmpty())
        private fun writeEvent(event: CalendarEvent) = Bundle().apply {
            putLong("id", event.id); putLong("calendar", event.calendarId); putString("title", event.title)
            putString("note", event.note); putString("location", event.location); putLong("start", event.start); putLong("end", event.end)
            putBoolean("all_day", event.allDay); putString("zone", event.timeZone); putString("end_zone", event.endTimeZone)
            putString("recurrence", event.recurrence); putString("recurrence_dates", event.recurrenceDates)
            event.originalId?.let { putLong("original_id", it) }
        }
        private fun readEvent(state: Bundle) = CalendarEvent(state.getLong("id"), state.getLong("calendar"),
            state.getString("title").orEmpty(), state.getString("note"), state.getString("location"),
            state.getLong("start"), state.getLong("end"), state.getBoolean("all_day"), state.getString("zone").orEmpty(),
            state.getString("end_zone"), state.getString("recurrence"), state.getString("recurrence_dates"),
            if (state.containsKey("original_id")) state.getLong("original_id") else null)
    }
}
