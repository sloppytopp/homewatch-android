package io.github.sloppytopp.homewatch.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import io.github.sloppytopp.homewatch.detect.BleClassifier

/**
 * Filtered BLE scan. Android 8.1+ pauses UNFILTERED scans when the screen is off, so we ask the radio only for the
 * advertisements we care about (Remote ID, Apple Find My, Tile, SmartTag, Chipolo) - which also saves battery.
 */
@SuppressLint("MissingPermission") // permissions are checked by the caller before start()
class BleScanner(private val ctx: Context, private val inspect: Boolean = false) {
    private var scanner: BluetoothLeScanner? = null

    private fun uuid(short: String) = ParcelUuid.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    private fun filters(): List<ScanFilter> {
        val f = ArrayList<ScanFilter>()
        f += ScanFilter.Builder().setServiceData(uuid(BleClassifier.REMOTE_ID_UUID), ByteArray(0), ByteArray(0)).build()
        f += ScanFilter.Builder().setServiceData(uuid("fd5a"), ByteArray(0), ByteArray(0)).build()
        for (u in BleClassifier.TRACKER_UUIDS.keys) f += ScanFilter.Builder().setServiceUuid(uuid(u)).build()
        // Apple "Find My" (type 0x12): both separated and owner-nearby; the engine tells them apart.
        f += ScanFilter.Builder().setManufacturerData(0x004C, byteArrayOf(0x12), byteArrayOf(0xFF.toByte())).build()
        // Pairing pop-up advertisements, for the pop-up flood detector: Apple Proximity Pairing (0x07) and Nearby Action (0x0F), Google Fast Pair, Windows Swift Pair
        for (t in byteArrayOf(0x07, 0x0F)) f += ScanFilter.Builder().setManufacturerData(0x004C, byteArrayOf(t), byteArrayOf(0xFF.toByte())).build()
        f += ScanFilter.Builder().setServiceData(uuid(BleClassifier.FAST_PAIR_UUID), ByteArray(0), ByteArray(0)).build()
        f += ScanFilter.Builder().setManufacturerData(0x0006, byteArrayOf(0x03, 0x00), byteArrayOf(0xFF.toByte(), 0xFF.toByte())).build()
        return f
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) = handle(r)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(it) }
        override fun onScanFailed(errorCode: Int) {
            Monitor.engine.error = "Bluetooth scan failed (code $errorCode)"
        }
    }

    private fun handle(r: ScanResult) {
        try {
            val rec = r.scanRecord ?: return
            if (inspect) {
                val arr = rec.manufacturerSpecificData
                Monitor.engine.onInspect(r.device.address, rec.deviceName, r.rssi, if (arr.size() > 0) arr.keyAt(0) else null)
                return
            }
            val mfr = HashMap<Int, ByteArray>()
            val arr = rec.manufacturerSpecificData
            for (i in 0 until arr.size()) mfr[arr.keyAt(i)] = arr.valueAt(i)
            val sd = rec.serviceData.entries.associate { it.key.toString() to it.value }
            val uu = rec.serviceUuids?.map { it.toString() } ?: emptyList()
            val c = BleClassifier.classify(mfr, sd, uu, rec.deviceName)
            Monitor.engine.onAdvertisement(r.device.address, r.rssi, c)
        } catch (e: Exception) {
            // hostile or garbled advertisement: never let it kill the scanner
        }
    }

    fun start(): Boolean {
        val adapter: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            Monitor.engine.error = "Bluetooth is off - turn it on to scan"
            return false
        }
        val s = adapter.bluetoothLeScanner ?: run { Monitor.engine.error = "Bluetooth scanner unavailable"; return false }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false) // also receive Bluetooth 5 extended advertisements (used by some Remote ID drones)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .build()
        s.startScan(if (inspect) emptyList() else filters(), settings, callback)
        scanner = s
        Monitor.engine.error = null
        return true
    }

    fun stop() {
        try { scanner?.stopScan(callback) } catch (_: Exception) {}
        scanner = null
    }
}
