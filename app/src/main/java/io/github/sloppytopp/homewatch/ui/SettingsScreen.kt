package io.github.sloppytopp.homewatch.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.sloppytopp.homewatch.data.AlertStyle
import io.github.sloppytopp.homewatch.data.Home
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.detect.Geo
import io.github.sloppytopp.homewatch.scan.Monitor
import java.util.Locale

private data class Found(val line: String, val lat: Double, val lon: Double)

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<Triple<Double, Double, Float>?>(null) } // lat, lon, accuracy
    var addr by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Found>>(emptyList()) }
    var latT by remember { mutableStateOf("") }
    var lonT by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun useGps() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> { msg = "Turn on Location in your phone's quick settings, then try again."; return }
        }
        msg = "Getting your position (stand near a window for GPS)..."
        try {
            LocationManagerCompat.getCurrentLocation(lm, provider, CancellationSignal(), ContextCompat.getMainExecutor(ctx)) { loc ->
                if (loc == null) msg = "Couldn't get a position fix. Try again near a window."
                else {
                    gps = Triple(loc.latitude, loc.longitude, loc.accuracy)
                    Prefs.saveHome(Home(loc.latitude, loc.longitude, "from this phone's GPS (about ${Math.round(loc.accuracy)} m)"))
                    msg = "Home set from your phone's GPS (accurate to about ${Math.round(loc.accuracy)} m). Nothing was sent anywhere."
                }
            }
        } catch (e: SecurityException) { msg = "Location permission is needed for this." }
    }

    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) useGps() else msg = "Without Location permission, use the address or manual options instead." }

    fun searchAddress() {
        if (addr.isBlank()) return
        if (!Geocoder.isPresent()) { msg = "This phone has no address lookup service. Use GPS or enter the numbers manually."; return }
        busy = true; msg = null
        val q = addr.trim()
        Thread {
            val res = try {
                @Suppress("DEPRECATION")
                Geocoder(ctx, Locale.US).getFromLocationName(q, 3)?.map { Found(it.getAddressLine(0) ?: q, it.latitude, it.longitude) } ?: emptyList()
            } catch (e: Exception) { null }
            Handler(Looper.getMainLooper()).post {
                busy = false
                if (res == null) msg = "Address lookup failed (it needs an internet connection)."
                else { found = res; if (res.isEmpty()) msg = "No match. Try adding the city and state, or use GPS." }
            }
        }.start()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Display and alerts", color = UiColors.text, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Night mode (dim red on black)", color = UiColors.text, fontSize = 14.sp)
            Switch(checked = Prefs.night, onCheckedChange = { Prefs.saveNight(it) })
        }
        Text("When something is flagged:", color = UiColors.dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlertStyle.values().forEach { st ->
                val on = Prefs.alertStyle == st
                Button(
                    onClick = { Prefs.saveAlertStyle(st) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (on) UiColors.buttonBg else androidx.compose.ui.graphics.Color(0xFF111111),
                        contentColor = if (on) UiColors.buttonFg else UiColors.dim),
                ) { Text(when (st) { AlertStyle.CHIME -> "Soft chime"; AlertStyle.VIBRATE -> "Vibrate"; AlertStyle.SILENT -> "Silent" }, fontSize = 12.sp) }
            }
        }
        Text("Never a voice. Vibrate is best when you are walking toward a device.", color = UiColors.faint, fontSize = 11.sp)

        Text("Home location", color = UiColors.text, fontSize = 16.sp)
        Text(Prefs.home?.let { "Set: %.5f, %.5f  (%s)".format(it.lat, it.lon, it.note.ifEmpty { "entered by you" }) } ?: "Not set yet. It lets the drone map show where a drone is relative to your house.",
            color = UiColors.dim, fontSize = 12.sp)
        Text("1. Most accurate: stand at home and use your phone's GPS. Nothing is sent anywhere.", color = UiColors.dim, fontSize = 12.sp)
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) useGps()
                else askLoc.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
        ) { Text("Use my current location") }

        Text("2. Or search by address. Android's location service looks the address up (it leaves this phone for that lookup only). " +
            "Address lookups can be off by a lot - check the result against GPS.", color = UiColors.dim, fontSize = 12.sp)
        OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Street address, city, state") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { searchAddress() }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg)) {
            Text(if (busy) "Searching..." else "Search address")
        }
        found.forEach { r ->
            val off = gps?.let { Geo.distanceM(r.lat, r.lon, it.first, it.second) }
            Text("${r.line}\n%.5f, %.5f".format(r.lat, r.lon) + (off?.let { "\nDistance from your phone's GPS: ${Math.round(it)} m" +
                if (it > 200) " - that is a big gap; this lookup is probably imprecise, GPS is better." else "" } ?: "\n(Use GPS first to compare.)"),
                color = UiColors.text, fontSize = 12.sp)
            TextButton(onClick = { Prefs.saveHome(Home(r.lat, r.lon, "from address search")); found = emptyList(); msg = "Home set from the address search." }) { Text("Use this one") }
        }

        Text("3. Or type the numbers yourself (west longitude is negative, e.g. 40.7128 and -74.0060).", color = UiColors.dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = latT, onValueChange = { latT = it }, label = { Text("Latitude") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(value = lonT, onValueChange = { lonT = it }, label = { Text("Longitude") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        TextButton(onClick = {
            val la = latT.trim().toDoubleOrNull(); val lo = lonT.trim().toDoubleOrNull()
            if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0 || (la == 0.0 && lo == 0.0)) msg = "Those numbers don't look right. Use plain numbers; west longitude is negative."
            else { Prefs.saveHome(Home(la, lo, "entered by you")); msg = "Home saved." }
        }) { Text("Save these numbers") }
        if (Prefs.home != null) TextButton(onClick = { Prefs.saveHome(null); msg = "Home location cleared." }) { Text("Clear home location", color = UiColors.warn) }
        msg?.let { Text(it, color = UiColors.warn, fontSize = 12.sp) }
        if (Prefs.home != null) {
            TextButton(onClick = {
                Prefs.home?.let { Monitor.engine.startDemo(it.lat, it.lon); Monitor.refresh() }
                msg = "A demo drone (clearly labelled, not real) will show on the Radar tab's drone map for 60 seconds."
            }) { Text("Preview the drone map with a demo drone") }
        }

        Text("Your Wi-Fi networks", color = UiColors.text, fontSize = 16.sp)
        Text("Learned from the network this phone is connected to; they show green on the radar.", color = UiColors.dim, fontSize = 12.sp)
        if (Prefs.mySsids.isEmpty()) Text("none yet (start scanning while on your home Wi-Fi)", color = UiColors.dim, fontSize = 12.sp)
        Prefs.mySsids.forEach { s -> Row { Text(s, color = UiColors.text, fontSize = 13.sp, modifier = Modifier.weight(1f)); TextButton(onClick = { Prefs.forgetSsid(s) }) { Text("Forget") } } }

        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery settings (allow unrestricted background use)") }
        Text("Homewatch v0.2 - detect-only. Nothing leaves this phone except the optional address lookup above.", color = UiColors.faint, fontSize = 11.sp)
    }
}
