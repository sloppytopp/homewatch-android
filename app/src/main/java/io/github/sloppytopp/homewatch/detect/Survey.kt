package io.github.sloppytopp.homewatch.detect

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/** One sighting made while walking, tagged with where the phone was. Stays on the phone until the user exports it. */
data class SurveyPoint(
    val ts: Long, val kind: String, val key: String, val label: String, val rssi: Int,
    val lat: Double, val lon: Double, val acc: Float,
    val caps: String = "", val freq: Int = 0, val vendor: String = "", val klass: String = "",
)

/**
 * Write-time gate for survey points: a source standing still next to a stationary phone would otherwise be logged every 3 s forever.
 * A point is kept if it is the first for its source, the phone moved, enough time passed, or the signal changed noticeably.
 */
class SurveyGate(private val minMoveM: Double = 3.0, private val minGapMs: Long = 30_000, private val minRssiDelta: Int = 6) {
    private class Last(val ts: Long, val lat: Double, val lon: Double, val rssi: Int)
    private val last = HashMap<String, Last>()

    fun keep(p: SurveyPoint): Boolean {
        val l = last[p.key]
        val ok = l == null || p.ts - l.ts >= minGapMs || Math.abs(p.rssi - l.rssi) >= minRssiDelta ||
            Geo.distanceM(p.lat, p.lon, l.lat, l.lon) >= minMoveM
        if (ok) { last[p.key] = Last(p.ts, p.lat, p.lon, p.rssi); if (last.size > 5000) last.clear() }
        return ok
    }
}

class Estimate(
    val key: String, val kind: String, val label: String, val lat: Double, val lon: Double, val radiusM: Double,
    val samples: Int, val bestRssi: Int, val vendor: String, val klass: String, val caps: String,
)

object Survey {
    private const val K = 111_320.0

    /** True when every estimate sits in one small spot - the sign of standing still (or indoor GPS drift), not of real findings. */
    fun clustered(est: List<Estimate>, withinM: Double = 30.0): Boolean {
        if (est.size < 3) return false
        val lat0 = est.map { it.lat }.average(); val cosLat = cos(Math.toRadians(lat0))
        for (a in est) for (b in est) {
            val dx = (a.lon - b.lon) * K * cosLat; val dy = (a.lat - b.lat) * K
            if (sqrt(dx * dx + dy * dy) > withinM) return false
        }
        return true
    }

    /**
     * Rough position of each signal source: the signal-strength-weighted centre of the spots where it was loudest.
     * An ESTIMATE only - walls and the phone's antenna bend signal strength - so every pin carries a radius.
     */
    fun estimate(points: List<SurveyPoint>, minSamples: Int = 3, top: Int = 12): List<Estimate> {
        return points.groupBy { it.key }.mapNotNull { (key, ps) ->
            if (ps.size < minSamples) return@mapNotNull null
            val best = ps.sortedByDescending { it.rssi }.take(top)
            val lat0 = best.map { it.lat }.average()
            val cosLat = cos(Math.toRadians(lat0))
            val xs = best.map { (it.lon - best[0].lon) * K * cosLat }
            val ys = best.map { (it.lat - best[0].lat) * K }
            // GPS jitters by metres even when you stand still: demand a real walk, scaled to how shaky the fixes are
            val meanAcc = best.map { it.acc.toDouble() }.average()
            if ((xs.max() - xs.min()) + (ys.max() - ys.min()) < maxOf(25.0, 3 * meanAcc)) return@mapNotNull null
            val w = best.map { 10.0.pow(it.rssi / 10.0) }
            val sw = w.sum()
            val cx = xs.indices.sumOf { w[it] * xs[it] } / sw
            val cy = ys.indices.sumOf { w[it] * ys[it] } / sw
            val spread = sqrt(xs.indices.sumOf { w[it] * ((xs[it] - cx).pow(2) + (ys[it] - cy).pow(2)) } / sw)
            val first = best[0]
            Estimate(
                key, first.kind, first.label,
                lat = best[0].lat + cy / K, lon = best[0].lon + cx / (K * cosLat),
                radiusM = maxOf(5.0, spread + best.map { it.acc.toDouble() }.average()),
                samples = ps.size, bestRssi = best[0].rssi, vendor = first.vendor, klass = first.klass, caps = first.caps,
            )
        }.sortedByDescending { it.bestRssi }
    }
}

