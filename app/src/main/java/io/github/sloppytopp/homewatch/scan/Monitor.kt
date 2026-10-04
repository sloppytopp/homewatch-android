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
    val engine = Engine(onAlert = { d, t -> onAlert(d, t) }, eventSink = { runCatching { Store.addEvent(it) } })
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

    /** Which alert the user is currently hunting ("tracker" | "drone" | "camera"), or null. */
    var hunt by mutableStateOf<String?>(null)

}
