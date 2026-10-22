package net.jooti.bareshell

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.Executors

internal class TasksPage(private val context: Context, state: Bundle? = null) {
    private val store = TaskStore(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val view = ScrollView(context).apply {
        id = R.id.tasks_page
        isSaveEnabled = false
        isFillViewport = true
        addView(body)
    }
    private var active = true
    private var loading = false
    private var loaded = cachedRecords != null
    private var loadError = false
    private var saveError = false
    private var saving = false
    private var pending: LauncherTask? = state?.getBundle("tasks_pending")?.let(::readTask)
    private var pendingAction = state?.getString("tasks_pending_action")?.let(TaskAction::valueOf)
    private var records = cachedRecords.orEmpty()
    private var selectedId = state?.getString("tasks_selected")
    private val expandedSections = mutableSetOf<TaskSection>()
    private var original: LauncherTask? = null
    private var editor: LinearLayout? = null
    private var titleInput: EditText? = null
    private var noteInput: EditText? = null
    private var dateLabel: TextView? = null
    private var draftTitle = ""
    private var draftNote = ""
    private var draftDate = LocalDate.now()
    private var initialDate = draftDate
    private var dialog: AlertDialog? = null
    private var dateDialog: DatePickerDialog? = null
    private var restoredScroll = state?.getInt("tasks_scroll")

    init {
        state?.getStringArrayList("tasks_sections")?.forEach { name ->
            TaskSection.entries.firstOrNull { it.name == name }?.let(expandedSections::add)
        }
        if (state?.getBoolean("tasks_editing") == true) {
            original = state.getBundle("tasks_original")?.let(::readTask)
            draftTitle = state.getString("tasks_title").orEmpty()
            draftNote = state.getString("tasks_note").orEmpty()
            draftDate = LocalDate.ofEpochDay(state.getLong("tasks_date"))
            initialDate = LocalDate.ofEpochDay(state.getLong("tasks_initial_date"))
            makeEditor()
        }
        render()
        refresh()
    }

    private val dirty: Boolean
        get() = if (original == null) {
            draftTitle.isNotEmpty() || draftNote.isNotEmpty() || draftDate != initialDate
        } else {
            draftTitle != original?.title || draftNote != original?.note
        }

    fun requestLeave(action: () -> Unit) {
        if (saving || (pending != null && loading)) {
            Toast.makeText(context, R.string.settings_saving, Toast.LENGTH_SHORT).show()
            return
        }
        if (pending == null && (editor == null || !dirty)) {
            action()
        } else {
            dialog?.dismiss()
            dialog = AlertDialog.Builder(context)
                .setMessage(if (pending == null) R.string.task_discard_question else R.string.task_save_uncertain)
                .setPositiveButton(R.string.task_discard) { _, _ -> closeEditor(); action() }
                .setNegativeButton(R.string.task_keep_editing, null)
                .show()
        }
    }

    fun back(): Boolean {
        if (editor == null && selectedId == null) return false
        requestLeave {
            if (editor != null) closeEditor() else selectedId = null
            render()
        }
        return true
    }

    fun refresh() {
        if (!active || loading || saving) return
        loading = true
        worker.execute {
            val result = try {
                store.load()
            } catch (error: SQLiteException) {
                Log.e(TAG, "Cannot load tasks", error)
                null
            } catch (error: SecurityException) {
                Log.e(TAG, "Task storage access denied", error)
                null
            }
            handler.post {
                if (active) {
                    loading = false
                    if (result == null && !loadError) {
                        Toast.makeText(context, R.string.tasks_load_failed, Toast.LENGTH_LONG).show()
                    }
                    loadError = result == null
                    if (result != null) {
                        records = result
                        cachedRecords = result
                        loaded = true
                        val submitted = pending
                        if (submitted != null) {
                            val saved = result.firstOrNull { it.id == submitted.id && it.revision == submitted.revision }
                            if (saved != null) {
                                completeSave(saved, requireNotNull(pendingAction))
                            } else saveError = true
                            pending = null
                            pendingAction = null
                        }
                        val current = original
                        val latest = result.firstOrNull { it.id == current?.id }
                        if (latest != null && (!dirty ||
                                (draftTitle == latest.title && draftNote == latest.note))) {
                            val rebuild = latest != current
                            original = latest
                            draftTitle = latest.title
                            draftNote = latest.note
                            draftDate = latest.date
                            if (rebuild) makeEditor()
                        }
                        if (selectedId != null && result.none { it.id == selectedId }) selectedId = null
                    }
                    render()
                }
            }
        }
    }

    fun saveState(state: Bundle) {
        state.putString("tasks_selected", selectedId)
        state.putInt("tasks_scroll", view.scrollY)
        state.putStringArrayList("tasks_sections", ArrayList(expandedSections.map { it.name }))
        state.putBoolean("tasks_editing", editor != null)
        pending?.let { state.putBundle("tasks_pending", writeTask(it)) }
        pendingAction?.let { state.putString("tasks_pending_action", it.name) }
        if (editor != null) {
            original?.let { state.putBundle("tasks_original", writeTask(it)) }
            state.putString("tasks_title", draftTitle)
            state.putString("tasks_note", draftNote)
            state.putLong("tasks_date", draftDate.toEpochDay())
            state.putLong("tasks_initial_date", initialDate.toEpochDay())
        }
    }

    fun dispose() {
        active = false
        TransitionManager.endTransitions(body)
        dialog?.dismiss()
        dateDialog?.dismiss()
        worker.execute { store.close() }
    }

    private fun render(animate: Boolean = false) {
        val scroll = restoredScroll ?: view.scrollY
        restoredScroll = null
        val focused = when {
            titleInput?.hasFocus() == true -> titleInput
            noteInput?.hasFocus() == true -> noteInput
            else -> null
        }
        TransitionManager.endTransitions(body)
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            TransitionManager.beginDelayedTransition(body, AutoTransition().setDuration(200))
        }
        (editor?.parent as? ViewGroup)?.removeView(editor)
        body.removeAllViews()
        label(body, R.string.task_add, R.id.task_add).onTextClick {
            requestLeave {
                closeEditor()
                selectedId = null
                draftTitle = ""
                draftNote = ""
                draftDate = LocalDate.now()
                initialDate = draftDate
                makeEditor()
                render(true)
            }
        }
        if (loadError) label(body, R.string.tasks_load_failed, R.id.status_message).onTextClick { refresh() }
        if (!loaded && !loadError) label(body, R.string.tasks_loading, R.id.status_message)
        if (saveError) label(body, if (editor != null) R.string.tasks_save_failed else R.string.task_action_failed,
            R.id.status_message).onTextClick { refresh() }
        if (saving) label(body, R.string.settings_saving, R.id.status_message)
        if (editor != null && original == null) body.addView(editor)
        val sections = taskSections(records, LocalDate.now())
        TaskSection.entries.forEach { section ->
            val tasks = sections.getValue(section)
            val open = section == TaskSection.DOING || section in expandedSections ||
                tasks.any { it.id == selectedId }
            val heading = context.textLabel(
                context.getString(R.string.task_section_count, context.getString(section.labelRes), tasks.size),
                R.id.section_heading, primary = section == TaskSection.DOING,
            ).apply {
                transitionName = "section:${section.name}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
                if (section != TaskSection.DOING) {
                    contentDescription = context.getString(
                        if (open) R.string.contact_expanded else R.string.contact_collapsed, text,
                    )
                    onTextClick {
                        requestLeave {
                            if (open) {
                                expandedSections.remove(section)
                                if (tasks.any { it.id == selectedId }) {
                                    closeEditor()
                                    selectedId = null
                                }
                            } else expandedSections.add(section)
                            render(true)
                        }
                    }
                }
            }
            body.addView(heading)
            if (open) {
                if (tasks.isEmpty() && loaded) label(body, R.string.tasks_empty, R.id.status_message)
                tasks.forEach { task ->
                    val selected = selectedId == task.id
                    body.addView(context.textLabel(task.title, R.id.task_row, primary = true).apply {
                        transitionName = "task:${task.id}"
                        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                        if ((selectedId != null || editor != null) && !selected) setTextColor(context.appearanceColor(R.color.foreground_muted))
                        contentDescription = context.getString(
                            if (selected) R.string.contact_expanded else R.string.contact_collapsed, task.title,
                        )
                        onTextClick {
                            requestLeave {
                                closeEditor()
                                selectedId = if (selected) null else task.id
                                render(true)
                            }
                        }
                    })
                    if (selected) {
                        if (editor != null && original?.id == task.id) body.addView(editor)
                        else body.addView(details(task))
                    }
                    else if (task.date != LocalDate.now() || task.date != task.originalDate) {
                        body.addView(context.textLabel(
                            context.getString(R.string.task_date, format(task.date)), R.id.task_date,
                        ).apply {
                            transitionName = "date:${task.id}"
                            setTextColor(context.appearanceColor(R.color.foreground_muted))
                        })
                    }
                }
            }
        }
        setEditorEnabled(!saving && pending == null)
        focused?.requestFocus()
        view.post { if (active) view.scrollTo(0, scroll) }
    }

