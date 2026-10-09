package io.github.sloppytopp.homewatch.detect

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TscmRoom(val name: String, val sweptAt: Long?, val signals: Int?, val checked: Set<String>)

/**
 * The scope-and-method part of a TSCM-style sweep report: what was covered, what was and was NOT checked, and honest wording.
 * It never says a place is "clear": only that nothing was found by the listed methods.
 */
object Tscm {
    const val TITLE = "N0RMA TSCM-STYLE SWEEP REPORT (not a professional sweep)"

    private val NOT_DONE = listOf(
        "Non-linear junction detection (finds electronics even when switched off or dormant)",
        "Wideband RF spectrum analysis (a phone hears only its own Wi-Fi and Bluetooth radios)",
        "Thermal imaging",
        "Wired, telephone, power-line and cellular/GPS device checks",
        "Inspection by a trained examiner with specialist equipment",
    )

    private fun fmt(ts: Long, tz: TimeZone) = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).apply { timeZone = tz }.format(Date(ts))

    fun sections(rooms: List<TscmRoom>, events: List<EventRow>, follow: List<FollowHit>, mineCount: Int, tz: TimeZone = TimeZone.getDefault()): String {
        val flagged = events.filter { it.level == Level.WATCH || it.level == Level.ALERT }
        val o = StringBuilder()
        o.appendLine("SCOPE")
        if (rooms.isEmpty()) o.appendLine("- No rooms or areas were set up, so no room-by-room sweep or inspection is recorded.")
        else o.appendLine("- Areas covered: " + rooms.joinToString(", ") { it.name })
        o.appendLine()
        o.appendLine("METHODS PERFORMED")
        o.appendLine("1. Radio sweep (Wi-Fi and Bluetooth heard by this phone), per area:")
        if (rooms.isEmpty()) o.appendLine("   (none)")
        rooms.forEach { r ->
            o.appendLine("   - ${r.name}: " + if (r.sweptAt == null) "NOT SWEPT" else "swept ${fmt(r.sweptAt, tz)}, ${r.signals ?: 0} signals heard")
        }
        o.appendLine("2. Physical inspection checklist, per area (what the person doing the sweep ticked off by looking):")
        if (rooms.isEmpty()) o.appendLine("   (none)")
        rooms.forEach { r ->
            val items = Inspection.itemsFor(r.name)
            val done = items.count { it.id in r.checked }
            o.appendLine("   - ${r.name}: $done of ${items.size} items checked")
            items.filter { it.id !in r.checked }.forEach { o.appendLine("       NOT CHECKED: ${it.where}") }
        }
        o.appendLine("3. Camera-lens finder (phone camera and torch): available in the app; use is not recorded, so this report makes no statement about it.")
        o.appendLine("4. Tracker following test: " + if (follow.isEmpty()) "no tracker was heard at three or more places over 30+ minutes."
            else follow.joinToString("; ") { "${it.label} heard at ${it.visits.size} places over ${it.spanMs / 60_000} min" })
        o.appendLine("5. Watch/alert findings in the log period: ${flagged.size}" +
            (if (flagged.isNotEmpty()) " (" + flagged.groupingBy { it.domain }.eachCount().entries.joinToString(", ") { "${it.value} ${it.key}" } + ")" else ""))
        if (mineCount > 0) o.appendLine("   $mineCount device(s)/source(s) were marked by the user as their own and are not flagged.")
        o.appendLine()
        o.appendLine("METHODS NOT PERFORMED (a phone cannot do these)")
        NOT_DONE.forEach { o.appendLine("- $it") }
        o.appendLine()
        o.appendLine("CONCLUSION")
        o.appendLine(if (flagged.isEmpty() && follow.isEmpty()) "- Nothing was flagged by the methods listed above. This does NOT show the area is free of surveillance devices."
            else "- ${flagged.size} item(s) were flagged and are listed in the event log below. Each is a signal worth checking in person, not proof of surveillance or of who placed anything.")
        o.appendLine("- For a high-risk situation, hire a licensed TSCM professional. If you are in danger, call your local emergency number.")
        return o.toString()
    }
}
