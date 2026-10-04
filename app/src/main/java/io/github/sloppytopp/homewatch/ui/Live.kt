package io.github.sloppytopp.homewatch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.detect.Level as DLevel
import io.github.sloppytopp.homewatch.detect.Snapshot
import kotlinx.coroutines.delay

/** Ticks once a second so "scanning for 4m 12s" keeps moving between engine refreshes. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    return now
}

fun signalBars(rssi: Int): String = when {
    rssi >= -55 -> "▂▄▆█"
    rssi >= -67 -> "▂▄▆"
    rssi >= -80 -> "▂▄"
    else -> "▂"
}

fun ago(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}

/** A small breathing dot: the "it is alive" signal. Quiet and dim - never a bright flash. */
@Composable
fun Heartbeat(running: Boolean) {
    val a by rememberInfiniteTransition(label = "hb").animateFloat(
        initialValue = 0.25f, targetValue = 1f, label = "a",
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
    )
    Box(Modifier.size(10.dp).alpha(if (running) a else 1f).clip(CircleShape).background(if (running) UiColors.good else UiColors.neutral))
}

private fun worst(s: Snapshot): DLevel = listOf(s.drone.level, s.tracker.level, s.camera.level).maxByOrNull { it.ordinal } ?: DLevel.OFF

/** One plain-language answer plus proof the app is working. */
@Composable
fun LiveBanner(s: Snapshot) {
    val now = rememberNow()
    val lvl = if (!s.running) DLevel.OFF else worst(s)
    val (head, accentLevel) = when {
        !s.running -> "Not scanning" to Level.OFF
        lvl == DLevel.ALERT -> "Needs your attention" to Level.ALERT
        lvl == DLevel.WATCH -> "Keeping an eye on something" to Level.WATCH
        else -> "All clear" to Level.OK
    }
    val sub = if (!s.running) "Tap Start scanning to check what's around you."
    else buildString {
        append("Scanning for ${ago(now - s.startedAt)}")
        append("  ·  Wi-Fi: ${s.wifi.size} networks")
        if (s.wifiAt > 0) append(" (scanned ${ago(now - s.wifiAt)} ago)")
        append("  ·  Bluetooth: ${s.adsSeen} signals heard")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accentLevel.containerC()).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heartbeat(s.running)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(head, color = accentLevel.accentC(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = UiColors.dim, fontSize = 12.sp)
        }
    }
}

fun openInMaps(ctx: Context, lat: Double, lon: Double, label: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

@Composable
fun Meter(fraction: Float, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(UiColors.ring)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(22.dp).clip(RoundedCornerShape(11.dp))
                .background(if (fraction > 0.75f) UiColors.alert else if (fraction > 0.45f) UiColors.watch else UiColors.good))
        }
        Text(label, color = UiColors.text, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