    private fun label(parent: LinearLayout, resource: Int, id: Int): TextView =
        context.textLabel(context.getString(resource), id).also {
            if (id == R.id.status_message) it.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            parent.addView(it)
        }

    private fun details(task: LauncherTask): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(context.dp(24), 0, 0, 0)
        val actions = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val desired = (0 until childCount).sumOf {
                    val label = getChildAt(it) as TextView
                    ceil(label.paint.measureText(label.text.toString())).toInt() +
                        label.totalPaddingLeft + label.totalPaddingRight
                }
                orientation = if (desired <= MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) {
                    HORIZONTAL
                } else VERTICAL
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        fun action(resource: Int, action: TaskAction) {
            actions.addView(context.textLabel(context.getString(resource), R.id.task_action).apply {
                isEnabled = !saving && pending == null
                onTextClick {
                    if (action == TaskAction.POSTPONE) {
                        pickDate(true, task.date) { date -> submit(task, task.title, task.note, action, date) }
                    } else submit(task, task.title, task.note, action)
                }
            })
        }
        if (task.status == TaskStatus.OPEN) {
            action(R.string.task_finish, TaskAction.FINISH)
            action(R.string.task_postpone, TaskAction.POSTPONE)
            action(R.string.task_cancel, TaskAction.CANCEL)
        } else action(R.string.task_reopen, TaskAction.REOPEN)
        addView(actions)
        label(this, R.string.task_edit, R.id.task_action).apply {
            isEnabled = !saving && pending == null
            onTextClick {
                original = task
                draftTitle = task.title
                draftNote = task.note
                draftDate = task.date
                initialDate = task.date
                saveError = false
                makeEditor()
                render(true)
            }
        }
        if (task.note.isNotEmpty()) addView(context.textLabel(task.note, R.id.task_note_display).apply {
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        })
        addView(context.textLabel(context.getString(R.string.task_date, format(task.date)), R.id.task_date))
        addView(context.textLabel(context.getString(R.string.task_original_date, format(task.originalDate)), R.id.task_original_date))
    }

    private fun makeEditor() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            transitionName = "editor:${original?.id ?: "new"}"
            setPaddingRelative(context.dp(24), 0, 0, 0)
        }
        editor = panel
        fun input(hint: Int, id: Int, value: String, multiline: Boolean, update: (String) -> Unit): EditText =
            EditText(context).apply {
                this.id = id
                isSaveEnabled = false
                setHint(hint)
                contentDescription = context.getString(hint)
                setTextColor(context.appearanceColor(R.color.foreground))
                setHintTextColor(context.appearanceColor(R.color.foreground_muted))
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                textSize = if (multiline) 16f else 24f
                minHeight = context.dp(56)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
                setSingleLine(!multiline)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setText(value)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) { update(s.toString()) }
                })
                panel.addView(this)
            }
        titleInput = input(R.string.task_title, R.id.task_title, draftTitle, false) { draftTitle = it }
        noteInput = input(R.string.task_note, R.id.task_note, draftNote, true) { draftNote = it }
        dateLabel = context.textLabel(context.getString(R.string.task_date, format(draftDate)), R.id.task_date).also {
            panel.addView(it)
            if (original == null) it.onTextClick { pickDate(false) { date ->
                draftDate = date
                dateLabel?.text = context.getString(R.string.task_date, format(date))
            } }
        }
        original?.let {
            panel.addView(context.textLabel(
                context.getString(R.string.task_original_date, format(it.originalDate)), R.id.task_original_date,
            ))
        }
        label(panel, R.string.task_save, R.id.task_action).onTextClick {
            if (validTitle()) submit(original, draftTitle, draftNote, TaskAction.SAVE)
        }
        label(panel, R.string.task_discard, R.id.task_action).onTextClick {
            requestLeave { closeEditor(); render(true) }
        }
    }

    private fun pickDate(futureOnly: Boolean, date: LocalDate = draftDate, accept: (LocalDate) -> Unit) {
        val selected = if (futureOnly) maxOf(date, LocalDate.now().plusDays(1)) else date
        dateDialog?.dismiss()
        dateDialog = DatePickerDialog(context, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            if (futureOnly && date <= LocalDate.now()) {
                Toast.makeText(context, R.string.task_future_required, Toast.LENGTH_LONG).show()
            } else accept(date)
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply { show() }
    }

    private fun validTitle(): Boolean {
        if (draftTitle.isNotBlank()) return true
        titleInput?.error = context.getString(R.string.task_title_required)
        titleInput?.requestFocus()
        return false
    }

    private fun submit(previous: LauncherTask?, title: String, note: String, action: TaskAction, postponedDate: LocalDate? = null) {
        if (saving || pending != null) return
        val next = if (previous == null) {
            LauncherTask.create(title, note, draftDate)
        } else {
            if (action == TaskAction.POSTPONE && (postponedDate == null || postponedDate <= LocalDate.now())) {
                Toast.makeText(context, R.string.task_future_required, Toast.LENGTH_LONG).show()
                return
            }
            previous.change(title, note, action, LocalDate.now(), postponedDate)
        }
        saving = true
        pending = next
        pendingAction = action
        saveError = false
        render()
        worker.execute {
            val success = try {
                store.save(next, previous)
                true
            } catch (error: SQLiteException) {
                Log.e(TAG, "Cannot save task", error)
                false
            } catch (error: SecurityException) {
                Log.e(TAG, "Task write access denied", error)
                false
            }
            handler.post {
                if (active) {
                    saving = false
                    pending = null
                    pendingAction = null
                    saveError = !success
                    if (success) {
                        records = records.filterNot { it.id == next.id } + next
                        cachedRecords = records
                        completeSave(next, action)
                        Toast.makeText(context, when (action) {
                            TaskAction.SAVE -> R.string.settings_saved
                            TaskAction.FINISH -> R.string.task_finished_message
                            TaskAction.CANCEL -> R.string.task_canceled_message
                            TaskAction.POSTPONE -> R.string.task_postponed_message
                            TaskAction.REOPEN -> R.string.task_reopened_message
                        }, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (editor != null) R.string.tasks_save_failed else R.string.task_action_failed,
                            Toast.LENGTH_LONG).show()
                    }
                    render(true)
                }
            }
        }
    }

    private fun completeSave(task: LauncherTask, action: TaskAction) {
        closeEditor()
        selectedId = if (action == TaskAction.SAVE) task.id else null
    }

    private fun setEditorEnabled(enabled: Boolean) {
        val panel = editor ?: return
        for (index in 0 until panel.childCount) panel.getChildAt(index).isEnabled = enabled
        panel.getChildAt(panel.childCount - 1).isEnabled = !saving
    }

    private fun closeEditor() {
        (editor?.parent as? ViewGroup)?.removeView(editor)
        editor = null
        titleInput = null
        noteInput = null
        original = null
        if (!saving) {
            pending = null
            pendingAction = null
        }
        saveError = false
    }

    private fun format(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    private fun writeTask(task: LauncherTask) = Bundle().apply {
        putString("id", task.id)
        putString("title", task.title)
        putString("note", task.note)
        putLong("date", task.date.toEpochDay())
        putLong("original", task.originalDate.toEpochDay())
        putString("status", task.status.name)
        putLong("changed", task.changedAt)
        putLong("revision", task.revision)
    }

    private fun readTask(bundle: Bundle) = LauncherTask(
        requireNotNull(bundle.getString("id")), requireNotNull(bundle.getString("title")),
        bundle.getString("note").orEmpty(), LocalDate.ofEpochDay(bundle.getLong("date")),
        LocalDate.ofEpochDay(bundle.getLong("original")), TaskStatus.valueOf(bundle.getString("status").orEmpty()),
        bundle.getLong("changed"), bundle.getLong("revision"),
    )

    companion object {
        private const val TAG = "BareShellTasks"
        // Shared ordering keeps recreation reads behind writes from the old page.
        private val worker = Executors.newSingleThreadExecutor()
        private var cachedRecords: List<LauncherTask>? = null
    }
}

private val TaskSection.labelRes: Int
    get() = when (this) {
        TaskSection.DOING -> R.string.tasks_doing
        TaskSection.POSTPONED -> R.string.tasks_postponed
        TaskSection.FINISHED -> R.string.tasks_finished
        TaskSection.CANCELED -> R.string.tasks_canceled
    }
