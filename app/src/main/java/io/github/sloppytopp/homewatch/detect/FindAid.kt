package io.github.sloppytopp.homewatch.detect

/** Helpers for the find-it screen. Pure, so they can be tested without a phone. */
object FindAid {
    /** Gap between vibration pulses for a signal: faster as it gets stronger (Geiger-counter style). Null = too faint to bother. */
    fun pulseGapMs(rssi: Int): Long? {
        if (rssi < -92) return null
        val t = ((rssi + 92) / 52.0).coerceIn(0.0, 1.0)   // -92 dBm -> 0, -40 dBm -> 1
        return (1600 - t * 1450).toLong()                   // 1.6 s .. 0.15 s
    }

    /** Strongest reading so far, kept for the walk: the spot where it was loudest is the best clue. */
    fun best(history: List<Int>, previousBest: Int?): Int? = (history + listOfNotNull(previousBest)).maxOrNull()

    /** How the current reading compares to the best so far, in words. */
    fun versusBest(now: Int, best: Int): String = when {
        now >= best -> "This is the strongest so far"
        best - now <= 3 -> "Almost back to your best spot"
        else -> "${best - now} dB weaker than your best spot"
    }
}
