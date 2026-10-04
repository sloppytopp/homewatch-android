package io.github.sloppytopp.homewatch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.sloppytopp.homewatch.detect.EventRow
import io.github.sloppytopp.homewatch.detect.Level

/** On-device history. Events never contain operator coordinates; everything older than 30 days is deleted. */
object Store {
    private lateinit var helper: SQLiteOpenHelper
    private const val KEEP_DAYS = 30

    fun init(ctx: Context) {
        helper = object : SQLiteOpenHelper(ctx, "homewatch.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY, ts INTEGER, domain TEXT, level TEXT, msg TEXT)")
                db.execSQL("CREATE TABLE beeps(id INTEGER PRIMARY KEY, ts INTEGER)")
                db.execSQL("CREATE INDEX ev_ts ON events(ts)")
            }
            override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
        }
        prune()
    }

    @Synchronized fun addEvent(e: EventRow) {
        helper.writableDatabase.insert("events", null, ContentValues().apply {
            put("ts", e.ts); put("domain", e.domain); put("level", e.level.name); put("msg", e.msg)
        })
    }

    @Synchronized fun addBeep(ts: Long = System.currentTimeMillis()) {
        helper.writableDatabase.insert("beeps", null, ContentValues().apply { put("ts", ts) })
    }

    @Synchronized fun events(sinceMs: Long, limit: Int = 500): List<EventRow> {
        val out = ArrayList<EventRow>()
        helper.readableDatabase.rawQuery("SELECT ts,domain,level,msg FROM events WHERE ts>=? ORDER BY ts DESC LIMIT ?", arrayOf("$sinceMs", "$limit")).use {
            while (it.moveToNext()) out += EventRow(it.getLong(0), it.getString(1), runCatching { Level.valueOf(it.getString(2)) }.getOrDefault(Level.OK), it.getString(3))
        }
        return out
    }

    @Synchronized fun beeps(sinceMs: Long): List<Long> {
        val out = ArrayList<Long>()
        helper.readableDatabase.rawQuery("SELECT ts FROM beeps WHERE ts>=? ORDER BY ts DESC", arrayOf("$sinceMs")).use { while (it.moveToNext()) out += it.getLong(0) }
        return out
    }

    @Synchronized fun prune() {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        helper.writableDatabase.delete("events", "ts<?", arrayOf("$cutoff"))
        helper.writableDatabase.delete("beeps", "ts<?", arrayOf("$cutoff"))
    }

    @Synchronized fun clearAll() {
        helper.writableDatabase.delete("events", null, null)
        helper.writableDatabase.delete("beeps", null, null)
    }
}
