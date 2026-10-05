package io.github.sloppytopp.homewatch

import android.Manifest
import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import io.github.sloppytopp.homewatch.scan.Demo
import io.github.sloppytopp.homewatch.scan.Monitor
import io.github.sloppytopp.homewatch.scan.ScanService
import io.github.sloppytopp.homewatch.ui.DroneMapView
import io.github.sloppytopp.homewatch.ui.HelpCard
import io.github.sloppytopp.homewatch.ui.FollowCard
import io.github.sloppytopp.homewatch.ui.Heartbeat
import io.github.sloppytopp.homewatch.ui.LockScreen
import io.github.sloppytopp.homewatch.ui.HuntHost
import io.github.sloppytopp.homewatch.ui.LiveBanner
import io.github.sloppytopp.homewatch.ui.Meter
import io.github.sloppytopp.homewatch.ui.NearbyScreen
import io.github.sloppytopp.homewatch.ui.rememberNow
import kotlinx.coroutines.delay
import io.github.sloppytopp.homewatch.ui.HistoryScreen
import io.github.sloppytopp.homewatch.ui.HomewatchTheme
import io.github.sloppytopp.homewatch.ui.Level
import io.github.sloppytopp.homewatch.ui.RadarView
import io.github.sloppytopp.homewatch.ui.RoomsScreen
import io.github.sloppytopp.homewatch.ui.SettingsScreen
import io.github.sloppytopp.homewatch.ui.SurveyCard
import io.github.sloppytopp.homewatch.ui.StatusTile
import io.github.sloppytopp.homewatch.ui.UiColors
import io.github.sloppytopp.homewatch.detect.Snapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun sweepSummary(s: Snapshot): String {
    val f = SimpleDateFormat("h:mm a", Locale.US)
    val flagged = listOf(s.drone, s.tracker, s.camera).count { it.level == io.github.sloppytopp.homewatch.detect.Level.WATCH || it.level == io.github.sloppytopp.homewatch.detect.Level.ALERT }
    val cams = s.wifi.count { it.klass == "camera" || it.klass == "drone" }
    return "Sweep finished at ${f.format(Date())}: ${s.wifi.size} Wi-Fi networks ($cams suspicious), ${s.trackers.size} tracker signals, ${s.drones.size} drone claims. " +
        if (flagged == 0) "Nothing unusual." else "$flagged item(s) flagged - see the tiles."
}

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
        handleIntent(intent)
        setContent { HomewatchTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) { i?.getStringExtra("hunt")?.let { Monitor.hunt = it } }

    private var lastStop = 0L
    private var firstStart = true

    override fun onStart() {
        super.onStart()
        if (Prefs.hasPin && (firstStart || SystemClock.elapsedRealtime() - lastStop > 30_000)) Monitor.locked = true
        firstStart = false
    }

    override fun onStop() { super.onStop(); lastStop = SystemClock.elapsedRealtime() }

    override fun onResume() { super.onResume(); Monitor.refresh() }
}

