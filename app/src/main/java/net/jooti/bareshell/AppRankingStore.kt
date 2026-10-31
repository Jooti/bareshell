package net.jooti.bareshell

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

internal class AppRankingStore(context: Context) :
    SQLiteOpenHelper(context, "app_ranking.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE adjustments (package TEXT PRIMARY KEY NOT NULL, points INTEGER NOT NULL, decay_day INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("Unsupported app ranking version")
    }

    private fun read(db: SQLiteDatabase): Map<String, RankingAdjustment> = buildMap {
        db.query("adjustments", arrayOf("package", "points", "decay_day"), null, null, null, null, null).use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val points = it.getLong(1)
                val day = it.getLong(2)
                if (name.isNullOrBlank() || points !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
                    day !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()
                ) throw SQLiteException("Invalid app ranking record")
                put(name, RankingAdjustment(points.toInt(), day))
            }
        }
    }

    private fun write(db: SQLiteDatabase, name: String, score: RankingAdjustment) {
        db.delete("adjustments", "package = ?", arrayOf(name))
        if (score.points == 0) return
        db.insertOrThrow("adjustments", null, ContentValues().apply {
            put("package", name)
            put("points", score.points)
            put("decay_day", score.decayDay)
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

    fun reconcile(apps: List<AppEntry>, today: Long = LocalDate.now().toEpochDay()): Map<String, RankingAdjustment> =
        transaction { db ->
            val present = apps.mapTo(mutableSetOf()) { it.packageName }
            val scores = read(db).filterKeys { it in present }.mapValues { it.value.decay(today) }
                .filterValues { it.points != 0 }
            db.delete("adjustments", null, null)
            scores.forEach { (name, score) -> write(db, name, score) }
            scores
        }

    fun adjust(name: String, delta: Int, today: Long = LocalDate.now().toEpochDay()) {
        require(name.isNotBlank())
        transaction { db ->
            write(db, name, (read(db)[name] ?: RankingAdjustment(0, today)).adjust(delta, today))
        }
    }

    private companion object {
        val lock = Any()
    }
}
