package io.github.sloppytopp.homewatch.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.data.Store
import io.github.sloppytopp.homewatch.detect.Estimate
import io.github.sloppytopp.homewatch.detect.Export
import io.github.sloppytopp.homewatch.detect.Geo
import io.github.sloppytopp.homewatch.detect.Survey
import io.github.sloppytopp.homewatch.detect.SurveyPoint
import io.github.sloppytopp.homewatch.scan.Monitor
import kotlinx.coroutines.delay

private fun write(ctx: Context, uri: Uri, text: String): Boolean =
    try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null } catch (e: Exception) { false }

/** Walk around, let N0RMA log what it hears and where, see rough source positions, export to Google Earth / WiGLE. */
@Composable
fun SurveyCard() {
    val ctx = LocalContext.current
    val s = Monitor.snapshot
    var v by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var wigleWarn by remember { mutableStateOf(false) }
    var clearAsk by remember { mutableStateOf(false) }

    LaunchedEffect(Monitor.surveying) { while (Monitor.surveying) { delay(4000); v++ } }
    val points = remember(v, Monitor.surveyCount) { Store.survey() }
    val allEst = remember(points) { Survey.estimate(points) }
    val clustered = remember(allEst) { Survey.clustered(allEst) }
    val est = if (clustered) emptyList() else allEst

    val kmlOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri ->
        msg = if (uri != null && pending != null) (if (write(ctx, uri, pending!!)) "Saved. Open the file in Google Earth (or any KML viewer)." else "Couldn't save the file.") else null
    }
    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        msg = if (uri != null && pending != null) (if (write(ctx, uri, pending!!)) "Saved. Nothing was uploaded - you decide what to do with the file." else "Couldn't save the file.") else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Survey walk", color = UiColors.text, fontSize = 16.sp)
        Text("Walk around your property or street while this is on. N0RMA logs every Wi-Fi network and tracker it hears together with where you were (GPS), then estimates roughly where each one is. " +
            "Everything stays on this phone until YOU export it.", color = UiColors.dim, fontSize = 12.sp)
        if (!s.running) Text("Start scanning on the Status tab first.", color = UiColors.warn, fontSize = 13.sp)
        Button(
            onClick = { Monitor.surveying = !Monitor.surveying; if (Monitor.surveying) Monitor.fastWifi = true else Monitor.fastWifi = false },
            enabled = s.running, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
        ) { Text(if (Monitor.surveying) "Stop survey" else "Start survey walk") }
        val loc = Monitor.lastLoc
        Text(
            (if (Monitor.surveying) "Logging: ${Monitor.surveyCount} sightings" + (loc?.let { " - GPS accurate to about ${Math.round(it.accuracy)} m" } ?: "") else "${points.size} sightings saved from earlier surveys") +
                (Monitor.surveyNote?.let { "\n$it" } ?: ""), color = UiColors.text, fontSize = 12.sp,
        )

        if (points.isNotEmpty()) {
            SurveyMap(points, est)
            if (clustered) Text("Every source landed in the same spot, so no positions are shown: that happens when you stay in one place, or when GPS drifts indoors. " +
                "For real estimates, walk outside along your property or street for 30+ metres with a clear sky overhead.", color = UiColors.warn, fontSize = 12.sp)
            else Text("Estimated sources (rough - walls and antennas distort signal strength):", color = UiColors.dim, fontSize = 12.sp)
            if (est.isEmpty() && !clustered) Text("Not enough movement yet. Walk 30+ metres in different directions, outdoors if you can.", color = UiColors.dim, fontSize = 12.sp)
            val home = Prefs.home
            est.take(10).forEach { e ->
                val where = if (home != null) "${Math.round(Geo.distanceM(e.lat, e.lon, home.lat, home.lon))} m ${Geo.compass(Geo.bearingDeg(e.lat, e.lon, home.lat, home.lon))} of home"
                else "%.5f, %.5f".format(e.lat, e.lon)
                Text("${e.label.ifEmpty { e.key }} - $where (+/- ${Math.round(e.radiusM)} m, strongest ${e.bestRssi} dBm, ${e.samples} samples)" +
                    (if (e.klass == "camera" || e.klass == "drone") "  [${e.klass}-like]" else ""),
                    color = if (e.klass == "camera" || e.klass == "drone") UiColors.alert else UiColors.text, fontSize = 12.sp)
            }

            Text("Export", color = UiColors.text, fontSize = 14.sp)
            Button(
                onClick = {
                    pending = Export.kml(Prefs.home?.let { it.lat to it.lon }, points, est, System.currentTimeMillis()); kmlOut.launch("homewatch-survey.kml")
                }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF111111), contentColor = UiColors.buttonFg),
            ) { Text("Save for Google Earth (.kml)") }
            Button(
                onClick = { wigleWarn = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF111111), contentColor = UiColors.buttonFg),
            ) { Text("Save a WiGLE file (.csv)") }
            TextButton(onClick = { clearAsk = true }) { Text("Delete survey data", color = UiColors.warn) }
        }
        msg?.let { Text(it, color = UiColors.warn, fontSize = 12.sp) }
        Text("Survey points include your GPS trail, so they are sensitive. They are deleted after 30 days, and by \"Delete all history\".", color = UiColors.faint, fontSize = 11.sp)
    }

    if (wigleWarn) AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = { wigleWarn = false }, title = { Text("About WiGLE files") },
        text = {
            Text("This only SAVES a file on your phone - nothing is uploaded.\n\nWiGLE (wigle.net) is a public map of Wi-Fi networks. If you upload this file, the networks you heard - including your own home network - and the places you walked become visible on a public map. " +
                "If you're trying to stay hidden, do not upload a survey made at or near your home.\n\nUseful, safer use: open wigle.net yourself and search a network's MAC address to see whether it has been in this area for years (long-standing neighbor) or is new.")
        },
        confirmButton = {
            TextButton(onClick = {
                wigleWarn = false
                pending = Export.wigleCsv(points, "N0RMA 0.4", Build.MODEL, Build.VERSION.RELEASE, Build.DEVICE, Build.MANUFACTURER); csvOut.launch("homewatch-wigle.csv")
            }) { Text("Save the file") }
        },
        dismissButton = { TextButton(onClick = { wigleWarn = false }) { Text("Cancel") } },
    )
    if (clearAsk) AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = { clearAsk = false }, title = { Text("Delete survey data?") }, text = { Text("This removes every saved sighting and your GPS trail from this phone.") },
        confirmButton = { TextButton(onClick = { Store.clearSurvey(); Monitor.surveyCount = 0; v++; clearAsk = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { clearAsk = false }) { Text("Cancel") } },
    )
}

