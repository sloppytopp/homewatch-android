package io.github.sloppytopp.homewatch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.detect.Geo
import io.github.sloppytopp.homewatch.detect.Trend
import io.github.sloppytopp.homewatch.scan.Monitor
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Opened by tapping a flagged tile or an alert notification: what it is, which way, how close. */
@Composable
fun HuntHost(domain: String, onClose: () -> Unit) {
    val s = Monitor.snapshot
    when (domain) {
        "tracker" -> {
            val t = s.trackers.maxByOrNull { it.rssi }
            var safe by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            if (!safe) SafetyFirst(onContinue = { safe = true }, onClose = onClose)
            else if (t != null) Finder(t.addr, t.label, onClose)
            else Notice("The tracker isn't being heard right now. Wait a few seconds with scanning on, or move around and try again.", onClose)
        }
        "drone" -> DroneHunt(onClose)
        else -> WifiHunt(onClose)
    }
}

/** Shown before the finder: finding a tracker is not the first step. Removing it can tip off the person who placed it. */
@Composable
private fun SafetyFirst(onContinue: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = onClose, title = { Text("Before you look for it") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. In danger right now? Get somewhere safe and call 911 (or your local emergency number).")
                Text("2. Don't remove, switch off or destroy it yet. If someone is tracking you, taking it away can alert them and make things worse.")
                Text("3. Document it: when you find it, photograph it where it is, and note the date and time. Export the evidence report from the History tab.")
                Text("4. Talk to someone first: an advocate (US: National Domestic Violence Hotline 1-800-799-7233, or text START to 88788) or the police. They can help you plan what to do next.")
                Text("It may also be harmless: a neighbor's or a passer-by's tracker, or one in a borrowed bag or car. A flag is a reason to look, not proof.", fontSize = 12.sp, color = UiColors.dim)
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("I understand - help me find it") } },
        dismissButton = {
            Column {
                TextButton(onClick = { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:18007997233")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open hotline in dialer") }
                TextButton(onClick = onClose) { Text("Not now") }
            }
        },
    )
}

@Composable
private fun Notice(text: String, onClose: () -> Unit) = AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = onClose, title = { Text("Nothing to find right now") }, text = { Text(text) },
    confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
)

