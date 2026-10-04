package io.github.sloppytopp.homewatch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.sloppytopp.homewatch.detect.EventRow
import io.github.sloppytopp.homewatch.detect.Level
import io.github.sloppytopp.homewatch.detect.RoomItem
import io.github.sloppytopp.homewatch.detect.RoomScan

/** On-device history. Events never contain operator coordinates; everything older than 30 days is deleted. */
object Store {
    private lateinit var helper: SQLiteOpenHelper
    private const val KEEP_DAYS = 30

    fun init(ctx: Context) {
        helper = object : SQLiteOpenHelper(ctx, "homewatch.db", null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY, ts INTEGER, domain TEXT, level TEXT, msg TEXT)")
                db.execSQL("CREATE TABLE beeps(id INTEGER PRIMARY KEY, ts INTEGER)")
                db.execSQL("CREATE INDEX ev_ts ON events(ts)")
                createRoomTables(db)
            }
            override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) { if (o < 2) createRoomTables(db) }
        }
        prune()
    }

    private fun createRoomTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS room_scan(id INTEGER PRIMARY KEY, room TEXT, ts INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS room_item(scan_id INTEGER, key TEXT, kind TEXT, label TEXT, rssi REAL)")
    }

    @Synchronized fun addRoomScan(s: RoomScan) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert("room_scan", null, ContentValues().apply { put("room", s.room); put("ts", s.ts) })
            s.items.forEach { i -> db.insert("room_item", null, ContentValues().apply { put("scan_id", id); put("key", i.key); put("kind", i.kind); put("label", i.label); put("rssi", i.rssi) }) }
            // keep only the 3 most recent sweeps per room
            db.execSQL("DELETE FROM room_item WHERE scan_id IN (SELECT id FROM room_scan WHERE room=? AND id NOT IN (SELECT id FROM room_scan WHERE room=? ORDER BY ts DESC LIMIT 3))", arrayOf(s.room, s.room))
            db.execSQL("DELETE FROM room_scan WHERE room=? AND id NOT IN (SELECT id FROM room_scan WHERE room=? ORDER BY ts DESC LIMIT 3)", arrayOf(s.room, s.room))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun scanFromRow(room: String, ts: Long, id: Long): RoomScan {
        val items = ArrayList<RoomItem>()
        helper.readableDatabase.rawQuery("SELECT key,kind,label,rssi FROM room_item WHERE scan_id=? ORDER BY rssi DESC", arrayOf("$id")).use {
            while (it.moveToNext()) items += RoomItem(it.getString(0), it.getString(1), it.getString(2), it.getDouble(3))
        }
        return RoomScan(room, ts, items)
    }

    /** Most recent sweep of every room. */
    @Synchronized fun latestScans(): Map<String, RoomScan> {
        val out = LinkedHashMap<String, RoomScan>()
        helper.readableDatabase.rawQuery("SELECT room,ts,id FROM room_scan s WHERE ts=(SELECT MAX(ts) FROM room_scan WHERE room=s.room) ORDER BY room", null).use {
            while (it.moveToNext()) out[it.getString(0)] = scanFromRow(it.getString(0), it.getLong(1), it.getLong(2))
        }
        return out
    }

    @Synchronized fun previousScan(room: String): RoomScan? {
        helper.readableDatabase.rawQuery("SELECT room,ts,id FROM room_scan WHERE room=? ORDER BY ts DESC LIMIT 1", arrayOf(room)).use {
            return if (it.moveToFirst()) scanFromRow(it.getString(0), it.getLong(1), it.getLong(2)) else null
        }
    }

    @Synchronized fun deleteRoom(room: String) {
        val db = helper.writableDatabase
        db.execSQL("DELETE FROM room_item WHERE scan_id IN (SELECT id FROM room_scan WHERE room=?)", arrayOf(room))
        db.delete("room_scan", "room=?", arrayOf(room))
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
        helper.writableDatabase.delete("room_item", null, null)
        helper.writableDatabase.delete("room_scan", null, null)
    }
}
