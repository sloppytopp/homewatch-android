package io.github.sloppytopp.homewatch.detect

enum class Level { OFF, OK, WATCH, ALERT }

/** [mineKey] is what "This is mine" would save for the thing named in [message] (a Bluetooth address, or "wifi:" + the first five octets of a camera-like network). */
data class DomainState(val level: Level, val message: String, val mineKey: String? = null)
data class TrackerRow(val addr: String, val label: String, val rssi: Int, val seenS: Long, val agoS: Long, val mine: Boolean = false)
data class DroneRow(val addr: String, val info: RemoteIdInfo, val rssi: Int, val agoS: Long, val via: String)
data class EventRow(val ts: Long, val domain: String, val level: Level, val msg: String)

/** A drone's claimed position (and operator position, live only) for the map. */
/** Any Bluetooth device heard while 'show all devices' is on. */
data class InspectRow(val addr: String, val name: String, val rssi: Int, val company: String, val ageS: Long)

data class DroneFix(val id: String, val lat: Double?, val lon: Double?, val opLat: Double?, val opLon: Double?, val alt: Double?, val rssi: Int)

data class Snapshot(
    val running: Boolean = false,
    val drone: DomainState = DomainState(Level.OFF, "Not scanning"),
    val tracker: DomainState = DomainState(Level.OFF, "Not scanning"),
    val camera: DomainState = DomainState(Level.OFF, "Not scanning"),
    val spam: DomainState = DomainState(Level.OFF, "Not scanning"),
    val rf: DomainState = DomainState(Level.OFF, "Needs optional RTL-SDR hardware"),
    val trackers: List<TrackerRow> = emptyList(),
    val drones: List<DroneRow> = emptyList(),
    val wifi: List<WifiRow> = emptyList(),
    val fixes: List<DroneFix> = emptyList(),
    val adsSeen: Long = 0,
    val ambientIgnored: Long = 0,
    val events: List<EventRow> = emptyList(),
    val error: String? = null,
    val startedAt: Long = 0,
    val wifiAt: Long = 0,
    val inspect: List<InspectRow> = emptyList(),
    val now: Long = 0,
    val demo: Boolean = false,
    val follow: List<FollowHit> = emptyList(),
)

/**
 * Turns raw sightings (Bluetooth + Wi-Fi) into honest status. Thread-safe (callbacks arrive on binder threads).
 * Operator coordinates are shown live only - never written to the event log.
 */
