package io.github.sloppytopp.homewatch.detect

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lines a sensor beep up against detections, with a background baseline so "always something nearby" is not mistaken for a match. */
object Report {
    fun build(beeps: List<Long>, events: List<EventRow>, windowMin: Int = 5, nowMs: Long = System.currentTimeMillis()): List<String> {
        if (beeps.isEmpty()) return listOf("No sensor beeps logged yet. Tap \"I heard my sensor beep\" each time it goes off.")
        val f = SimpleDateFormat("EEE MM-dd HH:mm:ss", Locale.US)
        val t = SimpleDateFormat("HH:mm:ss", Locale.US)
        val w = windowMin * 60_000L
        val flagged = events.filter { it.level == Level.WATCH || it.level == Level.ALERT }
        val out = ArrayList<String>()
        var hits = 0
        for (b in beeps.sorted()) {
            val near = flagged.filter { it.ts in (b - w)..(b + w) }.sortedBy { it.ts }
            out += "BEEP ${f.format(Date(b))}"
            if (near.isNotEmpty()) {
                hits++
                near.forEach { out += "   ${t.format(Date(it.ts))} ${it.level.name.lowercase()} ${it.domain}: ${it.msg}" }
            } else out += "   nothing flagged within +/-$windowMin min"
        }
        out += ""
        out += "== $hits of ${beeps.size} beeps had a detection nearby. =="
        val first = minOf(beeps.min(), flagged.minOfOrNull { it.ts } ?: nowMs)
        val windows = maxOf(1L, (nowMs - first) / (2 * w))
        val busy = flagged.map { it.ts / (2 * w) }.distinct().size
        out += "Background: detections were present in $busy of ~$windows ${2 * windowMin}-minute windows overall. " +
            "If that is close to $hits of ${beeps.size}, detections are just background and prove nothing about the beeps."
        if (hits == 0) out += "Nothing detected lines up with any beep: the sensor, an animal or the environment is the likely cause."
        return out
    }
}
