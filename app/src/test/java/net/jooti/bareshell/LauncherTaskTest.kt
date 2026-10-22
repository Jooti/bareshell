package net.jooti.bareshell

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTaskTest {
    private val today = LocalDate.of(2026, 10, 21)
    private fun task(date: LocalDate = today) = LauncherTask.create("Task", "", date, 1)

    @Test fun newTasksKeepTheirOriginalDateAndStartOpen() {
        listOf(today.minusDays(1), today, today.plusDays(1)).forEach { date ->
            val task = task(date)
            assertEquals(date, task.originalDate)
            assertEquals(date, task.date)
            assertEquals(TaskStatus.OPEN, task.status)
        }
    }

    @Test fun todayAndOverdueAreDoingButFutureIsPostponed() {
        assertEquals(TaskSection.DOING, task(today.minusYears(1)).section(today))
        assertEquals(TaskSection.DOING, task().section(today))
        val future = task(today.plusDays(1))
        assertEquals(TaskSection.POSTPONED, future.section(today))
        assertEquals(TaskSection.DOING, future.section(today.plusDays(1)))
    }

    @Test fun repeatedPostponementPreservesOriginalDateAndEdits() {
        val first = task().change("Updated", "Note", TaskAction.POSTPONE, today, today.plusDays(1))
        val next = first.change("Updated again", "Note\n2", TaskAction.POSTPONE, today, today.plusDays(7))
        assertEquals(today, next.originalDate)
        assertEquals(today.plusDays(7), next.date)
        assertEquals("Note\n2", next.note)
        assertEquals(2L, next.revision)
    }

    @Test fun noteOnlySaveDoesNotChangeDatesStatusOrHistoryOrder() {
        val old = task()
        val saved = old.change(old.title, "یادداشت\nEnglish", TaskAction.SAVE, today, now = 9)
        assertEquals(old.date, saved.date)
        assertEquals(old.originalDate, saved.originalDate)
        assertEquals(old.status, saved.status)
        assertEquals(old.changedAt, saved.changedAt)
        assertEquals("یادداشت\nEnglish", saved.note)
    }

    @Test fun quickTransitionsKeepSavedTextAndOriginalDatesFromEitherOpenSection() {
        listOf(today.minusDays(1), today, today.plusDays(3)).forEach { date ->
            val saved = LauncherTask.create("Saved title", "Saved note\nSecond line", date, 1)
            listOf(TaskAction.FINISH, TaskAction.CANCEL, TaskAction.POSTPONE).forEach { action ->
                val moved = saved.change(saved.title, saved.note, action, today,
                    if (action == TaskAction.POSTPONE) today.plusDays(7) else null, now = 5)
                assertEquals(saved.title, moved.title)
                assertEquals(saved.note, moved.note)
                assertEquals(saved.originalDate, moved.originalDate)
                assertEquals(1L, moved.revision)
                assertEquals(if (action == TaskAction.POSTPONE) today.plusDays(7) else date, moved.date)
                if (action != TaskAction.POSTPONE) {
                    val reopened = moved.change(moved.title, moved.note, TaskAction.REOPEN, today, now = 9)
                    assertEquals(saved.title, reopened.title)
                    assertEquals(saved.note, reopened.note)
                    assertEquals(saved.date, reopened.date)
                    assertEquals(saved.originalDate, reopened.originalDate)
                    assertEquals(saved.section(today), reopened.section(today))
                    assertEquals(2L, reopened.revision)
                }
            }
        }
    }

    @Test fun finishedAndCanceledDoNotAutomaticallyReenterDoing() {
        listOf(TaskAction.FINISH to TaskSection.FINISHED, TaskAction.CANCEL to TaskSection.CANCELED).forEach { (action, section) ->
            val closed = task().change("عنوان", "Note", action, today, now = 5)
            assertEquals(section, closed.section(today.plusYears(1)))
            val edited = closed.change("عنوان", "Edited", TaskAction.SAVE, today, now = 8)
            assertEquals(5L, edited.changedAt)
            assertEquals(section, edited.section(today))
        }
    }

    @Test fun reopeningKeepsScheduledDateAndUsesDateGrouping() {
        listOf(today.minusDays(1), today, today.plusDays(1)).forEach { date ->
            listOf(TaskAction.FINISH, TaskAction.CANCEL).forEach { action ->
                val reopened = task(date).change("Task", "", action, today)
                    .change("Task", "Reopened", TaskAction.REOPEN, today)
                assertEquals(date, reopened.date)
                assertEquals(date, reopened.originalDate)
                assertEquals(task(date).section(today), reopened.section(today))
            }
        }
    }

    @Test fun groupingKeepsFixedSectionOrderAndDeterministicDateOrder() {
        val a = task(today.plusDays(3)).copy(id = "a")
        val b = task(today.minusDays(2)).copy(id = "b")
        val c = task().copy(id = "c")
        val d = task(today.plusDays(1)).copy(id = "d")
        val sections = taskSections(listOf(a, c, d, b), today)
        assertEquals(TaskSection.entries.toList(), sections.keys.toList())
        assertEquals(listOf(b, c), sections[TaskSection.DOING])
        assertEquals(listOf(d, a), sections[TaskSection.POSTPONED])
        assertTrue(sections.getValue(TaskSection.FINISHED).isEmpty())
    }

    @Test fun historySortsByTransitionNotNoteEditAndBreaksTiesByIdentity() {
        val a = task().copy(id = "a").change("Task", "", TaskAction.FINISH, today, now = 5)
        val b = task().copy(id = "b").change("Task", "", TaskAction.FINISH, today, now = 9)
        val c = task().copy(id = "c").change("Task", "", TaskAction.FINISH, today, now = 9)
        val edited = a.change("Task", "Edited", TaskAction.SAVE, today, now = 20)
        assertEquals(listOf(b, c, edited), taskSections(listOf(c, edited, b), today)[TaskSection.FINISHED])
    }

    @Test fun rtlAndLtrTitlesAndNotesArePreserved() {
        listOf("عنوان فارسی", "English title", "عنوان English ۱۲۳").forEach { title ->
            val task = LauncherTask.create(title, "سطر اول\nSecond line", today)
            assertEquals(title, task.title)
            assertEquals("سطر اول\nSecond line", task.note)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTitleIsRejected() { LauncherTask.create(" \n\t", "", today) }

    @Test(expected = IllegalArgumentException::class)
    fun blankEditedTitleIsRejected() { task().change(" ", "", TaskAction.SAVE, today) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotPostponeToToday() { task().change("Task", "", TaskAction.POSTPONE, today, today) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotPostponeToPast() { task().change("Task", "", TaskAction.POSTPONE, today, today.minusDays(1)) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotPostponeWithoutDate() { task().change("Task", "", TaskAction.POSTPONE, today) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotPostponeClosedTask() {
        task().change("Task", "", TaskAction.FINISH, today)
            .change("Task", "", TaskAction.POSTPONE, today, today.plusDays(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotReopenOpenTask() { task().change("Task", "", TaskAction.REOPEN, today) }
}