class Engine(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onAlert: (domain: String, genericText: String) -> Unit = { _, _ -> },
    private val eventSink: (EventRow) -> Unit = {},
    private val isMine: (String) -> Boolean = { false },
    private val trailSink: (Sight) -> Unit = {},
    private val netSink: (List<String>) -> Unit = {},
    private val locProvider: () -> Pair<Double, Double>? = { null },
) {
    /** The phone's own GPS fixes (only available while the phone has a fix, e.g. during a survey walk). Used to veto "followed you" when the phone barely moved. */
    private val phonePath = ArrayList<Triple<Long, Double, Double>>()
    private var lastPathAt = 0L

    /** Farthest the phone got from itself between [from] and [to], or null when there are too few GPS fixes to say. */
    internal fun movedM(from: Long, to: Long): Double? {
        val pts = phonePath.filter { it.first in from..to }
        // fail open: only vouch for "the phone stayed put" when GPS fixes cover most of the span; otherwise say nothing and let the alert stand
        if (pts.size < 3 || pts.last().first - pts.first().first < (to - from) * 0.8) return null
        // linear time: farthest point from the first, then farthest from that one (a close lower bound on the true spread)
        val a = pts.maxBy { Geo.distanceM(pts[0].second, pts[0].third, it.second, it.third) }
        return pts.maxOf { Geo.distanceM(a.second, a.third, it.second, it.third) }
    }

    private class Sighting(val kind: Kind, val label: String, val first: Long) {
        var last = first
        var n = 0
        var rssi = -127
        var rssiMax = -127
        var info: RemoteIdInfo? = null
    }

    private class WifiHit(val row: WifiRow, val info: RemoteIdInfo?, val why: String)

    private val spam = SpamDetector()
    private var lastSpamLevel = Level.OK
    private val sightings = HashMap<String, Sighting>()
    private val events = ArrayDeque<EventRow>()
    private val lastEmit = HashMap<String, Long>()
    private val knownNets = HashSet<String>()   // Wi-Fi BSSIDs seen before; a new one is logged once
    private var netsLoaded = false
    private val trail = ArrayList<Sight>()
    private val lastTrail = HashMap<String, Long>()
    private var placeFp: Set<String> = emptySet()
    private var followAt = 0L
    private var followHits: List<FollowHit> = emptyList()
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

    private class Insp(var name: String, var rssi: Int, val company: String, var last: Long)
    private val inspect = HashMap<String, Insp>()
    @Volatile var startedAt = 0L
    private var demo: DroneFix? = null
    private var demoUntil = 0L

    @Volatile var running = false
    /** Sample-data mode: synthetic sightings, never logged to history and never alerting. */
    @Volatile var demoMode = false
    @Volatile var wifiEnabled = false
    @Volatile var wifiNote: String? = null
    @Volatile var error: String? = null

    @Synchronized
    fun onAdvertisement(addr: String, rssi: Int, c: Classification) {
        adsSeen++
        when (c.kind) {
            Kind.NONE -> return
            Kind.AMBIENT -> { ambient++; return }
            Kind.SPAM -> { if (!demoMode) spam.onAd(addr, c.label, rssi, clock()); return }
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
    fun onInspect(addr: String, name: String?, rssi: Int, companyId: Int?) {
        val now = clock()
        val co = companyId?.let { COMPANIES[it] ?: "company 0x%04X".format(it) } ?: ""
        val x = inspect.getOrPut(addr) { Insp(name ?: "", rssi, co, now) }
        x.rssi = rssi; x.last = now
        if (!name.isNullOrEmpty()) x.name = name
        if (inspect.size > 400) inspect.entries.removeIf { now - it.value.last > 60_000 }
    }

    @Synchronized fun clearInspect() { inspect.clear() }

    /** Load the BSSIDs seen on earlier runs. An empty set means "learn the neighbourhood silently on the first scan". */
    @Synchronized fun loadKnownNets(saved: Set<String>) { knownNets.clear(); knownNets += saved; netsLoaded = true }

    /** Reload saved tracker sightings (so following detection survives an app restart). */
    @Synchronized fun loadTrail(saved: List<Sight>) { trail.clear(); trail += saved; followAt = 0 }

    /** "Delete all history" must also forget the in-memory trail, or following alerts would keep firing from deleted data. */
    @Synchronized fun clearTrail() { trail.clear(); lastTrail.clear(); followHits = emptyList(); followAt = 0 }

    @Synchronized
    fun onWifiScan(obs: List<WifiObs>) {
        wifiAt = clock()
        placeFp = Follow.fingerprint(obs)
        val rows = ArrayList<WifiRow>()
        val drones = ArrayList<WifiHit>()
        val cams = ArrayList<WifiHit>()
        for (o in obs) {
            val klass = WifiClassifier.klassOf(o)
            val vendor = WifiClassifier.lookup(o.bssid)?.vendor ?: ""
            val row = WifiRow(o.ssid.ifEmpty { "(hidden)" }, o.bssid, o.level, vendor, klass, o.caps, o.freq)
            rows += row
            var ridFound = false
            for (d in o.ridData) {
                val info = RemoteId.fromWifiVendorData(d)
                if (info != RemoteIdInfo()) { drones += WifiHit(row, info, "Remote ID beacon (Wi-Fi)"); ridFound = true }
            }
            if (!ridFound && klass == "drone") drones += WifiHit(row, null, "drone-like network ($vendor)")
            if (klass == "camera") cams += WifiHit(row, null, "camera-like network")
        }
        if (!demoMode && netsLoaded) {
            val firstEver = knownNets.isEmpty()
            val added = rows.filter { knownNets.add(it.bssid) }
            if (added.isNotEmpty()) {
                if (!firstEver) added.filter { it.level >= -80 }.sortedByDescending { it.level }.take(3).forEach {
                    emit("network", Level.OK, "newnet:${it.bssid}", "New Wi-Fi network appeared: '${it.ssid}' ${it.bssid}" +
                        (if (it.vendor.isNotEmpty()) " ${it.vendor}" else "") + " ${it.level} dBm", 0, wifiAt)
                }
                if (knownNets.size > MAX_KNOWN_NETS) knownNets.clear()   // runaway guard (e.g. a long drive): relearn
                netSink(added.map { it.bssid })
            }
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
        spam.clear(); lastSpamLevel = Level.OK
        sightings.clear(); events.clear(); lastEmit.clear(); adsSeen = 0; ambient = 0; inspect.clear()
        startedAt = clock()
        wifiAt = 0; wifiRows = emptyList(); wifiDrones = emptyList(); wifiCams = emptyList()
        lastDroneLevel = Level.OK; lastTrackerLevel = Level.OK; lastCameraLevel = Level.OK
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val now = clock()
        sightings.entries.removeIf { now - it.value.last > FORGET_MS }
        val live = sightings.filter { now - it.value.last <= WINDOW_MS }
        val bleDrones = live.filter { it.value.kind == Kind.DRONE }
        val allTrackers = live.filter { it.value.kind == Kind.TRACKER }
        val trackers = allTrackers.filter { !isMine(it.key) }   // your own trackers never raise a flag
        val mineCount = allTrackers.size - trackers.size
        val wifiFresh = wifiAt != 0L && now - wifiAt <= WIFI_FRESH_MS

        // ---- following: log each unknown tracker about once a minute with where we are (Wi-Fi fingerprint), then look for the same one at several places
        if (!demoMode && wifiFresh && placeFp.isNotEmpty()) trackers.forEach { (a, s) ->
            // only a tracker heard just now says anything about WHERE it is: the 5-minute "current" window would otherwise log it at places it never was
            if (now - s.last <= FRESH_HEARD_MS && now - (lastTrail[a] ?: 0L) >= TRAIL_EVERY_MS) {
                lastTrail[a] = now
                Sight(now, a, s.label, placeFp).also { trail += it; trailSink(it) }
                while (trail.size > MAX_TRAIL) trail.removeAt(0)
            }
        }
        if (!demoMode && now - lastPathAt >= TRAIL_EVERY_MS) locProvider()?.let { (la, lo) ->
            lastPathAt = now; phonePath += Triple(now, la, lo); while (phonePath.size > 2000) phonePath.removeAt(0)
        }
        if (now - followAt >= 30_000L) { followAt = now; followHits = Follow.analyze(trail) }
        // A tracker that sat still while the phone's Wi-Fi view shifted is not following: if GPS shows the phone barely moved, veto it
        val following = followHits.filter { h ->
            trackers.containsKey(h.key) && (movedM(h.visits.minOf { it.first }, h.visits.maxOf { it.last })?.let { it >= MIN_MOVE_M } ?: true)
        }

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
        if (droneState.level == Level.ALERT && lastDroneLevel != Level.ALERT) alert("drone", "A drone signal was detected near the house.")
        lastDroneLevel = droneState.level

        // ---- trackers: ALERT only when persistent AND close; otherwise WATCH
        val trackerState = if (following.isNotEmpty()) {
            val h = following.first()
            val msg = "${h.label} has FOLLOWED you: heard ${h.key} at ${h.visits.size} different places over ${h.spanMs / 60_000} min"
            emit("tracker", Level.ALERT, "follow:${h.key}", msg, 1_800_000, now)
            DomainState(Level.ALERT, msg, h.key)
        } else if (trackers.isNotEmpty()) {
            val (addr, s) = trackers.entries.maxByOrNull { it.value.rssi }!!
            val dur = (s.last - s.first) / 1000
            val close = dur >= 300 && s.n >= 5 && s.rssiMax >= -70
            val msg = "${s.label} nearby: $addr rssi=${s.rssi} dBm, seen ${dur}s"
            val lvl = if (close) Level.ALERT else Level.WATCH
            emit("tracker", lvl, "tracker:$addr", msg, 900_000, now)
            DomainState(lvl, msg, addr)
        } else DomainState(Level.OK, "No unknown trackers in range ($adsSeen Bluetooth ads heard, $ambient normal Apple devices ignored" +
            (if (mineCount > 0) ", $mineCount of your own trackers" else "") + ")")
        if (trackerState.level == Level.ALERT && lastTrackerLevel != Level.ALERT) alert("tracker", if (following.isNotEmpty()) "A tracker has followed you across several places." else "A tracker has stayed close to you.")
        lastTrackerLevel = trackerState.level

        // ---- camera-like Wi-Fi sources (spy cams often broadcast their own network)
        val cameraState = when {
            !wifiEnabled -> DomainState(Level.OFF, wifiNote ?: "Wi-Fi scanning is off")
            !wifiFresh -> DomainState(Level.OFF, wifiNote ?: "Waiting for the first Wi-Fi scan...")
            wifiCams.none { !isMine(camKey(it.row.bssid, it.row.ssid)) } && wifiCams.isNotEmpty() ->
                DomainState(Level.OK, "No camera-like Wi-Fi sources besides ${wifiCams.map { camKey(it.row.bssid, it.row.ssid) }.distinct().size} you marked as yours")
            wifiCams.isNotEmpty() -> {
                val h = wifiCams.filter { !isMine(camKey(it.row.bssid, it.row.ssid)) }.maxByOrNull { it.row.level }!!
                val near = h.row.level > -60
                val lvl = if (near) Level.ALERT else Level.WATCH
                val msg = "Camera-like Wi-Fi source '${h.row.ssid}' ${h.row.bssid}${if (h.row.vendor.isNotEmpty()) " (${h.row.vendor})" else ""} " +
                    "${h.row.level} dBm${if (near) " - VERY CLOSE" else ""}"
                // One radio often broadcasts several virtual networks (…:79, …:89): log them as one source, and repeat a quiet WATCH only every 6 h
                emit("camera", lvl, camKey(h.row.bssid, h.row.ssid), msg, if (near) 1_800_000 else 6 * 3_600_000L, now)
                DomainState(lvl, msg, camKey(h.row.bssid, h.row.ssid))
            }
            else -> DomainState(Level.OK, "No camera-like Wi-Fi sources (${wifiRows.size} networks in range)")
        }
        if (cameraState.level == Level.ALERT && lastCameraLevel != Level.ALERT) alert("camera", "Something camera-like showed up very close on Wi-Fi.")
        lastCameraLevel = cameraState.level

        // ---- Bluetooth pairing pop-up flood (Flipper Zero / phone-app spam)
        val sp = spam.evaluate(now)
        if (sp.level != Level.OK) emit("bluetooth", sp.level, "spam:${sp.family}", sp.message, 900_000, now)
        if (sp.level == Level.ALERT && lastSpamLevel != Level.ALERT) alert("bluetooth", "A flood of fake Bluetooth pairing pop-ups is being broadcast nearby.")
        lastSpamLevel = sp.level
        val spamState = DomainState(sp.level, sp.message)

        if (demo != null && now < demoUntil) fixes += demo!!
        val off = Snapshot()
        return Snapshot(
            running = running,
            drone = if (running) droneState else off.drone,
            tracker = if (running) trackerState else off.tracker,
            camera = if (running) cameraState else off.camera,
            spam = if (running) spamState else off.spam,
            trackers = allTrackers.map { (a, s) -> TrackerRow(a, s.label, s.rssi, (s.last - s.first) / 1000, (now - s.last) / 1000, isMine(a)) }
                .sortedByDescending { it.rssi },
            drones = droneRows,
            wifi = if (wifiFresh) wifiRows else emptyList(),
            fixes = fixes,
            adsSeen = adsSeen, ambientIgnored = ambient, events = events.toList(), error = error,
            startedAt = startedAt, wifiAt = wifiAt, now = now, demo = demoMode, follow = followHits.filter { h -> !isMine(h.key) && now - h.visits.maxOf { it.last } <= FOLLOW_CARD_MS },
            inspect = inspect.entries.filter { now - it.value.last <= 60_000 }
                .map { (a, x) -> InspectRow(a, x.name, x.rssi, x.company, (now - x.last) / 1000) }.sortedByDescending { it.rssi },
        )
    }

    /** Virtual networks from one radio share their first five octets: treat them as one source. */
    private fun camKey(bssid: String, ssid: String) = "wifi:" + bssid.lowercase().split(":").take(5).joinToString(":") + "|" + ssid

    private fun alert(domain: String, text: String) { if (!demoMode) onAlert(domain, text) }

    private fun pos(i: RemoteIdInfo?) = if (i?.lat != null && i.lon != null) " at %.5f,%.5f".format(i.lat, i.lon) else ""
    private fun op(i: RemoteIdInfo?) = if (i?.operatorLat != null && i.operatorLon != null) ", operator at %.5f,%.5f".format(i.operatorLat, i.operatorLon) else ""

    private fun emit(domain: String, level: Level, key: String, msg: String, cooldownMs: Long, now: Long) {
        if (now - (lastEmit[key] ?: 0L) < cooldownMs) return
        lastEmit[key] = now
        val e = EventRow(now, domain, level, msg)
        events.addFirst(e)
        while (events.size > MAX_EVENTS) events.removeLast()
        if (!demoMode) eventSink(e)
    }

    companion object {
        const val WINDOW_MS = 300_000L      // a sighting stays "current" for 5 min
        const val FORGET_MS = 3_600_000L    // forgotten entirely after 1 h
        const val WIFI_FRESH_MS = 180_000L  // a Wi-Fi scan counts for 3 min
        const val MAX_EVENTS = 200
        const val MAX_KNOWN_NETS = 5000
        const val TRAIL_EVERY_MS = 60_000L
        const val MAX_TRAIL = 6000
        const val FRESH_HEARD_MS = 15_000L   // a tracker counts as "here right now" only if heard this recently (survey rows and following trail)
        const val FOLLOW_CARD_MS = 6 * 3_600_000L   // an old follow timeline is history (see Evidence), not a live warning
        const val MIN_MOVE_M = 150.0        // "followed you" needs the phone to have travelled at least this far, when GPS can tell
        val COMPANIES = mapOf(
            0x004C to "Apple", 0x0075 to "Samsung", 0x0006 to "Microsoft", 0x00E0 to "Google", 0x0087 to "Garmin",
            0x0171 to "Amazon", 0x02E5 to "Espressif (IoT)", 0x0059 to "Nordic (IoT)", 0x0157 to "Huami/Amazfit",
            0x038F to "Xiaomi", 0x0499 to "Ruuvi", 0x0310 to "Tile?", 0x00D2 to "Dialog Semiconductor", 0x0131 to "Cypress",
        )
    }
}
