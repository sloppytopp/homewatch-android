package io.github.sloppytopp.homewatch.detect

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turn-in-place direction hint. A phone has ONE antenna, so it cannot point at a transmitter. But your body blocks
 * 2.4 GHz: while you turn slowly in a circle, the signal is usually strongest when you face the source.
 * We bucket (compass heading, signal) samples into 8 sectors and average the *power* (not dB) per sector.
 */
class DirectionFinder(private val buckets: Int = 8) {
    private val sumMw = DoubleArray(buckets)
    private val n = IntArray(buckets)
    var total = 0; private set

    fun add(headingDeg: Double, rssi: Int) {
        val i = (((headingDeg % 360 + 360) % 360) / (360.0 / buckets)).toInt().coerceIn(0, buckets - 1)
        sumMw[i] += 10.0.pow(rssi / 10.0); n[i]++; total++
    }

    fun reset() { sumMw.fill(0.0); n.fill(0); total = 0 }

    fun coveredSectors() = n.count { it > 0 }

    fun meanDbm(i: Int): Double? = if (n[i] == 0) null else 10 * Math.log10(sumMw[i] / n[i])

    class Result(val bearingDeg: Double, val spreadDb: Double, val confident: Boolean)

    /** Needs most of the circle covered and a clear strongest side; otherwise says it can't tell. */
    fun result(): Result? {
        if (coveredSectors() < buckets - 2 || total < 10) return null
        val means = (0 until buckets).mapNotNull { i -> meanDbm(i)?.let { i to it } }
        val spread = means.maxOf { it.second } - means.minOf { it.second }
        var x = 0.0; var y = 0.0
        for ((i, db) in means) {
            val w = 10.0.pow(db / 10.0); val a = Math.toRadians((i + 0.5) * 360.0 / buckets)
            x += w * sin(a); y += w * cos(a)
        }
        val bearing = (Math.toDegrees(atan2(x, y)) + 360) % 360
        return Result(bearing, spread, spread >= 4.0)
    }
}

/** "Warmer / colder" from the last few readings (newest last). */
object Trend {
    fun of(samples: List<Int>): String? {
        if (samples.size < 6) return null
        val half = samples.size / 2
        val d = samples.takeLast(half).average() - samples.take(half).average()
        return when { d >= 3 -> "warmer"; d <= -3 -> "colder"; else -> "steady" }
    }
}
