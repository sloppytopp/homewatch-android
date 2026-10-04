package io.github.sloppytopp.homewatch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.scan.Monitor
import io.github.sloppytopp.homewatch.scan.ScanService
import io.github.sloppytopp.homewatch.ui.DroneMapView
import io.github.sloppytopp.homewatch.ui.HistoryScreen
import io.github.sloppytopp.homewatch.ui.HomewatchTheme
import io.github.sloppytopp.homewatch.ui.Level
import io.github.sloppytopp.homewatch.ui.RadarView
import io.github.sloppytopp.homewatch.ui.SettingsScreen
import io.github.sloppytopp.homewatch.ui.StatusTile
import io.github.sloppytopp.homewatch.ui.UiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun lv(l: io.github.sloppytopp.homewatch.detect.Level) = Level.valueOf(l.name)

/** BLE needs one permission; Wi-Fi scanning and notifications are optional extras. */
private fun requiredPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun optionalPermissions(): List<String> {
    val l = ArrayList<String>()
    if (Build.VERSION.SDK_INT >= 33) { l += Manifest.permission.NEARBY_WIFI_DEVICES; l += Manifest.permission.POST_NOTIFICATIONS }
    else if (Build.VERSION.SDK_INT >= 31) l += Manifest.permission.ACCESS_FINE_LOCATION
    return l
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark system bars, regardless of the phone's light/dark setting (no bright strip at night).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent { HomewatchTheme { Root() } }
    }

    override fun onResume() { super.onResume(); Monitor.refresh() }
}

@Composable
private fun Root() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Status", "Radar", "History", "Settings").forEachIndexed { i, name ->
                TextButton(onClick = { tab = i }) {
                    Text(name, color = if (tab == i) UiColors.text else UiColors.faint, fontSize = if (tab == i) 15.sp else 14.sp)
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> StatusScreen()
                1 -> { RadarView(Monitor.snapshot); Text("Drone map", color = UiColors.text, fontSize = 16.sp); DroneMapView(Monitor.snapshot) }
                2 -> HistoryScreen()
                else -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun StatusScreen() {
    val ctx = LocalContext.current
    val s = Monitor.snapshot
    var explain by remember { mutableStateOf(false) }
    var locOff by remember { mutableStateOf(false) }

    fun startScan() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locOff = !LocationManagerCompat.isLocationEnabled(lm) && Build.VERSION.SDK_INT <= 30
        if (locOff) return
        ContextCompat.startForegroundService(ctx, Intent(ctx, ScanService::class.java))
    }

    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (requiredPermissions().all { res[it] == true || ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) startScan()
    }

    fun onStartTapped() {
        val all = (requiredPermissions() + optionalPermissions()).distinct()
        val missing = all.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startScan() else explain = true
    }

    Text("Homewatch", color = UiColors.text, fontSize = 22.sp)
    Button(
        onClick = { if (s.running) ctx.startService(Intent(ctx, ScanService::class.java).setAction(ScanService.ACTION_STOP)) else onStartTapped() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
    ) { Text(if (s.running) "Stop scanning" else "Start scanning") }

    s.error?.let { Text("⚠ $it", color = UiColors.warn, fontSize = 13.sp) }
    if (locOff) {
        Text("Android 11 and older only deliver Bluetooth scan results while the phone's Location switch is on. " +
            "Homewatch never saves or sends your location - the switch is just how Android gates scanning.", color = UiColors.warn, fontSize = 13.sp)
        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Open Location settings") }
    }

    StatusTile("Drone near the house?", lv(s.drone.level), s.drone.message)
    StatusTile("Active tracker present?", lv(s.tracker.level), s.tracker.message)
    StatusTile("Camera-like Wi-Fi source?", lv(s.camera.level), s.camera.message)
    StatusTile("Unusual RF activity?", lv(s.rf.level), s.rf.message)

    if (s.running) {
        Text("Right now: ${s.adsSeen} Bluetooth ads heard, ${s.wifi.size} Wi-Fi networks", color = UiColors.dim, fontSize = 13.sp)
        s.drones.forEach { Text("DRONE claim (${it.via})  ${it.info.basicId ?: it.addr}  ${it.rssi} dBm  ${it.agoS}s ago", color = UiColors.text, fontSize = 13.sp) }
        s.trackers.take(6).forEach { Text("${it.label}  ${it.addr}  ${it.rssi} dBm  seen ${it.seenS}s", color = UiColors.text, fontSize = 13.sp) }
    }
    if (s.events.isNotEmpty()) {
        Text("Recent events", color = UiColors.dim, fontSize = 13.sp)
        val f = SimpleDateFormat("HH:mm:ss", Locale.US)
        s.events.take(5).forEach { Text("${f.format(Date(it.ts))}  ${it.level.name.lowercase()}  ${it.msg}", color = UiColors.text, fontSize = 12.sp) }
    }
    Text("Detect-only. A quiet screen is not a guarantee of safety. Nothing leaves this phone.", color = UiColors.faint, fontSize = 12.sp)

    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text("Before we scan") },
        text = {
            Text(
                "Homewatch listens for Bluetooth and Wi-Fi signals from nearby drones, trackers and camera-like networks, entirely on this phone.\n\n" +
                    (if (Build.VERSION.SDK_INT >= 31) "Android will ask for \"Nearby devices\" - that is Bluetooth scanning" +
                        (if (Build.VERSION.SDK_INT >= 33) " and Wi-Fi scanning.\n\n" else ", and for Location to allow Wi-Fi scanning.\n\n")
                    else "Android will ask for \"Location\" - on this Android version that is the only way to allow Bluetooth and Wi-Fi scanning. Homewatch does not read, save or send your location.\n\n") +
                    "It may also ask to show notifications, so it can chime softly when something is flagged. Nothing is uploaded and there is no account."
            )
        },
        confirmButton = { TextButton(onClick = { explain = false; askPerms.launch((requiredPermissions() + optionalPermissions()).distinct().toTypedArray()) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } },
    )
}
