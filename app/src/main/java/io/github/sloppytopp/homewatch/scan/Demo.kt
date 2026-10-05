package io.github.sloppytopp.homewatch.scan

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sloppytopp.homewatch.data.Home
import io.github.sloppytopp.homewatch.detect.Classification
import io.github.sloppytopp.homewatch.detect.Kind
import io.github.sloppytopp.homewatch.detect.RemoteIdInfo
import io.github.sloppytopp.homewatch.detect.WifiObs

/** "Try it with sample data": clearly labelled fake sightings so a new user can see every screen work. Nothing real is scanned or saved. */
object Demo {
    var active by mutableStateOf(false); private set
    val FAKE_HOME = Home(40.0, -100.0, "sample")
    private val h = Handler(Looper.getMainLooper())
    private var n = 0
    private val tick = object : Runnable {
        override fun run() { feed(); Monitor.refresh(); h.postDelayed(this, 2000) }
    }

    fun start() {
        if (active) return
        val e = Monitor.engine
        e.reset(); e.demoMode = true; e.running = true; e.wifiEnabled = true
        active = true; n = 0
        h.post(tick)
    }

    fun stop() {
        h.removeCallbacks(tick)
        val e = Monitor.engine
        e.demoMode = false; e.running = false; e.wifiEnabled = false; e.reset()
        active = false; Monitor.refresh()
    }

    private fun feed() {
        val e = Monitor.engine
        if (n++ % 3 == 0) e.onWifiScan(listOf(
            WifiObs("aa:bb:cc:00:00:01", "SampleHome", -49),
            WifiObs("aa:bb:cc:00:00:02", "Neighbor-WiFi", -72),
            WifiObs("aa:bb:cc:00:00:03", "CoffeeShop", -81),
            WifiObs("02:11:22:33:44:55", "HDWifiCam_8F2A", -66),   // camera-like name
        ))
        e.onAdvertisement("00:00:00:00:00:01", -63, Classification(Kind.TRACKER, "Tile tracker"))
        e.onInspect("11:22:33:00:00:01", "Fitness band", -62, 0x0157)
        e.onInspect("11:22:33:00:00:02", "TV remote", -70, null)
        e.onInspect("11:22:33:00:00:03", null, -78, 0x004C)
        // A sample drone ~230 m NE of a FICTIONAL home (never the user's real saved home, so screenshots can't reveal where anyone lives).
        val h = FAKE_HOME
        e.onAdvertisement("DE:AD:00:00:00:01", -70, Classification(Kind.DRONE, "Remote ID broadcast",
            RemoteIdInfo(basicId = "SAMPLE-DRONE", lat = h.lat + 0.0018, lon = h.lon + 0.0017, altM = 60.0, operatorLat = h.lat - 0.0003, operatorLon = h.lon - 0.0002)))
    }
}
