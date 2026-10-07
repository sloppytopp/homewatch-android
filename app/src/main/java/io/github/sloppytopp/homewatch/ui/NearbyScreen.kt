package io.github.sloppytopp.homewatch.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.sloppytopp.homewatch.detect.DirectionFinder
import io.github.sloppytopp.homewatch.detect.Geo
import io.github.sloppytopp.homewatch.detect.Trend
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.scan.Monitor
import kotlinx.coroutines.delay

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color) =
    Text(" $text ", color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun DeviceRow(bars: String, title: String, sub: String, rssi: Int, chip: String? = null, chipColor: androidx.compose.ui.graphics.Color = UiColors.dim, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(bars, color = UiColors.good, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Row { Text(title, color = UiColors.text, fontSize = 14.sp); if (chip != null) Chip(chip, chipColor) }
            Text(sub, color = UiColors.dim, fontSize = 11.sp)
        }
        Text("$rssi dBm", color = UiColors.dim, fontSize = 11.sp)
    }
}

@Composable
fun NearbyScreen() {
    val ctx = LocalContext.current
    val s = Monitor.snapshot
    val now = rememberNow()
    var finding by remember { mutableStateOf<Pair<String, String>?>(null) }
    var wifiDetail by remember { mutableStateOf<io.github.sloppytopp.homewatch.detect.WifiRow?>(null) }
    DisposableEffect(Unit) { onDispose { Monitor.setInspect(ctx, false) } } // unfiltered scan only while this screen is open

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("What's around you", color = UiColors.text, fontSize = 20.sp)
        if (!s.running) Text("Start scanning on the Status tab to fill these lists.", color = UiColors.warn, fontSize = 13.sp)

        Text("Wi-Fi networks (${s.wifi.size})" + if (s.wifiAt > 0) " - scanned ${ago(now - s.wifiAt)} ago" else "", color = UiColors.text, fontSize = 15.sp)
        Text("Tap a network for details: mark it as yours, or look it up on WiGLE. Android only allows a Wi-Fi scan every ~30 s.", color = UiColors.faint, fontSize = 11.sp)
        if (s.wifi.isEmpty()) Text(s.camera.message.takeIf { s.camera.level == io.github.sloppytopp.homewatch.detect.Level.OFF } ?: "No networks yet...", color = UiColors.dim, fontSize = 12.sp)
        s.wifi.forEach { w ->
            val mine = w.ssid in Prefs.mySsids
            DeviceRow(
                signalBars(w.level), w.ssid, "${w.bssid}${if (w.vendor.isNotEmpty()) " · ${w.vendor}" else ""}", w.level,
                chip = when { mine -> "YOURS"; w.klass == "camera" -> "CAMERA-LIKE"; w.klass == "drone" -> "DRONE-LIKE"; else -> null },
                chipColor = if (mine) UiColors.good else UiColors.alert,
                onClick = { wifiDetail = w },
            )
        }

        Text("Bluetooth", color = UiColors.text, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Show every Bluetooth device (phones, watches, gadgets)", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = Monitor.inspecting, onCheckedChange = { Monitor.setInspect(ctx, it) }, enabled = s.running)
        }
        Text("Tap any Bluetooth device to hunt for it with the hot/cold finder. Phones and watches change their address every few minutes, so one device can appear more than once.",
            color = UiColors.faint, fontSize = 11.sp)
        s.drones.filter { it.via == "Bluetooth" }.forEach { d ->
            DeviceRow(signalBars(d.rssi), "DRONE (claims Remote ID)", "${d.info.basicId ?: d.addr}", d.rssi, "DRONE", UiColors.alert) { finding = d.addr to "Drone broadcast" }
        }
        s.trackers.forEach { t ->
            DeviceRow(signalBars(t.rssi), t.label, "${t.addr} · seen ${ago(t.seenS * 1000)}", t.rssi, if (t.mine) "YOURS" else "TRACKER", if (t.mine) UiColors.good else UiColors.watch) { finding = t.addr to t.label }
        }
        if (Monitor.inspecting) {
            val known = s.trackers.map { it.addr }.toSet()
            val rows = s.inspect.filter { it.addr !in known }
            Text("${rows.size} other Bluetooth devices heard in the last minute", color = UiColors.dim, fontSize = 12.sp)
            rows.take(60).forEach { r ->
                DeviceRow(signalBars(r.rssi), r.name.ifEmpty { "(no name)" }, "${r.addr}${if (r.company.isNotEmpty()) " · ${r.company}" else ""}", r.rssi, onClick = { finding = r.addr to r.name.ifEmpty { r.company.ifEmpty { "Bluetooth device" } } })
            }
        } else if (s.trackers.isEmpty() && s.drones.none { it.via == "Bluetooth" }) {
            Text("No trackers or drones in range. Turn on \"Show every Bluetooth device\" to see all of them.", color = UiColors.dim, fontSize = 12.sp)
        }
    }

    finding?.let { (addr, label) -> Finder(addr, label) { finding = null } }
    wifiDetail?.let { w ->
        val mine = w.ssid in Prefs.mySsids
        AlertDialog(
            containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
            onDismissRequest = { wifiDetail = null }, title = { Text(w.ssid) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("MAC (BSSID): ${w.bssid}\nMaker: ${w.vendor.ifEmpty { "unknown" }}\nSignal: ${w.level} dBm\nSecurity: ${w.caps.ifEmpty { "unknown" }}" +
                        (if (w.klass == "camera" || w.klass == "drone") "\nLooks like a ${w.klass} (by name or maker)" else ""), fontSize = 13.sp)
                    Text("Where is it? Two in-house ways to find out, using only this phone's own radio - no account, nothing sent anywhere:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { wifiDetail = null; finding = "wifi:${w.bssid}" to w.ssid }) { Text("Find it now (hot/cold, right here)") }
                    TextButton(onClick = { Monitor.requestTab = 3; wifiDetail = null }) { Text("Walk a Survey -> real position + Google Earth pin") }
                    TextButton(onClick = { if (mine) Prefs.forgetSsid(w.ssid) else Prefs.learnSsid(w.ssid); wifiDetail = null }) {
                        Text(if (mine) "Not mine - stop showing it as yours" else "This is my network")
                    }
                    Text("Advanced: WiGLE is a public database of networks other people have logged. A network WiGLE has never seen near you is worth a closer look - but it needs a free WiGLE account, and you " +
                        "have to paste the MAC into its search box yourself (WiGLE doesn't support filling it in for us). Only worth it once the two options above haven't settled it.", fontSize = 11.sp, color = UiColors.faint)
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("MAC", w.bssid))
                        try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wigle.net/search")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                    }) { Text("Copy MAC and open wigle.net/search", fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { wifiDetail = null }) { Text("Close") } },
        )
    }
}

