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
}
