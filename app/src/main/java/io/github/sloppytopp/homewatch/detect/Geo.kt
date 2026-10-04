package io.github.sloppytopp.homewatch.detect

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

object Geo {
    private const val K = 111_320.0 // meters per degree of latitude

    /** East/north offset in meters of (lat,lon) from home. */
    fun offsetM(lat: Double, lon: Double, homeLat: Double, homeLon: Double): Pair<Double, Double> =
        Pair((lon - homeLon) * K * cos(Math.toRadians(homeLat)), (lat - homeLat) * K)

    fun distanceM(lat: Double, lon: Double, homeLat: Double, homeLon: Double): Double {
        val (e, n) = offsetM(lat, lon, homeLat, homeLon); return hypot(e, n)
    }

    fun bearingDeg(lat: Double, lon: Double, homeLat: Double, homeLon: Double): Double {
        val (e, n) = offsetM(lat, lon, homeLat, homeLon)
        return (Math.toDegrees(atan2(e, n)) + 360) % 360
    }

    fun compass(deg: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((deg + 22.5) / 45).toInt() % 8]

    /** Rough distance from signal strength (log-distance, n=2.5). Indoors this can be badly wrong. */
    fun rssiToMeters(rssi: Int, txAt1m: Int): Double = Math.pow(10.0, (txAt1m - rssi) / 25.0)

    /** 0..1 radius on the radar: [0,.33] very close (<5 m), [.33,.66] close (<20 m), rest far. */
    fun radarFraction(m: Double): Double {
        val x = maxOf(0.5, m)
        return when {
            x <= 5 -> x / 5 * 0.33
            x <= 20 -> 0.33 + (x - 5) / 15 * 0.33
            else -> minOf(1.0, 0.66 + minOf(x - 20, 40.0) / 40 * 0.34)
        }
    }

    @Suppress("unused") fun sq(x: Double) = sqrt(x * x)
}
