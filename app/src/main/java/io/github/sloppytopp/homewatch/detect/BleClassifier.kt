package io.github.sloppytopp.homewatch.detect

enum class Kind { DRONE, TRACKER, AMBIENT, NONE }

data class Classification(val kind: Kind, val label: String = "", val remoteId: RemoteIdInfo? = null)

object BleClassifier {
    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"
    const val REMOTE_ID_UUID = "fffa"
    private const val APPLE = 0x004C

    val TRACKER_UUIDS = mapOf(
        "feed" to "Tile tracker", "fd84" to "Tile tracker", "fd5a" to "Samsung SmartTag",
        "fe33" to "Chipolo tracker", "fe65" to "Chipolo tracker",
        "fcb2" to "Apple Find My accessory", "fd44" to "Apple Find My accessory",
    )

    /** 16-bit alias of a Bluetooth base UUID ("0000fffa-0000-1000-8000-00805f9b34fb" -> "fffa"). */
    fun shortUuid(u: String): String {
        val l = u.lowercase()
        return if (l.endsWith(BASE_SUFFIX) && l.startsWith("0000")) l.substring(4, 8) else l
    }

    fun classify(
        manufacturerData: Map<Int, ByteArray>,
        serviceData: Map<String, ByteArray>,
        serviceUuids: List<String>,
        name: String? = null,
    ): Classification {
        val sd = serviceData.mapKeys { shortUuid(it.key) }
        sd[REMOTE_ID_UUID]?.let {
            return Classification(Kind.DRONE, "Remote ID broadcast", RemoteId.fromBleServiceData(it))
        }
        manufacturerData[APPLE]?.let { a ->
            if (a.size >= 2 && (a[0].toInt() and 0xFF) == 0x12) {
                // Long payload = SEPARATED from its owner (what a planted AirTag looks like).
                // Short (len 0x02) = its owner's device is in range: normal, not a threat.
                return if ((a[1].toInt() and 0xFF) >= 0x10)
                    Classification(Kind.TRACKER, "Apple Find My tracker SEPARATED from its owner")
                else Classification(Kind.AMBIENT, "Apple device with owner nearby (normal)")
            }
        }
        for (u in sd.keys + serviceUuids.map { shortUuid(it) }) {
            TRACKER_UUIDS[u]?.let { return Classification(Kind.TRACKER, it) }
        }
        val n = (name ?: "").lowercase()
        if (listOf("airtag", "smarttag", "tile", "chipolo", "pebblebee", "tracker").any { it in n }) {
            return Classification(Kind.TRACKER, "tracker by name ($name)")
        }
        return Classification(Kind.NONE)
    }
}