/** Hot/cold finder with a warmer/colder trend and an optional turn-in-place compass sweep. */
@Composable
internal fun Finder(addr: String, label: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val compass = rememberCompass()
    val heading by compass.heading
    val hasCompass = compass.available
    val history = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    var sweeping by remember { mutableStateOf(false) }
    var sweepAt by remember { mutableLongStateOf(0L) }
    val finder = remember { DirectionFinder() }
    var answer by remember { mutableStateOf<DirectionFinder.Result?>(null) }
    var answered by remember { mutableStateOf(false) }

    val isWifi = addr.startsWith("wifi:")
    val bssid = addr.removePrefix("wifi:")

    fun rssiNow(): Pair<Int?, Long?> {
        val s = Monitor.snapshot
        if (isWifi) {
            if (s.wifiAt == 0L) return null to null
            return s.wifi.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }?.level to (System.currentTimeMillis() - s.wifiAt) / 1000
        }
        s.trackers.firstOrNull { it.addr == addr }?.let { return it.rssi to it.agoS }
        s.inspect.firstOrNull { it.addr == addr }?.let { return it.rssi to it.ageS }
        s.drones.firstOrNull { it.addr == addr }?.let { return it.rssi to it.agoS }
        return null to null
    }

    // Wi-Fi scans land roughly every 15-30 s (Android throttles them), not every second like BLE - ask for the faster cadence while this is open.
    DisposableEffect(isWifi) {
        val was = Monitor.fastWifi
        if (isWifi) Monitor.fastWifi = true
        onDispose { if (isWifi) Monitor.fastWifi = was }
    }

    LaunchedEffect(addr) {
        while (true) {
            Monitor.refresh()
            val (r, age) = rssiNow()
            if (r != null && age != null && age <= (if (isWifi) 40 else 1)) {
                history.add(r); if (history.size > 12) history.removeAt(0)
                if (sweeping) finder.add(heading.toDouble(), r)
            }
            if (sweeping && System.currentTimeMillis() - sweepAt > 40_000) { sweeping = false; answer = finder.result(); answered = true }
            delay(500)
        }
    }

    val (rssi, age) = rssiNow()
    val trend = Trend.of(history.toList())
    AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = onClose,
        title = { Text("Finding: $label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(addr, fontSize = 12.sp)
                if (rssi == null) Text("Not heard right now. Wait a few seconds, or move closer.")
                else {
                    Meter(((rssi + 100) / 60f), "$rssi dBm - " + when { rssi >= -50 -> "very hot: it's right here"; rssi >= -62 -> "hot"; rssi >= -75 -> "warm"; else -> "cold" } +
                        (age?.let { if (it > (if (isWifi) 20 else 3)) " (last scan ${it}s ago)" else "" } ?: ""))
                    Text(when (trend) {
                        "warmer" -> "▲ Getting WARMER - keep going this way."
                        "colder" -> "▼ Getting COLDER - turn around."
                        "steady" -> "● No change yet - take a few steps in any direction."
                        else -> "Take a few steps: the arrow will tell you if you're getting warmer or colder."
                    }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (rssi < -78) Text("Heads up: at $rssi dBm it is faint - most likely in another building or far room. Direction hints get reliable once you're closer than about -70 dBm, so first walk toward wherever it gets stronger.", fontSize = 12.sp)
                }

                val likely = remember(addr) { io.github.sloppytopp.homewatch.detect.RoomBook.likelyRoom("ble:$addr", io.github.sloppytopp.homewatch.data.Store.latestScans()) }
                likely?.let { l ->
                    Text("From your room sweeps: probably in ${l.room} (${Math.round(l.rssi)} dBm" + (l.marginDb?.let { ", ${Math.round(it)} dB louder than the next room" } ?: ", heard only there") + ").",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                val mineNow = addr in Prefs.myDevices
                TextButton(onClick = { if (mineNow) Prefs.unmarkMine(addr) else Prefs.markMine(addr); Monitor.refresh() }) {
                    Text(if (mineNow) "Marked as YOURS - tap to flag it again" else "This is mine - stop flagging it")
                }
                if (isWifi) {
                    Text("Wi-Fi only refreshes every 15-30 seconds (a phone limit, not ours), so the compass sweep doesn't work well here - use warmer/colder above, or get a real position estimate:", fontSize = 12.sp)
                    TextButton(onClick = { Monitor.requestTab = 3; onClose() }) { Text("Open Survey walk (Radar tab) -> export to Google Earth") }
                } else {
                Text("Compass sweep", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                if (hasCompass) CompassDial(heading, finder, answer?.bearingDeg, compass.accuracy.value <= 1)
                if (!hasCompass) Text("This phone has no compass sensor, so use the warmer/colder arrow.", fontSize = 12.sp)
                else if (sweeping) {
                    val left = (40 - (System.currentTimeMillis() - sweepAt) / 1000).coerceAtLeast(0)
                    Text("Turn slowly in a full circle, phone flat in front of you. ${finder.coveredSectors()}/8 directions covered - $left s left. The orange wedges grow toward the strongest signal.", fontSize = 12.sp)
                    TextButton(onClick = { sweeping = false; answer = finder.result(); answered = true }) { Text("Finish now") }
                } else {
                    Text("Hold the phone flat, then turn slowly in place for a full circle. Your body blocks the signal, so it is usually strongest when you face the tracker.", fontSize = 12.sp)
                    answer?.let { r ->
                        Text(if (r.confident) "Strongest signal is toward ${Geo.compass(r.bearingDeg)} (about ${r.bearingDeg.toInt()}°). Walk that way a few steps, then check the warmer/colder arrow."
                        else "No clear direction (signal varied only ${"%.1f".format(r.spreadDb)} dB around the circle). Walk a few steps and sweep again, or rely on warmer/colder.", fontSize = 13.sp)
                    }
                    if (answered && answer == null) Text("Not enough readings - turn slower, and make sure the tracker is being heard (it advertises about every 2 seconds).", fontSize = 12.sp)
                    TextButton(onClick = { answered = false; answer = null; finder.let { f -> repeat(0) {} }; sweepAt = System.currentTimeMillis(); sweeping = true; resetFinder(finder) }) { Text(if (answered) "Sweep again" else "Start compass sweep") }
                }
                }
                Text("If you find something you don't own, leave it in place, photograph it, and contact local law enforcement.", fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

private fun resetFinder(f: DirectionFinder) = f.reset()
