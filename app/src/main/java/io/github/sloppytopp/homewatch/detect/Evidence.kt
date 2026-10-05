package io.github.sloppytopp.homewatch.detect

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A plain-text report for police, an advocate or a protective-order hearing: summary, tracker trail, event log.
 * Every log line carries a hash that also covers the line before it, so deleting, reordering or editing a line breaks the chain.
 * That shows the text was not edited AFTER export. It cannot prove who made it: for that, send the final chain hash to someone you trust right away.
 */
object Evidence {
    private const val VERSION = "homewatch-evidence-v1"

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fmt(ts: Long, tz: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply { timeZone = tz }.format(Date(ts))

    fun build(nowMs: Long, events: List<EventRow>, follow: List<FollowHit>, sights: List<Sight>, tz: TimeZone = TimeZone.getDefault()): String {
        val flagged = events.filter { it.level == Level.WATCH || it.level == Level.ALERT }.sortedBy { it.ts }
        val out = StringBuilder()
        out.appendLine("N0RMA EVIDENCE REPORT")
        out.appendLine("Generated: ${fmt(nowMs, tz)}")
        out.appendLine()
        out.appendLine("SUMMARY (plain language)")
        out.appendLine("- This report lists what this phone's Bluetooth and Wi-Fi radios heard: nearby trackers, drone broadcasts and camera-like Wi-Fi networks.")
        out.appendLine("- ${flagged.size} watch/alert events are listed below, from ${flagged.firstOrNull()?.let { fmt(it.ts, tz) } ?: "n/a"} to ${flagged.lastOrNull()?.let { fmt(it.ts, tz) } ?: "n/a"}.")
        if (follow.isEmpty()) out.appendLine("- No tracker was heard at three or more different places over 30+ minutes (the \"following\" test).")
        else follow.forEach { h ->
            out.appendLine("- FOLLOWING: ${h.label} (${h.key}) was heard at ${h.visits.size} different places over ${h.spanMs / 60_000} minutes.")
        }
        out.appendLine("- Limits: this is signal evidence, not proof of who placed a device. Places are rough Wi-Fi \"fingerprints\" (no GPS). Cellular/GPS trackers cannot be heard by a phone. Remote ID drone broadcasts can be faked.")
        out.appendLine()
        if (follow.isNotEmpty()) {
            out.appendLine("TRACKER TRAIL (which \"place\" each sighting was at; place numbers are in order first visited)")
            follow.forEach { h ->
                out.appendLine("${h.label} ${h.key}")
                h.visits.forEach { v -> out.appendLine("   place ${v.placeNo}: ${fmt(v.first, tz)} to ${fmt(v.last, tz)}  (${v.n} sightings)") }
            }
            out.appendLine()
        }
        val body = out.toString()
        val start0 = sha("$VERSION|${nowMs}|${sha(body)}")
        var h = start0
        out.appendLine("EVENT LOG  (line number | time | level | area | detail | chained hash)")
        flagged.forEachIndexed { i, e ->
            val line = "%04d | %s | %s | %s | %s".format(i + 1, fmt(e.ts, tz), e.level.name.lowercase(), e.domain, e.msg.replace('\n', ' '))
            h = sha("$h|$line")
            out.appendLine("$line | ${h.take(12)}")
        }
        out.appendLine()
        out.appendLine("CHAIN START: ${start0.take(12)}  (generated-at ${nowMs} ms)")
        out.appendLine("CHAIN END (final hash): $h")
        out.appendLine("To keep this tamper-evident, email or text the CHAIN END value to yourself or an advocate right now: it fixes the time and content.")
        return out.toString()
    }

    /** True when every log line and the final hash agree with the text. Detects edits, deletions and reordering of the event log. */
    fun verify(report: String): Boolean {
        val lines = report.lines()
        val gen = lines.firstOrNull { it.startsWith("CHAIN START:") } ?: return false
        val ms = Regex("generated-at (\\d+) ms").find(gen)?.groupValues?.get(1) ?: return false
        val end = lines.firstOrNull { it.startsWith("CHAIN END (final hash): ") }?.removePrefix("CHAIN END (final hash): ")?.trim() ?: return false
        val start = lines.indexOfFirst { it.startsWith("EVENT LOG") }
        if (start < 0) return false
        // everything above the event log (generated time, summary, tracker trail) is covered too
        val body = lines.take(start).joinToString("\n") + "\n"
        var h = sha("$VERSION|$ms|${sha(body)}")
        if (gen.removePrefix("CHAIN START:").trim().take(12) != h.take(12)) return false
        for (l in lines.drop(start + 1)) {
            if (l.isBlank()) break
            val cut = l.lastIndexOf(" | ")
            if (cut < 0) return false
            h = sha("$h|${l.substring(0, cut)}")
            if (l.substring(cut + 3).trim() != h.take(12)) return false
        }
        return h == end
    }
}
