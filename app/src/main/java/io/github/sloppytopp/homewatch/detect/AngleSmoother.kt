package io.github.sloppytopp.homewatch.detect

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Low-pass filter for compass headings that handles the 359 -> 0 wrap, so the needle doesn't jitter or spin. */
class AngleSmoother(private val alpha: Double = 0.18) {
    private var s = 0.0
    private var c = 0.0
    private var started = false

    fun update(deg: Double): Double {
        val r = Math.toRadians(deg)
        if (!started) { s = sin(r); c = cos(r); started = true }
        else { s += alpha * (sin(r) - s); c += alpha * (cos(r) - c) }
        return (Math.toDegrees(atan2(s, c)) + 360) % 360
    }
}
