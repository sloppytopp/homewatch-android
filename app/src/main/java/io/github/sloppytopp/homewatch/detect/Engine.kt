package io.github.sloppytopp.homewatch.detect

enum class Level { OFF, OK, WATCH, ALERT }

data class DomainState(val level: Level, val message: String)
data class TrackerRow(val addr: String, val label: String, val rssi: Int, val seenS: Long, val agoS: Long)
data class DroneRow(val addr: String, val info: RemoteIdInfo, val rssi: Int, val agoS: Long, val via: String)
data class EventRow(val ts: Long, val domain: String, val level: Level, val msg: String)

/** A drone's claimed position (and operator position, live only) for the map. */
data class DroneFix(val id: String, val lat: Double?, val lon: Double?, val opLat: Double?, val opLon: Double?, val alt: Double?, val rssi: Int)

data class Snapshot(
    val running: Boolean = false,
    val drone: DomainState = DomainState(Level.OFF, "Not scanning"),
    val tracker: DomainState = DomainState(Level.OFF, "Not scanning"),
    val camera: DomainState = DomainState(Level.OFF, "Not scanning"),
    val rf: DomainState = DomainState(Level.OFF, "Needs optional RTL-SDR hardware"),
    val trackers: List<TrackerRow> = emptyList(),
    val drones: List<DroneRow> = emptyList(),
    val wifi: List<WifiRow> = emptyList(),
    val fixes: List<DroneFix> = emptyList(),
    val adsSeen: Long = 0,
    val ambientIgnored: Long = 0,
    val events: List<EventRow> = emptyList(),
    val error: String? = null,
)

/**
 * Turns raw sightings (Bluetooth + Wi-Fi) into honest status. Thread-safe (callbacks arrive on binder threads).
 * Operator coordinates are shown live only - never written to the event log.
 */
