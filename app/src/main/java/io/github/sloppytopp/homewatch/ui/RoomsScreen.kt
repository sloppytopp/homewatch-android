package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.data.Store
import io.github.sloppytopp.homewatch.detect.RoomBook
import io.github.sloppytopp.homewatch.detect.SweepCollector
import io.github.sloppytopp.homewatch.scan.Monitor
import kotlinx.coroutines.delay

private const val SWEEP_SECONDS = 45

@Composable
fun RoomsScreen() {
    val ctx = LocalContext.current
    val s = Monitor.snapshot
    val now = rememberNow()
    var version by remember { mutableIntStateOf(0) }
    var sweeping by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<String?>(null) }
    var newRoom by remember { mutableStateOf("") }
    val latest = remember(version) { Store.latestScans() }

    LaunchedEffect(sweeping) {
        val room = sweeping ?: return@LaunchedEffect
        Monitor.setInspect(ctx, true); Monitor.fastWifi = true
        val c = SweepCollector()
        try {
            repeat(SWEEP_SECONDS) { delay(1000); Monitor.refresh(); c.sample(Monitor.snapshot) }
            val scan = c.result(room, System.currentTimeMillis())
            val prev = Store.previousScan(room)
            Store.addRoomScan(scan)
            val d = RoomBook.diff(prev, scan)
            result = buildString {
                append("$room swept: ${scan.items.size} signals heard (${scan.items.count { it.kind == "wifi" }} Wi-Fi, ${scan.items.count { it.kind != "wifi" }} Bluetooth).\n")
                if (prev == null) append("This is the first sweep of $room - it is now your baseline. Sweep it again later, or sweep your other rooms, to compare.")
                else {
                    if (d.new.isEmpty() && d.gone.isEmpty() && d.louder.isEmpty()) append("Nothing changed since the last sweep of this room.")
                    d.new.take(8).forEach { append("NEW since last time: ${it.label} (${Math.round(it.rssi)} dBm)\n") }
                    d.louder.take(5).forEach { (i, up) -> append("LOUDER than before: ${i.label} (+${Math.round(up)} dB)\n") }
                    d.gone.take(5).forEach { append("Gone: ${it.label}\n") }
                }
            }.trim()
        } finally {
            Monitor.fastWifi = false; Monitor.setInspect(ctx, false)
            sweeping = null; version++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Room sweeps", color = UiColors.text, fontSize = 20.sp)
        Text("Walk into a room, tap Sweep, and hold the phone still for 45 seconds. N0RMA remembers every Wi-Fi network and Bluetooth device it hears there. " +
            "Sweep each room once for a baseline, then again whenever you want to check: anything NEW in a room stands out. " +
            "With two or more rooms swept, it can also tell you which room a device is probably in - without GPS.", color = UiColors.dim, fontSize = 12.sp)
        if (!s.running) Text("Start scanning on the Status tab first.", color = UiColors.warn, fontSize = 13.sp)

        val sw = sweeping
        if (sw != null) {
            val frac = ((now - started) / (SWEEP_SECONDS * 1000f)).coerceIn(0f, 1f)
            Meter(frac, "Sweeping $sw... ${(SWEEP_SECONDS - (now - started) / 1000).coerceAtLeast(0)} s left - hold still")
        }
        result?.let { Text(it, color = UiColors.text, fontSize = 13.sp) }

        Prefs.rooms.forEach { room ->
            val last = latest[room]
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text(room, color = UiColors.text, fontSize = 16.sp)
                Text(if (last == null) "Not swept yet" else "Last swept ${ago(now - last.ts)} ago - ${last.items.size} signals", color = UiColors.dim, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { result = null; started = System.currentTimeMillis(); sweeping = room },
                        enabled = s.running && sweeping == null,
                        colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
                    ) { Text("Sweep this room", fontSize = 13.sp) }
                    TextButton(onClick = { Store.deleteRoom(room); Prefs.removeRoom(room); version++ }, enabled = sweeping == null) { Text("Remove", color = UiColors.warn) }
                }
            }
        }

        val suggestions = listOf("Bedroom", "Living room", "Kitchen", "Bathroom", "Garage", "Car").filter { it !in Prefs.rooms }
        if (suggestions.isNotEmpty()) {
            Text("Add a room:", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestions.take(4).forEach { n -> TextButton(onClick = { Prefs.addRoom(n) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text(n, fontSize = 12.sp) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newRoom, onValueChange = { newRoom = it.take(24) }, label = { Text("Or type a name") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { Prefs.addRoom(newRoom); newRoom = "" }, enabled = newRoom.isNotBlank()) { Text("Add") }
        }

        val specific = remember(version) { RoomBook.roomSpecific(latest) }
        if (latest.size >= 2) {
            Text("Which room is it in?", color = UiColors.text, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Devices that are clearly loudest in one room (by signal strength - a good hint, not proof):", color = UiColors.dim, fontSize = 12.sp)
            if (specific.isEmpty()) Text("Nothing stands out yet.", color = UiColors.dim, fontSize = 12.sp)
            specific.take(14).forEach { (i, l) ->
                Text("${i.label} - probably in ${l.room} (${Math.round(l.rssi)} dBm" + (l.marginDb?.let { ", ${Math.round(it)} dB louder than the next room" } ?: ", heard only here") + ")",
                    color = if (i.kind == "tracker") UiColors.watch else UiColors.text, fontSize = 12.sp)
            }
        }
        Text("Room data stays on this phone; \"Delete all history\" erases it too. Bluetooth phones and watches change address often, so unnamed Bluetooth devices are left out of comparisons.",
            color = UiColors.faint, fontSize = 11.sp)
    }
}