/** Plain local plot: your walk path, home, and estimated sources (red = camera/drone-like). No map tiles, nothing downloaded. */
@Composable
private fun SurveyMap(points: List<SurveyPoint>, est: List<Estimate>) {
    val tm = rememberTextMeasurer()
    val label = TextStyle(color = UiColors.dim, fontSize = 10.sp)
    val home = Prefs.home
    val lats = points.map { it.lat } + est.map { it.lat } + listOfNotNull(home?.lat)
    val lons = points.map { it.lon } + est.map { it.lon } + listOfNotNull(home?.lon)
    val la0 = lats.average(); val lo0 = lons.average()
    fun off(la: Double, lo: Double) = Geo.offsetM(la, lo, la0, lo0)
    val all = lats.indices.map { off(lats[it], lons[it]) }
    val half = maxOf(30.0, all.maxOf { maxOf(Math.abs(it.first), Math.abs(it.second)) } * 1.2)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f)) {
        val c = Offset(size.width / 2, size.height / 2); val sc = (size.minDimension / 2 - 12.dp.toPx()) / half.toFloat()
        fun pos(la: Double, lo: Double): Offset { val (e, n) = off(la, lo); return Offset(c.x + e.toFloat() * sc, c.y - n.toFloat() * sc) }
        drawRect(UiColors.ring, topLeft = Offset(1f, 1f), size = size.copy(width = size.width - 2, height = size.height - 2), style = Stroke(1.dp.toPx()))
        val path = Path()
        points.sortedBy { it.ts }.map { pos(it.lat, it.lon) }.distinct().forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, UiColors.good, style = Stroke(2.dp.toPx()))
        home?.let { h -> val o = pos(h.lat, h.lon); drawRect(androidx.compose.ui.graphics.Color(0xFF58708A), Offset(o.x - 5.dp.toPx(), o.y - 5.dp.toPx()), androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx())); drawText(tm, "HOME", Offset(o.x + 8.dp.toPx(), o.y - 5.dp.toPx()), label) }
        est.take(12).forEach { e ->
            val o = pos(e.lat, e.lon)
            val col = if (e.klass == "camera" || e.klass == "drone") UiColors.alert else if (e.kind == "tracker") UiColors.watch else UiColors.neutral
            drawCircle(col.copy(alpha = 0.25f), (e.radiusM.toFloat() * sc).coerceIn(4.dp.toPx(), 60.dp.toPx()), o)
            drawCircle(col, 5.dp.toPx(), o)
            drawText(tm, e.label.take(12), Offset(o.x + 7.dp.toPx(), o.y - 6.dp.toPx()), label)
        }
        drawText(tm, "N ↑   ${Math.round(half)} m from centre to edge", Offset(8.dp.toPx(), 4.dp.toPx()), label)
    }
}
