package io.github.sloppytopp.homewatch.detect

/** Parsed ASTM F3411 / FAA Remote ID fields. Anyone can broadcast these - they are CLAIMS, not facts. */
data class RemoteIdInfo(
    val basicId: String? = null,
    val uaType: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedMs: Double? = null,
    val altM: Double? = null,
    val heightAglM: Double? = null,
    val status: String? = null,
    val operatorLat: Double? = null,
    val operatorLon: Double? = null,
    val operatorId: String? = null,
)

object RemoteId {
    private val UA_TYPES = mapOf(
        0 to "none", 1 to "aeroplane", 2 to "helicopter/multirotor", 3 to "gyroplane", 4 to "VTOL",
        5 to "ornithopter", 6 to "glider", 7 to "kite", 8 to "free balloon", 9 to "captive balloon",
        10 to "airship", 11 to "parachute", 12 to "rocket", 13 to "tethered", 14 to "ground obstacle", 15 to "other",
    )

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    private fun i32(b: ByteArray, o: Int): Int =
        u8(b, o) or (u8(b, o + 1) shl 8) or (u8(b, o + 2) shl 16) or (u8(b, o + 3) shl 24)

    private fun u16(b: ByteArray, o: Int): Int = u8(b, o) or (u8(b, o + 1) shl 8)

    private fun text(b: ByteArray, from: Int, to: Int): String {
        val sb = StringBuilder()
        for (i in from until minOf(to, b.size)) {
            val c = u8(b, i)
            if (c == 0) break
            sb.append(if (c in 32..126) c.toChar() else '?')
        }
        return sb.toString().trim()
    }

    private fun lat(b: ByteArray, o: Int): Double? =
        (i32(b, o) * 1e-7).takeIf { it != 0.0 && it in -90.0..90.0 }

    private fun lon(b: ByteArray, o: Int): Double? =
        (i32(b, o) * 1e-7).takeIf { it != 0.0 && it in -180.0..180.0 }

    private fun alt(b: ByteArray, o: Int): Double? =
        u16(b, o).takeIf { it != 0xFFFF }?.let { Math.round((it * 0.5 - 1000) * 10) / 10.0 } // 0xFFFF = unknown

    private fun merge(a: RemoteIdInfo, n: RemoteIdInfo) = RemoteIdInfo(
        basicId = n.basicId ?: a.basicId, uaType = n.uaType ?: a.uaType,
        lat = n.lat ?: a.lat, lon = n.lon ?: a.lon, speedMs = n.speedMs ?: a.speedMs,
        altM = n.altM ?: a.altM, heightAglM = n.heightAglM ?: a.heightAglM, status = n.status ?: a.status,
        operatorLat = n.operatorLat ?: a.operatorLat, operatorLon = n.operatorLon ?: a.operatorLon,
        operatorId = n.operatorId ?: a.operatorId,
    )

    /** One 25-byte message, or null if it is too short / of an unknown type. */
    fun parseMessage(m: ByteArray): RemoteIdInfo? {
        if (m.size < 25) return null
        return when (u8(m, 0) shr 4) {
            0 -> RemoteIdInfo(basicId = text(m, 2, 22), uaType = UA_TYPES[u8(m, 1) and 0xF] ?: "?")
            1 -> {
                val raw = u8(m, 3)
                // 255 = unknown; multiplier flag -> value*0.75 + 255*0.25 (ASTM F3411)
                val speed = if (raw == 255) null
                else Math.round((if (u8(m, 1) and 1 != 0) raw * 0.75 + 255 * 0.25 else raw * 0.25) * 10) / 10.0
                RemoteIdInfo(
                    lat = lat(m, 5), lon = lon(m, 9), speedMs = speed,
                    altM = alt(m, 15), heightAglM = alt(m, 17),
                    status = when (u8(m, 1) shr 4) { 0 -> "undeclared"; 1 -> "ground"; 2 -> "airborne"; 3 -> "emergency"; else -> "?" },
                )
            }
            4 -> RemoteIdInfo(operatorLat = lat(m, 2), operatorLon = lon(m, 6))
            5 -> RemoteIdInfo(operatorId = text(m, 2, 22))
            else -> RemoteIdInfo()
        }
    }

    /** A bare message or a message pack. Radio input is attacker-controlled: never throws. */
    fun parsePack(buf: ByteArray): RemoteIdInfo = try {
        var out = RemoteIdInfo()
        if (buf.isNotEmpty()) {
            if ((u8(buf, 0) shr 4) == 0xF && buf.size >= 3) {
                val size = u8(buf, 1).let { if (it == 0) 25 else it }
                val count = minOf(u8(buf, 2), 9)
                for (i in 0 until count) {
                    val start = 3 + i * size
                    val end = start + size
                    if (end > buf.size) break
                    parseMessage(buf.copyOfRange(start, end))?.let { out = merge(out, it) }
                }
            } else {
                parseMessage(buf.copyOfRange(0, minOf(25, buf.size)))?.let { out = merge(out, it) }
            }
        }
        out
    } catch (e: Exception) {
        RemoteIdInfo()
    }

    /** BLE 0xFFFA service data: [app code 0x0D][counter][25-byte message or pack]. */
    fun fromBleServiceData(p: ByteArray): RemoteIdInfo =
        if (p.size >= 27 && u8(p, 0) == 0x0D) parsePack(p.copyOfRange(2, p.size)) else parsePack(p)
}