@Composable
private fun Root() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ctx0 = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Prefs.discreet) {
        val w = (ctx0 as? Activity)?.window
        if (Prefs.discreet) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (Monitor.locked) { LockScreen(); return }
    Monitor.hunt?.let { d -> HuntHost(d) { Monitor.hunt = null } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Heartbeat(Monitor.snapshot.running)
            Text("  N0RMA", color = UiColors.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { (ctx0 as? Activity)?.finishAndRemoveTask() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("✕", color = UiColors.dim, fontSize = 18.sp)
            }
            Text("Night", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
            Switch(checked = Prefs.night, onCheckedChange = { Prefs.saveNight(it) })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Status", "Nearby", "Rooms", "Radar", "History", "Settings").forEachIndexed { i, name ->
                TextButton(onClick = { tab = i }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                    Text(name, color = if (tab == i) UiColors.text else UiColors.faint, fontSize = if (tab == i) 14.sp else 12.sp)
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> StatusScreen()
                1 -> NearbyScreen()
                2 -> RoomsScreen()
                3 -> { RadarView(Monitor.snapshot); Text("Drone map", color = UiColors.text, fontSize = 16.sp); DroneMapView(Monitor.snapshot); SurveyCard() }
                4 -> HistoryScreen()
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

    val now = rememberNow()
    var sweepStart by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sweepStart) {
        if (sweepStart > 0) { delay(30_000); Monitor.lastSweep = sweepSummary(Monitor.snapshot); sweepStart = 0 }
    }

    if (!Prefs.welcomed) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(UiColors.buttonBg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Welcome - here's how this works", color = UiColors.text, fontSize = 16.sp)
            Text("1. Tap Start scanning. N0RMA listens for drones, trackers and camera-like Wi-Fi networks around you.\n" +
                "2. The banner tells you in plain words if everything is normal. It shows proof it's working: a breathing dot and live counts.\n" +
                "3. Use Nearby to see every Wi-Fi network and Bluetooth device, and tap one to hunt for it.\n" +
                "4. Everything stays on this phone. Use the Night switch at the top to dim the screen.", color = UiColors.dim, fontSize = 13.sp)
            TextButton(onClick = { Prefs.saveWelcomed() }) { Text("Got it") }
        }
    }

    Button(
        onClick = { if (Demo.active) Demo.stop() else if (s.running) ctx.startService(Intent(ctx, ScanService::class.java).setAction(ScanService.ACTION_STOP)) else onStartTapped() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
    ) { Text(if (Demo.active) "Stop sample data" else if (s.running) "Stop scanning" else "Start scanning") }
    if (!s.running && !Demo.active) TextButton(onClick = { Demo.start() }) { Text("Try it with sample data (nothing real is scanned)") }

    LiveBanner(s)

    if (sweepStart > 0) {
        val frac = ((now - sweepStart) / 30_000f).coerceIn(0f, 1f)
        Meter(frac, "Sweeping... ${(30 - (now - sweepStart) / 1000).coerceAtLeast(0)} s left - hold still, let it listen")
    } else {
        Button(
            onClick = { if (!s.running) onStartTapped(); sweepStart = System.currentTimeMillis() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF111111), contentColor = UiColors.buttonFg),
        ) { Text("Sweep now (30 seconds)") }
    }
    Monitor.lastSweep?.let { Text(it, color = UiColors.text, fontSize = 13.sp) }

    s.error?.let { Text("⚠ $it", color = UiColors.warn, fontSize = 13.sp) }
    if (locOff) {
        Text("Android 11 and older only deliver Bluetooth scan results while the phone's Location switch is on. " +
            "N0RMA never saves or sends your location - the switch is just how Android gates scanning.", color = UiColors.warn, fontSize = 13.sp)
        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Open Location settings") }
    }

    fun flagged(l: io.github.sloppytopp.homewatch.detect.Level) = l == io.github.sloppytopp.homewatch.detect.Level.WATCH || l == io.github.sloppytopp.homewatch.detect.Level.ALERT
    StatusTile("Drone near the house?", lv(s.drone.level), s.drone.message, if (flagged(s.drone.level)) ({ Monitor.hunt = "drone" }) else null)
    StatusTile("Active tracker present?", lv(s.tracker.level), s.tracker.message, if (flagged(s.tracker.level)) ({ Monitor.hunt = "tracker" }) else null)
    StatusTile("Hidden camera / unknown device?", lv(s.camera.level), s.camera.message, if (flagged(s.camera.level)) ({ Monitor.hunt = "camera" }) else null)
    StatusTile("Elevated RF / EMF? (RTL-SDR)", lv(s.rf.level), s.rf.message)

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
    FollowCard(s.follow)
    HelpCard()
    Text("Detect-only. A quiet screen is not a guarantee of safety. Nothing leaves this phone.", color = UiColors.faint, fontSize = 12.sp)

    if (explain) AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = { explain = false },
        title = { Text("Before we scan") },
        text = {
            Text(
                "N0RMA listens for Bluetooth and Wi-Fi signals from nearby drones, trackers and camera-like networks, entirely on this phone.\n\n" +
                    (if (Build.VERSION.SDK_INT >= 31) "Android will ask for \"Nearby devices\" - that is Bluetooth scanning" +
                        (if (Build.VERSION.SDK_INT >= 33) " and Wi-Fi scanning.\n\n" else ", and for Location to allow Wi-Fi scanning.\n\n")
                    else "Android will ask for \"Location\" - on this Android version that is the only way to allow Bluetooth and Wi-Fi scanning. N0RMA does not read, save or send your location.\n\n") +
                    "It may also ask to show notifications, so it can chime softly when something is flagged. Nothing is uploaded and there is no account."
            )
        },
        confirmButton = { TextButton(onClick = { explain = false; askPerms.launch((requiredPermissions() + optionalPermissions()).distinct().toTypedArray()) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } },
    )
}
