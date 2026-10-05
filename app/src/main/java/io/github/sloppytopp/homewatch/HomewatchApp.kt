package io.github.sloppytopp.homewatch

import android.app.Application
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.data.Store
import io.github.sloppytopp.homewatch.detect.WifiClassifier

class HomewatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        runCatching { io.github.sloppytopp.homewatch.scan.Monitor.engine.loadTrail(Store.trail()) }
        runCatching { assets.open("oui_watch.csv").bufferedReader().useLines { WifiClassifier.loadCsv(it) } }
    }
}