class Engine(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onAlert: (domain: String, genericText: String) -> Unit = { _, _ -> },
    private val eventSink: (EventRow) -> Unit = {},
) {
    private class Sighting(val kind: Kind, val label: String, val first: Long) {
        var last = first
        var n = 0
        var rssi = -127
        var rssiMax = -127
        var info: RemoteIdInfo? = null
    }

    private class WifiHit(val row: WifiRow, val info: RemoteIdInfo?, val why: String)

    private val sightings = HashMap<String, Sighting>()
    private val events = ArrayDeque<EventRow>()
    private val lastEmit = HashMap<String, Long>()
    private var adsSeen = 0L
    private var ambient = 0L
    private var lastDroneLevel = Level.OK
    private var lastTrackerLevel = Level.OK
    private var lastCameraLevel = Level.OK

    // Wi-Fi: results of the most recent scan
    private var wifiAt = 0L
    private var wifiRows: List<WifiRow> = emptyList()
    private var wifiDrones: List<WifiHit> = emptyList()
    private var wifiCams: List<WifiHit> = emptyList()

    private var demo: DroneFix? = null
    private var demoUntil = 0L

    @Volatile var running = false
    @Volatile var wifiEnabled = false
    @Volatile var wifiNote: String? = null
    @Volatile var error: String? = null

    @Synchronized
    fun onAdvertisement(addr: String, rssi: Int, c: Classification) {
        adsSeen++
        when (c.kind) {
            Kind.NONE -> return
            Kind.AMBIENT -> { ambient++; return }
            else -> {}
        }
        val now = clock()
        val key = if (c.kind == Kind.DRONE && !c.remoteId?.basicId.isNullOrEmpty()) "rid:${c.remoteId!!.basicId}" else addr
        val s = sightings.getOrPut(key) { Sighting(c.kind, c.label, now) }
        s.last = now; s.rssi = rssi; s.n++
        if (rssi > s.rssiMax) s.rssiMax = rssi
        c.remoteId?.let { s.info = it }
    }

    @Synchronized
    fun onWifiScan(obs: List<WifiObs>) {
        wifiAt = clock()
        val rows = ArrayList<WifiRow>()
        val drones = ArrayList<WifiHit>()
        val cams = ArrayList<WifiHit>()
        for (o in obs) {
            val klass = WifiClassifier.klassOf(o)
            val vendor = WifiClassifier.lookup(o.bssid)?.vendor ?: ""
            val row = WifiRow(o.ssid.ifEmpty { "(hidden)" }, o.bssid, o.level, vendor, klass)
            rows += row
            var ridFound = false
            for (d in o.ridData) {
                val info = RemoteId.fromWifiVendorData(d)
                if (info != RemoteIdInfo()) { drones += WifiHit(row, info, "Remote ID beacon (Wi-Fi)"); ridFound = true }
            }
            if (!ridFound && klass == "drone") drones += WifiHit(row, null, "drone-like network ($vendor)")
            if (klass == "camera") cams += WifiHit(row, null, "camera-like network")
        }
        wifiRows = rows.sortedByDescending { it.level }
        wifiDrones = drones
        wifiCams = cams
    }

    /** A fake drone for 60 s so the map can be previewed. Not logged, no alert, clearly labelled. */
    @Synchronized
    fun startDemo(homeLat: Double, homeLon: Double) {
        demo = DroneFix("DEMO (not a real drone)", homeLat + 0.0018, homeLon + 0.0017, homeLat - 0.0003, homeLon - 0.0002, 60.0, -70)
        demoUntil = clock() + 60_000
    }

    @Synchronized
    fun reset() {
        sightings.clear(); events.clear(); lastEmit.clear(); adsSeen = 0; ambient = 0
        wifiAt = 0; wifiRows = emptyList(); wifiDrones = emptyList(); wifiCams = emptyList()
        lastDroneLevel = Level.OK; lastTrackerLevel = Level.OK; lastCameraLevel = Level.OK
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val now = clock()
        sightings.entries.removeIf { now - it.value.last > FORGET_MS }
        val live = sightings.filter { now - it.value.last <= WINDOW_MS }
        val bleDrones = live.filter { it.value.kind == Kind.DRONE }
        val trackers = live.filter { it.value.kind == Kind.TRACKER }
        val wifiFresh = wifiAt != 0L && now - wifiAt <= WIFI_FRESH_MS

        // ---- drone: Remote ID is unauthenticated, so word it as a claim
        var droneState = DomainState(Level.OK, "No Remote ID drone heard (Bluetooth${if (wifiEnabled) " + Wi-Fi" else ""})")
        val fixes = ArrayList<DroneFix>()
        val droneRows = ArrayList<DroneRow>()
        if (bleDrones.isNotEmpty()) {
            val (_, s) = bleDrones.entries.maxByOrNull { it.value.rssi }!!
            val i = s.info
            val base = "Broadcast CLAIMS a drone (Bluetooth, unverified) id=${i?.basicId ?: "?"} rssi=${s.rssi}${pos(i)}"
            emit("drone", Level.ALERT, "drone:${i?.basicId ?: "x"}", base, 120_000, now) // no operator coords in the log
            droneState = DomainState(Level.ALERT, base + op(i))
            bleDrones.forEach { (a, x) ->
                droneRows += DroneRow(a, x.info ?: RemoteIdInfo(), x.rssi, (now - x.last) / 1000, "Bluetooth")
                x.info?.let { fixes += DroneFix(it.basicId ?: a, it.lat, it.lon, it.operatorLat, it.operatorLon, it.altM, x.rssi) }
            }
        }
        if (wifiFresh && wifiDrones.isNotEmpty()) {
            val h = wifiDrones.maxByOrNull { it.row.level }!!
            val base = "Broadcast CLAIMS a drone: ${h.why} (unverified) '${h.row.ssid}' ${h.row.bssid} ${h.row.level} dBm" +
                (h.info?.basicId?.let { " id=$it" } ?: "") + pos(h.info)
            emit("drone", Level.ALERT, "wdrone:${h.row.bssid}", base, 120_000, now)
            if (droneState.level != Level.ALERT) droneState = DomainState(Level.ALERT, base + op(h.info))
            wifiDrones.forEach { x ->
                droneRows += DroneRow(x.row.bssid, x.info ?: RemoteIdInfo(), x.row.level, (now - wifiAt) / 1000, "Wi-Fi")
                x.info?.let { fixes += DroneFix(it.basicId ?: x.row.bssid, it.lat, it.lon, it.operatorLat, it.operatorLon, it.altM, x.row.level) }
            }
        }
        if (droneState.level == Level.ALERT && lastDroneLevel != Level.ALERT) onAlert("drone", "A drone signal was detected near the house.")
        lastDroneLevel = droneState.level

        // ---- trackers: ALERT only when persistent AND close; otherwise WATCH
        val trackerState = if (trackers.isNotEmpty()) {
            val (addr, s) = trackers.entries.maxByOrNull { it.value.rssi }!!
            val dur = (s.last - s.first) / 1000
            val close = dur >= 300 && s.n >= 5 && s.rssiMax >= -70
            val msg = "${s.label} nearby: $addr rssi=${s.rssi} dBm, seen ${dur}s"
            val lvl = if (close) Level.ALERT else Level.WATCH
            emit("tracker", lvl, "tracker:$addr", msg, 900_000, now)
            DomainState(lvl, msg)
        } else DomainState(Level.OK, "No separated trackers in range ($adsSeen Bluetooth ads heard, $ambient normal Apple devices ignored)")
        if (trackerState.level == Level.ALERT && lastTrackerLevel != Level.ALERT) onAlert("tracker", "A tracker has stayed close to the house.")
        lastTrackerLevel = trackerState.level

        // ---- camera-like Wi-Fi sources (spy cams often broadcast their own network)
        val cameraState = when {
            !wifiEnabled -> DomainState(Level.OFF, wifiNote ?: "Wi-Fi scanning is off")
            !wifiFresh -> DomainState(Level.OFF, wifiNote ?: "Waiting for the first Wi-Fi scan...")
            wifiCams.isNotEmpty() -> {
                val h = wifiCams.maxByOrNull { it.row.level }!!
                val near = h.row.level > -60
                val lvl = if (near) Level.ALERT else Level.WATCH
                val msg = "Camera-like Wi-Fi source '${h.row.ssid}' ${h.row.bssid}${if (h.row.vendor.isNotEmpty()) " (${h.row.vendor})" else ""} " +
                    "${h.row.level} dBm${if (near) " - VERY CLOSE" else ""}"
                emit("camera", lvl, "cam:${h.row.bssid}", msg, 1_800_000, now)
                DomainState(lvl, msg)
            }
            else -> DomainState(Level.OK, "No camera-like Wi-Fi sources (${wifiRows.size} networks in range)")
        }
        if (cameraState.level == Level.ALERT && lastCameraLevel != Level.ALERT) onAlert("camera", "Something camera-like showed up very close on Wi-Fi.")
        lastCameraLevel = cameraState.level

        if (demo != null && now < demoUntil) fixes += demo!!
        val off = Snapshot()
        return Snapshot(
            running = running,
            drone = if (running) droneState else off.drone,
            tracker = if (running) trackerState else off.tracker,
            camera = if (running) cameraState else off.camera,
            trackers = trackers.map { (a, s) -> TrackerRow(a, s.label, s.rssi, (s.last - s.first) / 1000, (now - s.last) / 1000) }
                .sortedByDescending { it.rssi },
            drones = droneRows,
            wifi = if (wifiFresh) wifiRows else emptyList(),
            fixes = fixes,
            adsSeen = adsSeen, ambientIgnored = ambient, events = events.toList(), error = error,
        )
    }

    private fun pos(i: RemoteIdInfo?) = if (i?.lat != null && i.lon != null) " at %.5f,%.5f".format(i.lat, i.lon) else ""
    private fun op(i: RemoteIdInfo?) = if (i?.operatorLat != null && i.operatorLon != null) ", operator at %.5f,%.5f".format(i.operatorLat, i.operatorLon) else ""

    private fun emit(domain: String, level: Level, key: String, msg: String, cooldownMs: Long, now: Long) {
        if (now - (lastEmit[key] ?: 0L) < cooldownMs) return
        lastEmit[key] = now
        val e = EventRow(now, domain, level, msg)
        events.addFirst(e)
        while (events.size > MAX_EVENTS) events.removeLast()
        eventSink(e)
    }

    companion object {
        const val WINDOW_MS = 300_000L      // a sighting stays "current" for 5 min
        const val FORGET_MS = 3_600_000L    // forgotten entirely after 1 h
        const val WIFI_FRESH_MS = 180_000L  // a Wi-Fi scan counts for 3 min
        const val MAX_EVENTS = 200
    }
}
