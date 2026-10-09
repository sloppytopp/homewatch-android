package io.github.sloppytopp.homewatch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.detect.SmartDevices
import io.github.sloppytopp.homewatch.detect.SmartGroup
import io.github.sloppytopp.homewatch.scan.Monitor

private class Check(val title: String, val steps: String)

private val CHECKLIST = listOf(
    Check("Amazon Sidewalk", "Alexa app > More > Settings > Account Settings > Amazon Sidewalk > turn it off. Sidewalk lets Echo and Ring devices share part of your internet connection with nearby Amazon devices. Amazon says it is encrypted; turning it off removes the sharing. Menu names change between app versions."),
    Check("Ring and Alexa accounts", "In the Ring app open Control Center and review Shared Users and Authorized Client Devices; remove anyone you don't recognise and turn on video encryption if offered. In the Alexa app open Alexa Privacy to review and delete voice history and see which skills have access."),
    Check("Microphones and cameras", "Use the mute button on speakers when you don't need them, and keep them out of bedrooms and private rooms. Cover or unplug cameras you aren't using."),
    Check("Your router", "Open your router's connected-devices list and look for anything you don't recognise. Put smart gadgets on a guest network, change default passwords, and install firmware updates."),
    Check("Old and unused gadgets", "Factory-reset and unplug devices you no longer use, and delete their accounts. Unmanaged gadgets are the easiest to take over."),
)

/** What smart gadgets are around, plus a privacy checklist. Names are hints, not proof. */
@Composable
fun SmartScreen() {
    val ctx = LocalContext.current
    val s = Monitor.snapshot
    var open by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf<Pair<String, String>?>(null) }
    DisposableEffect(Unit) { onDispose { Monitor.setInspect(ctx, false) } }

    val items = SmartDevices.fromSnapshot(s)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Smart devices and privacy", color = UiColors.text, fontSize = 20.sp)
        Text("Gadgets that listen, watch or share your connection. N0RMA sorts what it hears by network name, Bluetooth name and maker. " +
            "A name match is a hint, not proof; a device that hides its name won't show up here.", color = UiColors.dim, fontSize = 12.sp)
        if (!s.running) Text("Start scanning on the Status tab first.", color = UiColors.warn, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Also listen for Bluetooth gadgets", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = Monitor.inspecting, onCheckedChange = { Monitor.setInspect(ctx, it) }, enabled = s.running)
        }

        SmartGroup.values().forEach { g ->
            val rows = items.filter { it.group == g }
            if (rows.isNotEmpty()) {
                Text("${g.title} (${rows.size})", color = UiColors.text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                Text(g.blurb, color = UiColors.faint, fontSize = 11.sp)
                rows.take(12).forEach { r ->
                    DeviceRowSimple(signalBars(r.rssi), r.title, r.sub, r.rssi) { finding = r.addr to r.title }
                }
            }
        }
        if (s.running && items.isEmpty()) Text("No recognisable smart devices heard yet. Wi-Fi refreshes every ~30 s; turn on Bluetooth listening above and wait a minute.", color = UiColors.dim, fontSize = 12.sp)

        Text("Privacy checklist", color = UiColors.text, fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp))
        CHECKLIST.forEach { c ->
            TextButton(onClick = { open = if (open == c.title) null else c.title }) { Text((if (open == c.title) "▾  " else "▸  ") + c.title, color = UiColors.text) }
            if (open == c.title) Text(c.steps, color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }

        Text("License-plate cameras", color = UiColors.text, fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp))
        Text("DeFlock is a community map of automatic license-plate readers (such as Flock cameras), built on OpenStreetMap. " +
            "This opens it in your browser. N0RMA itself sends nothing; the map site will see your visit like any website.", color = UiColors.dim, fontSize = 12.sp)
        TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.deflock.org")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
            Text("Open the DeFlock map in your browser")
        }
    }
    finding?.let { (addr, label) -> Finder(addr, label) { finding = null } }
}

@Composable
private fun DeviceRowSimple(bars: String, title: String, sub: String, rssi: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("$bars  $title   $rssi dBm", color = UiColors.text, fontSize = 13.sp)
            Text(sub + "  ·  tap to find it", color = UiColors.faint, fontSize = 11.sp)
        }
    }
}
