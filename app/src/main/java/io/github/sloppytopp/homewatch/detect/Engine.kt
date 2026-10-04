package io.github.sloppytopp.homewatch.detect

enum class Level { OFF, OK, WATCH, ALERT }

data class DomainState(val level: Level, val message: String)
data class TrackerRow(val addr: String, val label: String, val rssi: Int, val seenS: Long, val agoS: Long)
data class DroneRow(val addr: String, val info: RemoteIdInfo, val rssi: Int, val agoS: Long)
data class EventRow(val ts: Long, val domain: String, val level: Level, val msg: String)

data class Snapshot(
    val running: Boolean = false,
    val drone: DomainState = DomainState(Level.OFF, "Not scanning"),
    val tracker: DomainState = DomainState(Level.OFF, "Not scanning"),
    val camera: DomainState = DomainState(Level.OFF, "Wi-Fi scanning comes in the next update"),
    val rf: DomainState = DomainState(Level.OFF, "Needs optional RTL-SDR hardware"),
    val trackers: List<TrackerRow> = emptyList(),
    val drones: List<DroneRow> = emptyList(),
    val adsSeen: Long = 0,
    val ambientIgnored: Long = 0,
    val events: List<EventRow> = emptyList(),
    val error: String? = null,
)

/**
 * Turns raw Bluetooth sightings into honest status. Thread-safe (BLE callbacks arrive on binder threads).
 * Operator coordinates are shown live only - they are never written into the event log.
 */
class Engine(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onAlert: (domain: String, genericText: String) -> Unit = { _, _ -> },
) {
    private class Sighting(val kind: Kind, val label: String, val first: Long) {
        var last = first
        var n = 0
        var rssi = -127
        var rssiMax = -127
        var info: RemoteIdInfo? = null
    }

    private val sightings = HashMap<String, Sighting>()
    private val events = ArrayDeque<EventRow>()
    private val lastEmit = HashMap<String, Long>()
    private var adsSeen = 0L
    private var ambient = 0L
    private var lastDroneLevel = Level.OK
    private var lastTrackerLevel = Level.OK
    @Volatile var running = false
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
    fun reset() {
        sightings.clear(); events.clear(); lastEmit.clear(); adsSeen = 0; ambient = 0
        lastDroneLevel = Level.OK; lastTrackerLevel = Level.OK
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val now = clock()
        sightings.entries.removeIf { now - it.value.last > FORGET_MS }
        val live = sightings.filter { now - it.value.last <= WINDOW_MS }
        val drones = live.filter { it.value.kind == Kind.DRONE }
        val trackers = live.filter { it.value.kind == Kind.TRACKER }

        // ---- drone: Remote ID is unauthenticated, so word it as a claim
        val droneState = if (drones.isNotEmpty()) {
            val (_, s) = drones.entries.maxByOrNull { it.value.rssi }!!
            val i = s.info
            val pos = if (i?.lat != null && i.lon != null) " at %.5f,%.5f".format(i.lat, i.lon) else ""
            val op = if (i?.operatorLat != null && i.operatorLon != null) ", operator at %.5f,%.5f".format(i.operatorLat, i.operatorLon) else ""
            val base = "Broadcast CLAIMS a drone (Bluetooth, unverified) id=${i?.basicId ?: "?"} rssi=${s.rssi}$pos"
            emit("drone", Level.ALERT, "drone:${i?.basicId ?: "x"}", base, 120_000, now) // no operator coords in the log
            DomainState(Level.ALERT, base + op)
        } else DomainState(Level.OK, "No Remote ID drone heard (Bluetooth)")
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

        return Snapshot(
            running = running,
            drone = if (running) droneState else Snapshot().drone,
            tracker = if (running) trackerState else Snapshot().tracker,
            trackers = trackers.map { (a, s) -> TrackerRow(a, s.label, s.rssi, (s.last - s.first) / 1000, (now - s.last) / 1000) }
                .sortedByDescending { it.rssi },
            drones = drones.map { (a, s) -> DroneRow(a, s.info ?: RemoteIdInfo(), s.rssi, (now - s.last) / 1000) },
            adsSeen = adsSeen, ambientIgnored = ambient, events = events.toList(), error = error,
        )
    }

    private fun emit(domain: String, level: Level, key: String, msg: String, cooldownMs: Long, now: Long) {
        if (now - (lastEmit[key] ?: 0L) < cooldownMs) return
        lastEmit[key] = now
        events.addFirst(EventRow(now, domain, level, msg))
        while (events.size > MAX_EVENTS) events.removeLast()
    }

    companion object {
        const val WINDOW_MS = 300_000L      // a sighting stays "current" for 5 min
        const val FORGET_MS = 3_600_000L    // forgotten entirely after 1 h
        const val MAX_EVENTS = 200
    }
}
