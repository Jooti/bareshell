package net.jooti.bareshell

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

internal class ContactHistoryStore(context: Context) :
    SQLiteOpenHelper(context, "contact_history.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE scores (lookup_key TEXT PRIMARY KEY NOT NULL, points INTEGER NOT NULL, decay_day INTEGER NOT NULL, last_interaction INTEGER NOT NULL, manual_points INTEGER NOT NULL DEFAULT 0, manual_day INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE scores ADD COLUMN manual_points INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE scores ADD COLUMN manual_day INTEGER NOT NULL DEFAULT 0")
        } else throw SQLiteException("Unsupported contact history version")
    }

    private fun read(db: SQLiteDatabase): Map<String, ContactScore> = buildMap {
        db.query("scores", arrayOf("lookup_key", "points", "decay_day", "last_interaction", "manual_points", "manual_day"),
            null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val points = cursor.getLong(1)
                val day = cursor.getLong(2)
                val time = cursor.getLong(3)
                val manual = cursor.getLong(4)
                val manualDay = cursor.getLong(5)
                if (key.isNullOrBlank() || points !in 0..Int.MAX_VALUE.toLong() ||
                    day !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() || time < 0 ||
                    manual !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
                    manualDay !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()
                ) throw SQLiteException("Invalid contact history record")
                put(key, ContactScore(points.toInt(), day, time, RankingAdjustment(manual.toInt(), manualDay)))
            }
        }
    }

    private fun write(db: SQLiteDatabase, key: String, score: ContactScore) {
        db.insertOrThrow("scores", null, ContentValues().apply {
            put("lookup_key", key)
            put("points", score.points)
            put("decay_day", score.decayDay)
            put("last_interaction", score.lastInteraction)
            put("manual_points", score.adjustment.points)
            put("manual_day", score.adjustment.decayDay)
        })
    }

    private fun <T> transaction(block: (SQLiteDatabase) -> T): T = synchronized(lock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    val version: Long get() = clearVersion.get()

    fun record(
        lookupKey: String,
        now: Long = System.currentTimeMillis(),
        today: Long = LocalDate.now().toEpochDay(),
        expectedVersion: Long = version,
    ) {
        require(lookupKey.isNotBlank())
        transaction { db ->
            // A successful clear also discards interactions still waiting in a worker queue.
            if (expectedVersion != version) return@transaction
            val previous = read(db)[lookupKey] ?: ContactScore(0, today, now)
            val next = previous.interact(today, now)
            db.delete("scores", "lookup_key = ?", arrayOf(lookupKey))
            write(db, lookupKey, next)
        }
    }

    fun reconcile(
        contacts: List<ContactEntry>,
        resolve: (String) -> String?,
        today: Long = LocalDate.now().toEpochDay(),
    ): Map<String, ContactScore> = transaction { db ->
        val present = contacts.mapTo(mutableSetOf()) { it.lookupKey }
        val next = mutableMapOf<String, ContactScore>()
        read(db).forEach { (key, stored) ->
            val score = stored.decay(today)
            if (score.points > 0 || score.adjustment.points != 0) {
                val canonical = if (key in present) key else resolve(key)
                if (canonical != null) next[canonical] = next[canonical]?.merge(score) ?: score
            }
        }
        db.delete("scores", null, null)
        next.forEach { (key, score) -> write(db, key, score) }
        next
    }

    fun adjust(
        lookupKey: String, delta: Int, today: Long = LocalDate.now().toEpochDay(),
        expectedVersion: Long = version,
    ): Boolean {
        require(lookupKey.isNotBlank())
        return transaction { db ->
            if (expectedVersion != version) return@transaction false
            val current = (read(db)[lookupKey] ?: ContactScore(0, today, 0)).decay(today)
            val next = current.copy(adjustment = current.adjustment.adjust(delta, today))
            db.delete("scores", "lookup_key = ?", arrayOf(lookupKey))
            if (next.points > 0 || next.adjustment.points != 0) write(db, lookupKey, next)
            true
        }
    }

    fun clearHistory() = synchronized(lock) {
        transaction { it.delete("scores", null, null) }
        clearVersion.incrementAndGet()
    }

    private companion object {
        val lock = Any()
        val clearVersion = AtomicLong()
    }
}
