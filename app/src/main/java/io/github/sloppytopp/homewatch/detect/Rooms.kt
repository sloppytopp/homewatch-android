package io.github.sloppytopp.homewatch.detect

data class RoomItem(val key: String, val kind: String, val label: String, val rssi: Double)
data class RoomScan(val room: String, val ts: Long, val items: List<RoomItem>)

/** Averages what is heard during a room sweep (about one sample per second). */
class SweepCollector {
    private val sum = HashMap<String, Double>()
    private val n = HashMap<String, Int>()
    private val meta = HashMap<String, Pair<String, String>>() // key -> (kind, label)

    private fun add(key: String, kind: String, label: String, rssi: Int) {
        sum[key] = (sum[key] ?: 0.0) + rssi; n[key] = (n[key] ?: 0) + 1
        if (key !in meta) meta[key] = kind to label
    }

    fun sample(s: Snapshot) {
        s.wifi.forEach { add("wifi:${it.bssid}", "wifi", it.ssid, it.level) }
        s.trackers.forEach { add("ble:${it.addr}", "tracker", it.label, it.rssi) }
        s.drones.forEach { add("ble:${it.addr}", "drone", "Drone broadcast", it.rssi) }
        s.inspect.forEach { add("ble:${it.addr}", "ble", it.name.ifEmpty { it.company.ifEmpty { "Bluetooth device" } }, it.rssi) }
    }

    val samples get() = n.values.maxOrNull() ?: 0

    fun result(room: String, ts: Long) = RoomScan(room, ts, sum.keys.map { k ->
        RoomItem(k, meta[k]!!.first, meta[k]!!.second, sum[k]!! / n[k]!!)
    }.sortedByDescending { it.rssi })
}

class RoomDiff(val new: List<RoomItem>, val gone: List<RoomItem>, val louder: List<Pair<RoomItem, Double>>)

object RoomBook {
    /** Phones and watches rotate their Bluetooth address, so unnamed Bluetooth devices are noise in comparisons. */
    private fun meaningful(i: RoomItem) = i.kind != "ble" || (i.label != "Bluetooth device")

    fun diff(prev: RoomScan?, now: RoomScan): RoomDiff {
        if (prev == null) return RoomDiff(emptyList(), emptyList(), emptyList())
        val p = prev.items.associateBy { it.key }
        val c = now.items.associateBy { it.key }
        return RoomDiff(
            new = now.items.filter { it.key !in p && meaningful(it) },
            gone = prev.items.filter { it.key !in c && meaningful(it) },
            louder = now.items.mapNotNull { i -> p[i.key]?.let { o -> if (i.rssi - o.rssi >= 8 && meaningful(i)) i to (i.rssi - o.rssi) else null } },
        )
    }

    class Likely(val room: String, val rssi: Double, val marginDb: Double?)

    /** Which room is a device loudest in? Margin = how much louder than the next-best room (null if heard in only one). */
    fun likelyRoom(key: String, latest: Map<String, RoomScan>): Likely? {
        val per = latest.values.mapNotNull { s -> s.items.firstOrNull { it.key == key }?.let { s.room to it.rssi } }.sortedByDescending { it.second }
        if (per.isEmpty()) return null
        return Likely(per[0].first, per[0].second, per.getOrNull(1)?.let { per[0].second - it.second })
    }

    /** Devices that clearly belong to one room (loudest there by at least [minMargin] dB, or heard only there). */
    fun roomSpecific(latest: Map<String, RoomScan>, minMargin: Double = 8.0): List<Pair<RoomItem, Likely>> {
        if (latest.size < 2) return emptyList()
        val items = latest.values.flatMap { it.items }.filter { meaningful(it) }.associateBy { it.key }
        return items.values.mapNotNull { i ->
            likelyRoom(i.key, latest)?.let { l -> if (l.marginDb == null || l.marginDb >= minMargin) i to l else null }
        }.sortedByDescending { it.first.rssi }
    }
}
