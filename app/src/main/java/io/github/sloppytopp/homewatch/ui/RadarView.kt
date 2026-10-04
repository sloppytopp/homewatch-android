package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.detect.Geo
import io.github.sloppytopp.homewatch.detect.Level
import io.github.sloppytopp.homewatch.detect.Snapshot
import kotlin.math.cos
import kotlin.math.sin

private data class Blip(val id: String, val label: String, val meters: Double, val color: Color, val triangle: Boolean = false)

private fun blips(s: Snapshot): List<Blip> {
    val out = ArrayList<Blip>()
    for (w in s.wifi) {
        val c = when {
            w.klass == "drone" || w.klass == "camera" -> UiColors.alert
            w.ssid in Prefs.mySsids -> UiColors.good
            else -> UiColors.neutral
        }
        out += Blip(w.bssid, w.ssid, Geo.rssiToMeters(w.level, -45), c)
    }
    val tcol = if (s.tracker.level == Level.ALERT) UiColors.alert else UiColors.watch
    for (t in s.trackers) out += Blip(t.addr, (if (t.mine) "Yours: " else "") + t.label.substringBefore(" SEPARATED"), Geo.rssiToMeters(t.rssi, -59), if (t.mine) UiColors.good else tcol)
    for (d in s.drones) out += Blip(d.addr, "DRONE", Geo.rssiToMeters(d.rssi, if (d.via == "Wi-Fi") -45 else -59), UiColors.alert, triangle = true)
    return out
}

private fun DrawScope.tri(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path().apply { moveTo(cx, cy - r); lineTo(cx + r * 0.9f, cy + r * 0.7f); lineTo(cx - r * 0.9f, cy + r * 0.7f); close() }
    drawPath(p, color)
}

@Composable
fun RadarView(s: Snapshot) {
    val tm = rememberTextMeasurer()
    val label = TextStyle(color = UiColors.dim, fontSize = 11.sp)
    Column {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val w = size.width
            val c = Offset(w / 2, w / 2)
            val r = w / 2 - 10.dp.toPx()
            listOf(0.33f to "very close", 0.66f to "close", 1f to "far").forEach { (f, name) ->
                drawCircle(UiColors.ring, r * f, c, style = Stroke(1.dp.toPx()))
                drawText(tm, name, Offset(c.x + 4.dp.toPx(), c.y - r * f + 2.dp.toPx()), label)
            }
            drawLine(UiColors.ring, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(UiColors.ring, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            drawRect(Color(0xFF58708A).copy(alpha = if (Prefs.night) 0.4f else 0.9f), Offset(c.x - 5.dp.toPx(), c.y - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()))
            for (b in blips(s)) {
                val a = (b.id.hashCode().toLong() and 0x7fffffff) % 3600 / 3600.0 * 2 * Math.PI
                val rad = r * Geo.radarFraction(b.meters).toFloat()
                val x = c.x + rad * cos(a).toFloat(); val y = c.y + rad * sin(a).toFloat()
                if (b.triangle) tri(x, y, 9.dp.toPx(), b.color) else drawCircle(b.color, 6.dp.toPx(), Offset(x, y))
                drawText(tm, b.label.take(14), Offset(x + 9.dp.toPx(), y - 6.dp.toPx()), label)
            }
        }
        Text("Rough estimate from signal strength - indoors it can be badly wrong. Direction is NOT known: blip angles are arbitrary.",
            color = UiColors.faint, fontSize = 11.sp)
    }
}

@Composable
fun DroneMapView(s: Snapshot) {
    val home = Prefs.home
    val fixes = s.fixes.filter { it.lat != null && it.lon != null }
    if (home == null) {
        Text("Set your home location in Settings to see drones plotted on a map.", color = UiColors.dim, fontSize = 13.sp); return
    }
    if (fixes.isEmpty()) {
        Text("No drone is broadcasting a position right now.", color = UiColors.dim, fontSize = 13.sp); return
    }
    val ctx = LocalContext.current
    val tm = rememberTextMeasurer()
    val label = TextStyle(color = UiColors.dim, fontSize = 11.sp)
    val pts = ArrayList<Pair<Double, Double>>()
    fixes.forEach { f ->
        pts += Geo.offsetM(f.lat!!, f.lon!!, home.lat, home.lon)
        if (f.opLat != null && f.opLon != null) pts += Geo.offsetM(f.opLat, f.opLon, home.lat, home.lon)
    }
    val maxD = maxOf(100.0, pts.maxOf { Math.hypot(it.first, it.second) }) * 1.25
    Column {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val w = size.width; val c = Offset(w / 2, w / 2); val sc = (w / 2 - 8.dp.toPx()) / maxD.toFloat()
            listOf(0.25, 0.5, 1.0).forEach { f ->
                drawCircle(UiColors.ring, (maxD * f).toFloat() * sc, c, style = Stroke(1.dp.toPx()))
                drawText(tm, "${Math.round(maxD * f)} m", Offset(c.x + 4.dp.toPx(), c.y - (maxD * f).toFloat() * sc), label)
            }
            drawText(tm, "N", Offset(c.x - 4.dp.toPx(), 2.dp.toPx()), label)
            drawRect(Color(0xFF58708A).copy(alpha = if (Prefs.night) 0.4f else 0.9f), Offset(c.x - 5.dp.toPx(), c.y - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()))
            drawText(tm, "HOME", Offset(c.x + 8.dp.toPx(), c.y + 4.dp.toPx()), label)
            fixes.forEach { f ->
                val (e, n) = Geo.offsetM(f.lat!!, f.lon!!, home.lat, home.lon)
                val x = c.x + e.toFloat() * sc; val y = c.y - n.toFloat() * sc
                if (f.opLat != null && f.opLon != null) {
                    val (oe, on) = Geo.offsetM(f.opLat, f.opLon, home.lat, home.lon)
                    val ox = c.x + oe.toFloat() * sc; val oy = c.y - on.toFloat() * sc
                    drawLine(UiColors.watch, Offset(x, y), Offset(ox, oy), 1.dp.toPx())
                    drawCircle(UiColors.watch, 6.dp.toPx(), Offset(ox, oy))
                }
                tri(x, y, 10.dp.toPx(), UiColors.alert)
            }
        }
        fixes.forEach { f ->
            val d = Geo.distanceM(f.lat!!, f.lon!!, home.lat, home.lon)
            val dir = Geo.compass(Geo.bearingDeg(f.lat, f.lon, home.lat, home.lon))
            Text("Drone ${f.id}: ${Math.round(d)} m $dir of home, alt ${f.alt?.let { Math.round(it).toString() } ?: "?"} m" +
                (if (f.opLat != null) " (dot = claimed operator position, shown live only)" else ""), color = UiColors.text, fontSize = 12.sp)
            TextButton(onClick = { openInMaps(ctx, f.lat, f.lon!!, "Drone claim ${f.id}") }) { Text("Open this position in your Maps app") }
        }
        Text("Positions are whatever the broadcast claims - Remote ID can be faked.", color = UiColors.faint, fontSize = 11.sp)
    }
}
