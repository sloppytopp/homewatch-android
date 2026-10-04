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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.sloppytopp.homewatch.scan.Monitor
import io.github.sloppytopp.homewatch.scan.ScanService
import io.github.sloppytopp.homewatch.ui.HomewatchTheme
import io.github.sloppytopp.homewatch.ui.Level
import io.github.sloppytopp.homewatch.ui.StatusTile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Dim = Color(0xFF8A8A8A)
private val Soft = Color(0xFFC8C8C8)

private fun lv(l: io.github.sloppytopp.homewatch.detect.Level) = Level.valueOf(l.name)

private fun neededPermissions(): Array<String> {
    val l = ArrayList<String>()
    if (Build.VERSION.SDK_INT >= 31) l += Manifest.permission.BLUETOOTH_SCAN else l += Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.POST_NOTIFICATIONS
    return l.toTypedArray()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark system bars, regardless of the phone's light/dark setting (no bright strip at night).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent { HomewatchTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        Monitor.refresh()
    }
}

@Composable
private fun Screen() {
    val ctx = LocalContext.current
    val s = Monitor.snapshot
    var explain by remember { mutableStateOf(false) }
    var locOff by remember { mutableStateOf(false) }

    fun startScan() {
        if (Build.VERSION.SDK_INT <= 30) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!LocationManagerCompat.isLocationEnabled(lm)) { locOff = true; return }
        }
        locOff = false
        ContextCompat.startForegroundService(ctx, Intent(ctx, ScanService::class.java))
    }

    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val bleOk = res.entries.filter { it.key != Manifest.permission.POST_NOTIFICATIONS }.all { it.value }
        if (bleOk) startScan()
    }

    fun onStartTapped() {
        val missing = neededPermissions().filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startScan() else explain = true
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Homewatch", color = Soft, fontSize = 22.sp)

        Button(
            onClick = {
                if (s.running) ctx.startService(Intent(ctx, ScanService::class.java).setAction(ScanService.ACTION_STOP))
                else onStartTapped()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2A22), contentColor = Color(0xFF9CC7AB)),
        ) { Text(if (s.running) "Stop scanning" else "Start scanning") }

        s.error?.let { Text("⚠ $it", color = Color(0xFFB39A55), fontSize = 13.sp) }
        if (locOff) {
            Text(
                "Android 11 and older only deliver Bluetooth scan results while the phone's Location switch is on. " +
                    "Homewatch never saves or sends your location - the switch is just how Android gates Bluetooth scanning.",
                color = Color(0xFFB39A55), fontSize = 13.sp,
            )
            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Open Location settings") }
        }

        StatusTile("Drone near the house?", lv(s.drone.level), s.drone.message)
        StatusTile("Active tracker present?", lv(s.tracker.level), s.tracker.message)
        StatusTile("Camera-like Wi-Fi source?", lv(s.camera.level), s.camera.message)
        StatusTile("Unusual RF activity?", lv(s.rf.level), s.rf.message)

        if (s.running) {
            Text("Bluetooth in range right now", color = Dim, fontSize = 13.sp)
            if (s.trackers.isEmpty() && s.drones.isEmpty()) Text("none", color = Dim, fontSize = 13.sp)
            s.drones.forEach { Text("DRONE claim  id=${it.info.basicId ?: "?"}  ${it.rssi} dBm  ${it.agoS}s ago", color = Soft, fontSize = 13.sp) }
            s.trackers.take(6).forEach { Text("${it.label}  ${it.addr}  ${it.rssi} dBm  seen ${it.seenS}s", color = Soft, fontSize = 13.sp) }
        }

        if (s.events.isNotEmpty()) {
            Text("Recent events", color = Dim, fontSize = 13.sp)
            val f = SimpleDateFormat("HH:mm:ss", Locale.US)
            s.events.take(8).forEach { Text("${f.format(Date(it.ts))}  ${it.level.name.lowercase()}  ${it.msg}", color = Soft, fontSize = 12.sp) }
        }

        Text(
            "Some phones (including TCL) stop background apps to save battery. If scanning stops on its own, allow Homewatch to run unrestricted:",
            color = Dim, fontSize = 12.sp,
        )
        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery settings") }
        Text("Detect-only. A quiet screen is not a guarantee of safety. Nothing leaves this phone.", color = Color(0xFF6A6A6A), fontSize = 12.sp)
    }

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text("Before we scan") },
            text = {
                Text(
                    "Homewatch listens for Bluetooth signals from nearby drones and trackers, entirely on this phone.\n\n" +
                        (if (Build.VERSION.SDK_INT >= 31) "Android will ask for \"Nearby devices\" - that is Bluetooth scanning.\n\n"
                        else "Android will ask for \"Location\" - on this Android version that is the only way to allow Bluetooth scanning. Homewatch does not read, save or send your location.\n\n") +
                        "It also asks to show notifications, so it can chime softly when something is flagged. Nothing is uploaded and there is no account."
                )
            },
            confirmButton = { TextButton(onClick = { explain = false; askPerms.launch(neededPermissions()) }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } },
        )
    }
}
