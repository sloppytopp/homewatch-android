package io.github.sloppytopp.homewatch.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sloppytopp.homewatch.data.Store
import io.github.sloppytopp.homewatch.detect.Engine
import io.github.sloppytopp.homewatch.detect.Snapshot

/** Process-wide hub: the service feeds the engine, the UI observes [snapshot]. */
object Monitor {
    var onAlert: (String, String) -> Unit = { _, _ -> }
    val engine = Engine(onAlert = { d, t -> onAlert(d, t) }, eventSink = { runCatching { Store.addEvent(it) } }, trailSink = { runCatching { Store.addSight(it) } },
        isMine = { runCatching { io.github.sloppytopp.homewatch.data.Prefs.isMine(it) }.getOrDefault(false) })
    var snapshot by mutableStateOf(Snapshot())
        private set

    fun refresh() { snapshot = engine.snapshot() }

    private var inspector: BleScanner? = null
    var inspecting by mutableStateOf(false); private set

    /** Unfiltered Bluetooth scan (shows every device). Only while the Nearby screen asks for it; Android pauses it with the screen off. */
    fun setInspect(ctx: android.content.Context, on: Boolean) {
        if (on == inspecting) return
        if (on) { inspector = BleScanner(ctx.applicationContext, inspect = true); if (inspector!!.start()) inspecting = true else inspector = null }
        else { inspector?.stop(); inspector = null; inspecting = false; engine.clearInspect() }
    }

    var lastSweep by mutableStateOf<String?>(null)

    /** Ask for Wi-Fi scans as often as Android allows (room sweeps, hunts). */
    var fastWifi by mutableStateOf(false)

    /** Survey walk: log every sighting together with the phone's GPS position. */
    var surveying by mutableStateOf(false)
    var surveyCount by mutableStateOf(0)
    var surveyNote by mutableStateOf<String?>(null)
    var lastLoc by mutableStateOf<android.location.Location?>(null)

    /** True while the PIN screen is showing. */
    var locked by mutableStateOf(false)

    /** Which alert the user is currently hunting ("tracker" | "drone" | "camera"), or null. */
    var hunt by mutableStateOf<String?>(null)

}
