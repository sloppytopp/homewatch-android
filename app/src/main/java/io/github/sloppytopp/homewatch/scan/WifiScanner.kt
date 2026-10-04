package io.github.sloppytopp.homewatch.scan

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.detect.WifiObs

/**
 * Periodic Wi-Fi scan. Android throttles scans (about 4 per 2 minutes for a foreground service), so we ask every 30 s
 * and use whatever the system returns. Needs Location permission AND the Location switch on (an Android rule).
 */
@SuppressLint("MissingPermission", "DEPRECATION")
class WifiScanner(private val ctx: Context) {
    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = read()
    }

    fun start() {
        if (!registered) { ctx.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); registered = true }
        Monitor.engine.wifiEnabled = true
        poke()
    }

    fun stop() {
        if (registered) { runCatching { ctx.unregisterReceiver(receiver) }; registered = false }
        Monitor.engine.wifiEnabled = false
    }

    /** Ask for a fresh scan (may be throttled) and read whatever is available. */
    fun poke() {
        if (!wm.isWifiEnabled) { Monitor.engine.wifiNote = "Wi-Fi is off - turn it on to scan"; return }
        try { wm.startScan() } catch (_: SecurityException) {}
        read()
    }

    private fun read() {
        try {
            val results = wm.scanResults
            if (results.isEmpty()) { Monitor.engine.wifiNote = "No Wi-Fi results yet (needs Location on, or the system is throttling scans)"; return }
            Monitor.engine.wifiNote = null
            Monitor.engine.onWifiScan(results.map { toObs(it) })
            wm.connectionInfo?.ssid?.trim('"')?.let { Prefs.learnSsid(it) }
        } catch (e: SecurityException) {
            Monitor.engine.wifiNote = "Wi-Fi scanning needs Location permission"
        } catch (e: Exception) {
            Monitor.engine.wifiNote = "Wi-Fi scan problem: ${e.javaClass.simpleName}"
        }
    }

    private fun toObs(r: ScanResult): WifiObs {
        val rid = ArrayList<ByteArray>()
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                for (ie in r.informationElements) {
                    if (ie.id != 221) continue
                    val b = ie.bytes.duplicate()
                    val arr = ByteArray(b.remaining()); b.get(arr)
                    // vendor IE = [OUI(3)][type...]; Remote ID uses OUI FA:0B:BC
                    if (arr.size >= 5 && arr[0] == 0xFA.toByte() && arr[1] == 0x0B.toByte() && arr[2] == 0xBC.toByte()) rid += arr.copyOfRange(3, arr.size)
                }
            } catch (_: Exception) {}
        }
        return WifiObs(r.BSSID ?: "", r.SSID ?: "", r.level, rid)
    }
}
