package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.data.Store
import io.github.sloppytopp.homewatch.detect.Evidence
import io.github.sloppytopp.homewatch.detect.Follow
import io.github.sloppytopp.homewatch.detect.Report
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    var v by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val events = remember(v) { Store.events(now - 72 * 3_600_000L) }
    val beeps = remember(v) { Store.beeps(now - 72 * 3_600_000L) }
    val report = remember(v) { Report.build(beeps, events) }
    val f = SimpleDateFormat("EEE HH:mm:ss", Locale.US)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sensor beep log", color = UiColors.text, fontSize = 16.sp)
        Text("Tap each time your motion sensor beeps. Homewatch then checks whether anything it detects lines up with the beeps - and how often something is nearby anyway.",
            color = UiColors.dim, fontSize = 12.sp)
        Button(
            onClick = { Store.addBeep(); v++ }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
        ) { Text("I heard my sensor beep - log it now") }
        Text("Beeps logged (72 h): ${beeps.size}", color = UiColors.dim, fontSize = 12.sp)
        report.forEach { Text(it, color = UiColors.text, fontSize = 12.sp) }

        Text("Evidence for police or an advocate", color = UiColors.text, fontSize = 16.sp)
        Text("Makes a plain-text report of everything flagged (30 days) with a tamper-evident hash chain. You choose where to send it; nothing is sent automatically.", color = UiColors.dim, fontSize = 12.sp)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Button(
            onClick = {
                val rpt = Evidence.build(System.currentTimeMillis(), Store.events(now - 30 * 86_400_000L, 1500), Follow.analyze(Store.trail()), Store.trail())
                ctx.startActivity(android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Homewatch evidence report").putExtra(android.content.Intent.EXTRA_TEXT, rpt), "Share report"))
            }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
        ) { Text("Export evidence report") }

        FollowCard(remember(v) { Follow.analyze(Store.trail()) })
        Text("Recent events", color = UiColors.text, fontSize = 16.sp)
        if (events.isEmpty()) Text("Nothing flagged yet.", color = UiColors.dim, fontSize = 12.sp)
        events.take(40).forEach { Text("${f.format(Date(it.ts))}  ${it.level.name.lowercase()}  ${it.domain}: ${it.msg}", color = UiColors.text, fontSize = 12.sp) }

        Text("History stays on this phone and is deleted after 30 days. Drone operator positions are never saved.", color = UiColors.faint, fontSize = 11.sp)
        TextButton(onClick = { confirm = true }) { Text("Delete all history now", color = UiColors.warn) }
    }
    if (confirm) AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = { confirm = false }, title = { Text("Delete all history?") },
        text = { Text("This removes every saved event and beep from this phone. It cannot be undone.") },
        confirmButton = { TextButton(onClick = { Store.clearAll(); v++; confirm = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
