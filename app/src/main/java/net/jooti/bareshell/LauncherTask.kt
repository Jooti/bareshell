package net.jooti.bareshell

import java.time.LocalDate
import java.util.UUID

internal enum class TaskStatus { OPEN, FINISHED, CANCELED }
internal enum class TaskSection { DOING, POSTPONED, FINISHED, CANCELED }
internal enum class TaskAction { SAVE, FINISH, CANCEL, POSTPONE, REOPEN }

internal data class LauncherTask(
    val id: String,
    val title: String,
    val note: String,
    val date: LocalDate,
    val originalDate: LocalDate,
    val status: TaskStatus = TaskStatus.OPEN,
    val changedAt: Long,
    val revision: Long = 0,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(changedAt >= 0 && revision >= 0)
    }

    fun section(today: LocalDate): TaskSection = when (status) {
        TaskStatus.OPEN -> if (date > today) TaskSection.POSTPONED else TaskSection.DOING
        TaskStatus.FINISHED -> TaskSection.FINISHED
        TaskStatus.CANCELED -> TaskSection.CANCELED
    }

    fun change(
        title: String,
        note: String,
        action: TaskAction,
        today: LocalDate,
        postponedDate: LocalDate? = null,
        now: Long = System.currentTimeMillis(),
    ): LauncherTask {
        require(title.isNotBlank())
        val nextStatus = when (action) {
            TaskAction.SAVE -> status
            TaskAction.FINISH -> { require(status == TaskStatus.OPEN); TaskStatus.FINISHED }
            TaskAction.CANCEL -> { require(status == TaskStatus.OPEN); TaskStatus.CANCELED }
            TaskAction.REOPEN -> { require(status != TaskStatus.OPEN); TaskStatus.OPEN }
            TaskAction.POSTPONE -> { require(status == TaskStatus.OPEN); TaskStatus.OPEN }
        }
        val nextDate = if (action == TaskAction.POSTPONE) {
            requireNotNull(postponedDate).also { require(it > today) }
        } else {
            require(postponedDate == null)
            date
        }
        return copy(
            title = title, note = note, status = nextStatus, date = nextDate,
            changedAt = if (nextStatus != status) now else changedAt,
            revision = Math.addExact(revision, 1),
        )
    }

    companion object {
        fun create(title: String, note: String, date: LocalDate, now: Long = System.currentTimeMillis()) =
            LauncherTask(UUID.randomUUID().toString(), title, note, date, date, changedAt = now)
    }
}

internal fun taskSections(tasks: List<LauncherTask>, today: LocalDate): Map<TaskSection, List<LauncherTask>> =
    TaskSection.entries.associateWith { section ->
        val matching = tasks.filter { it.section(today) == section }
        matching.sortedWith(
            if (section == TaskSection.DOING || section == TaskSection.POSTPONED) {
                compareBy<LauncherTask> { it.date }.thenBy { it.id }
            } else {
                compareByDescending<LauncherTask> { it.changedAt }.thenBy { it.id }
            },
        )
    }
