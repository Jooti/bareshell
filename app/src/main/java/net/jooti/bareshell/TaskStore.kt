package net.jooti.bareshell

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.time.DateTimeException
import java.time.LocalDate

internal class TaskStore(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tasks (
                id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL,
                scheduled_day INTEGER NOT NULL, original_day INTEGER NOT NULL,
                status TEXT NOT NULL, changed_at INTEGER NOT NULL, revision INTEGER NOT NULL
            )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("Unsupported task database version")
    }

    fun load(): List<LauncherTask> = readableDatabase.query(
        "tasks", arrayOf("id", "title", "note", "scheduled_day", "original_day", "status", "changed_at", "revision"),
        null, null, null, null, null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                try {
                    if ((0..7).any { cursor.isNull(it) }) throw SQLiteException("Null task field")
                    add(LauncherTask(
                        cursor.getString(0), cursor.getString(1), cursor.getString(2),
                        LocalDate.ofEpochDay(cursor.getLong(3)), LocalDate.ofEpochDay(cursor.getLong(4)),
                        TaskStatus.valueOf(cursor.getString(5)), cursor.getLong(6), cursor.getLong(7),
                    ))
                } catch (error: IllegalArgumentException) {
                    throw SQLiteException("Invalid task record", error)
                } catch (error: DateTimeException) {
                    throw SQLiteException("Invalid task date", error)
                }
            }
        }
    }

    fun save(task: LauncherTask, previous: LauncherTask?) {
        require(previous == null || (
            task.id == previous.id && task.originalDate == previous.originalDate &&
                task.revision == previous.revision + 1
            ))
        val values = ContentValues().apply {
            put("title", task.title)
            put("note", task.note)
            put("scheduled_day", task.date.toEpochDay())
            put("status", task.status.name)
            put("changed_at", task.changedAt)
            put("revision", task.revision)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (previous == null) {
                values.put("id", task.id)
                values.put("original_day", task.originalDate.toEpochDay())
                db.insertOrThrow("tasks", null, values)
            } else {
                val count = db.update("tasks", values, "id = ? AND revision = ?",
                    arrayOf(task.id, previous.revision.toString()))
                if (count != 1) throw SQLiteException("Task changed or is missing; reload before saving")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