@Composable
private fun DroneHunt(onClose: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { while (true) { Monitor.refresh(); delay(1000) } }
    val s = Monitor.snapshot
    val fix = s.fixes.firstOrNull { it.lat != null && it.lon != null }
    if (fix == null) {
        val ble = s.drones.firstOrNull()
        if (ble != null) Finder(ble.addr, "Drone broadcast (no position)", onClose)
        else Notice("No drone is being heard right now.", onClose)
        return
    }
    val compass = rememberCompass()
    val heading by compass.heading
    var me by remember { mutableStateOf<Location?>(null) }
    var gpsMsg by remember { mutableStateOf("Getting your position...") }
    DisposableEffect(Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = LocationListener { loc -> me = loc }
        try {
            @SuppressLint("MissingPermission") val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) me = last
            @SuppressLint("MissingPermission") val p = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestLocationUpdates(p, 1000L, 0f, l)
        } catch (e: SecurityException) { gpsMsg = "Location permission is needed to point you at the drone." } catch (e: Exception) { gpsMsg = "Couldn't read your position (is Location on?)." }
        onDispose { lm.removeUpdates(l) }
    }

    AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = onClose,
        title = { Text("Find the drone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Remote ID broadcast CLAIMS a drone: ${fix.id}. Altitude ${fix.alt?.let { Math.round(it).toString() } ?: "?"} m. This is only what the broadcast says - it can be faked.", fontSize = 12.sp)
                val m = me
                if (m == null) Text(gpsMsg, fontSize = 13.sp)
                else {
                    val dist = Geo.distanceM(fix.lat!!, fix.lon!!, m.latitude, m.longitude)
                    val bearing = Geo.bearingDeg(fix.lat, fix.lon, m.latitude, m.longitude)
                    val rel = ((bearing - heading + 360) % 360).toFloat()
                    Arrow(rel)
                    CompassDial(heading, null, bearing, compass.accuracy.value <= 1)
                    val turn = when { rel < 20 || rel > 340 -> "straight ahead"; rel < 160 -> "turn right ${Math.round(rel)}°"; rel > 200 -> "turn left ${Math.round(360 - rel)}°"; else -> "behind you" }
                    Text("${Math.round(dist)} m away - $turn  (${Geo.compass(bearing)})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Meter((1 - (dist / 500.0).coerceIn(0.0, 1.0)).toFloat(), "closer = fuller bar (0-500 m)")
                    if (!compass.available) Text("No compass sensor on this phone: use the compass direction (${Geo.compass(bearing)}) instead of the arrow.", fontSize = 12.sp)
                    else Text("Hold the phone flat so the arrow is accurate. It follows the drone as it moves.", fontSize = 11.sp)
                }
                TextButton(onClick = { openInMaps(ctx, fix.lat!!, fix.lon!!, "Drone claim ${fix.id}") }) { Text("Open the drone's position in Maps") }
                fix.opLat?.let { la -> fix.opLon?.let { lo ->
                    TextButton(onClick = { openInMaps(ctx, la, lo, "Claimed operator position") }) { Text("Open the claimed operator position in Maps") }
                } }
                Text("Safety: don't approach, chase or confront whoever may be flying it - the operator position is just a claim. Note the time, take photos from a safe distance, and report it to local law enforcement or the FAA. Never interfere with a drone.", fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

/** An arrow that points where to go; straight up = straight ahead of you. */
@Composable
private fun Arrow(relativeDeg: Float) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.8f)) {
        val c = Offset(size.width / 2, size.height / 2); val r = size.height / 2 - 6.dp.toPx()
        drawCircle(UiColors.ring, r, c, style = Stroke(1.dp.toPx()))
        val a = Math.toRadians(relativeDeg - 90.0)
        val tip = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        val l = Math.toRadians(relativeDeg - 90.0 + 150); val rr = Math.toRadians(relativeDeg - 90.0 - 150)
        val head = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + r * 0.35f * cos(l).toFloat(), tip.y + r * 0.35f * sin(l).toFloat())
            lineTo(tip.x + r * 0.35f * cos(rr).toFloat(), tip.y + r * 0.35f * sin(rr).toFloat()); close()
        }
        drawLine(UiColors.alert, c, tip, 6.dp.toPx())
        drawPath(head, UiColors.alert)
        drawCircle(UiColors.good, 5.dp.toPx(), c)
    }
}

@Composable
private fun WifiHunt(onClose: () -> Unit) {
    LaunchedEffect(Unit) { while (true) { Monitor.refresh(); delay(1000) } }
    val s = Monitor.snapshot
    val t = s.wifi.firstOrNull { it.klass == "camera" || it.klass == "drone" } ?: s.wifi.firstOrNull()
    val history = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    var lastAt by remember { mutableLongStateOf(0L) }
    val tgt = t?.bssid
    val rssi = s.wifi.firstOrNull { it.bssid == tgt }?.level
    if (rssi != null && s.wifiAt != lastAt) { lastAt = s.wifiAt; history.add(rssi); if (history.size > 8) history.removeAt(0) }
    if (t == null) { Notice("No Wi-Fi network to track right now.", onClose); return }
    val trend = Trend.of(history.toList())
    AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = onClose,
        title = { Text("Find: ${t.ssid}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${t.bssid}${if (t.vendor.isNotEmpty()) " · ${t.vendor}" else ""}", fontSize = 12.sp)
                if (rssi == null) Text("Not seen in the latest scan. Move a few steps and wait.")
                else {
                    Meter((rssi + 100) / 60f, "$rssi dBm - " + when { rssi >= -45 -> "very hot: it's right here"; rssi >= -58 -> "hot"; rssi >= -72 -> "warm"; else -> "cold" })
                    Text(when (trend) {
                        "warmer" -> "▲ Getting WARMER - keep going this way."
                        "colder" -> "▼ Getting COLDER - turn around."
                        "steady" -> "● No change yet."
                        else -> "Walk a few steps, then wait for the next scan."
                    }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Android only refreshes Wi-Fi every ~15-30 seconds, so move a few steps, then watch the next reading. A camera-like network is usually the camera itself broadcasting - check where the signal peaks. If you find something you don't own, leave it, photograph it, and contact law enforcement.", fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