object Export {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun utc(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    fun channel(freqMhz: Int): Int = when {
        freqMhz == 2484 -> 14
        freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
        freqMhz in 5000..5895 -> (freqMhz - 5000) / 5
        freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
        else -> 0
    }

    private fun styleFor(klass: String, kind: String) = when {
        klass == "camera" || klass == "drone" -> "red"
        kind == "tracker" -> "amber"
        kind == "ble" -> "blue"
        else -> "gray"
    }

    /** Google Earth / any KML viewer. Pins are ESTIMATES with a radius in the description. */
    fun kml(home: Pair<Double, Double>?, points: List<SurveyPoint>, estimates: List<Estimate>, generatedMs: Long): String = buildString {
        fun style(id: String, abgr: String) = append("<Style id=\"$id\"><IconStyle><color>$abgr</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon></IconStyle></Style>\n")
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n")
        append("<name>N0RMA survey ${esc(utc(generatedMs))} UTC</name>\n")
        append("<description>Estimated positions from signal strength while walking. Walls and antennas distort signal strength, so treat every pin as a rough guess (see the radius).</description>\n")
        style("red", "ff4040d0"); style("amber", "ff3aa0d8"); style("blue", "ffc08a58"); style("gray", "ff909090"); style("home", "ffe0a040")
        home?.let { (la, lo) -> append("<Placemark><name>Home</name><styleUrl>#home</styleUrl><Point><coordinates>$lo,$la,0</coordinates></Point></Placemark>\n") }
        val path = points.sortedBy { it.ts }.map { it.lon to it.lat }.fold(ArrayList<Pair<Double, Double>>()) { acc, p -> if (acc.lastOrNull() != p) acc.add(p); acc }
        if (path.size >= 2) {
            append("<Placemark><name>Walk path</name><Style><LineStyle><color>ff8cc65e</color><width>3</width></LineStyle></Style><LineString><tessellate>1</tessellate><coordinates>")
            path.forEach { (lo, la) -> append("$lo,$la,0 ") }
            append("</coordinates></LineString></Placemark>\n")
        }
        append("<Folder><name>Estimated sources (${estimates.size})</name>\n")
        estimates.forEach { e ->
            append("<Placemark><name>${esc(e.label.ifEmpty { e.key })}</name><styleUrl>#${styleFor(e.klass, e.kind)}</styleUrl><description>")
            append(esc("${e.key}${if (e.vendor.isNotEmpty()) " - ${e.vendor}" else ""}\nType: ${e.kind}${if (e.klass.isNotEmpty() && e.klass != "other") " (${e.klass}-like)" else ""}\n" +
                "Strongest ${e.bestRssi} dBm, ${e.samples} samples\nEstimated position, about +/- ${Math.round(e.radiusM)} m"))
            append("</description><Point><coordinates>${e.lon},${e.lat},0</coordinates></Point></Placemark>\n")
        }
        append("</Folder>\n</Document></kml>\n")
    }

    /**
     * Network names come from strangers' radios. A name like =HYPERLINK(...) would run as a formula when the CSV is opened in
     * Excel/Sheets (CSV injection), so a leading = + - @ tab or CR gets a single quote in front, then normal CSV quoting.
     */
    private fun csv(raw: String): String {
        val s = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** WiGLE's "WigleWifi-1.4" upload format. Exporting is local; uploading is the user's own choice (WiGLE is public). */
    fun wigleCsv(points: List<SurveyPoint>, appRelease: String, model: String, release: String, device: String, brand: String): String = buildString {
        append("WigleWifi-1.4,appRelease=${csv(appRelease)},model=${csv(model)},release=${csv(release)},device=${csv(device)},display=,board=,brand=${csv(brand)}\n")
        append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
        points.sortedBy { it.ts }.forEach { p ->
            val wifi = p.kind == "wifi"
            val mac = p.key.substringAfter(":")
            append("${csv(mac)},${csv(p.label)},${csv(if (wifi) p.caps else "Misc")},${utc(p.ts)},${if (wifi) channel(p.freq) else 0},${p.rssi},${p.lat},${p.lon},0,${p.acc},${if (wifi) "WIFI" else "BLE"}\n")
        }
    }
}
