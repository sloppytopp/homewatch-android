package io.github.sloppytopp.homewatch

import io.github.sloppytopp.homewatch.detect.*
import org.junit.Assert.*
import org.junit.Test

private fun le32(v: Double): ByteArray {
    val i = Math.round(v * 1e7).toInt()
    return byteArrayOf(i.toByte(), (i shr 8).toByte(), (i shr 16).toByte(), (i shr 24).toByte())
}
private fun locMsg(lat: Double, lon: Double): ByteArray {
    val m = ByteArray(25); m[0] = 0x10; m[1] = 0x20; m[3] = 40
    le32(lat).copyInto(m, 5); le32(lon).copyInto(m, 9)
    val alt = ((120 + 1000) * 2); m[15] = alt.toByte(); m[16] = (alt shr 8).toByte()
    return m
}
private fun basicMsg(id: String): ByteArray {
    val m = ByteArray(25); m[0] = 0x00; m[1] = 0x12
    id.toByteArray().copyInto(m, 2); return m
}
private fun sysMsg(lat: Double, lon: Double): ByteArray {
    val m = ByteArray(25); m[0] = 0x40
    le32(lat).copyInto(m, 2); le32(lon).copyInto(m, 6); return m
}
private fun pack(vararg msgs: ByteArray) = byteArrayOf(0xF2.toByte(), 25, msgs.size.toByte()) + msgs.reduce { a, b -> a + b }
private fun u(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"

class RemoteIdTest {
    @Test fun parsesPack() {
        val d = RemoteId.parsePack(pack(basicMsg("1581F4XYZ123"), locMsg(33.5, -85.3), sysMsg(33.51, -85.31)))
        assertEquals("1581F4XYZ123", d.basicId)
        assertEquals(33.5, d.lat!!, 1e-4)
        assertEquals(-85.31, d.operatorLon!!, 1e-4)
        assertEquals("airborne", d.status)
        assertEquals(10.0, d.speedMs!!, 1e-9)
        assertEquals(120.0, d.altM!!, 1e-9)
    }
    @Test fun bleServiceData() {
        assertEquals("ABC123", RemoteId.fromBleServiceData(byteArrayOf(0x0D, 1) + basicMsg("ABC123")).basicId)
    }
    @Test fun unknownValuesAreNull() {
        val m = locMsg(10.0, 20.0); m[3] = 0xFF.toByte(); m[15] = 0xFF.toByte(); m[16] = 0xFF.toByte()
        val d = RemoteId.parseMessage(m)!!
        assertNull(d.speedMs); assertNull(d.altM)
    }
    @Test fun speedMultiplier() {
        val m = locMsg(10.0, 20.0); m[1] = (m[1].toInt() or 1).toByte(); m[3] = 100
        assertEquals(Math.round((100 * 0.75 + 255 * 0.25) * 10) / 10.0, RemoteId.parseMessage(m)!!.speedMs!!, 1e-9)
    }
    @Test fun malformedNeverThrows() {
        assertEquals(RemoteIdInfo(), RemoteId.parsePack(byteArrayOf()))
        assertEquals(RemoteIdInfo(), RemoteId.parsePack(byteArrayOf(0xF2.toByte(), 0x19, 0xFF.toByte(), 0, 0, 0)))
        assertEquals(RemoteIdInfo(), RemoteId.fromBleServiceData(byteArrayOf(0x0D)))
        RemoteId.parsePack(ByteArray(7) { 0xFF.toByte() })
    }
}

class ClassifierTest {
    private fun cls(mfr: Map<Int, ByteArray> = emptyMap(), sd: Map<String, ByteArray> = emptyMap(), uu: List<String> = emptyList(), name: String? = null) =
        BleClassifier.classify(mfr, sd, uu, name).kind

    @Test fun appleSeparatedIsTracker() = assertEquals(Kind.TRACKER, cls(mfr = mapOf(0x004C to byteArrayOf(0x12, 0x19, 0x10) + ByteArray(22))))
    @Test fun appleOwnerNearbyIsAmbient() = assertEquals(Kind.AMBIENT, cls(mfr = mapOf(0x004C to byteArrayOf(0x12, 0x02, 0x00, 0x02))))
    @Test fun airPodsAreNothing() = assertEquals(Kind.NONE, cls(mfr = mapOf(0x004C to byteArrayOf(0x07, 0x19) + ByteArray(20))))
    @Test fun tile() = assertEquals(Kind.TRACKER, cls(uu = listOf(u("feed"))))
    @Test fun smartTag() = assertEquals(Kind.TRACKER, cls(sd = mapOf(u("fd5a") to byteArrayOf(1))))
    @Test fun chipolo() = assertEquals(Kind.TRACKER, cls(uu = listOf(u("fe33"))))
    @Test fun remoteIdAdvertisement() {
        val c = BleClassifier.classify(emptyMap(), mapOf(u("fffa") to byteArrayOf(0x0D, 1) + basicMsg("DRN1")), emptyList())
        assertEquals(Kind.DRONE, c.kind); assertEquals("DRN1", c.remoteId!!.basicId)
    }
    @Test fun garbageServiceDataNeverThrows() = assertEquals(Kind.DRONE, cls(sd = mapOf(u("fffa") to byteArrayOf(1, 2, 3))))
    @Test fun ordinaryDeviceIgnored() = assertEquals(Kind.NONE, cls(name = "Living room speaker"))
}

class EngineTest {
    private var t = 1_000_000L
    private val alerts = mutableListOf<Pair<String, String>>()
    private fun engine() = Engine({ t }, { d, x -> alerts += d to x }).also { it.running = true }
    private val sep = Classification(Kind.TRACKER, "Apple Find My tracker SEPARATED from its owner")

    @Test fun trackerStaysWatchUntilPersistentAndClose() {
        val e = engine()
        e.onAdvertisement("AA", -60, sep)
        assertEquals(Level.WATCH, e.snapshot().tracker.level)
        repeat(10) { t += 40_000; e.onAdvertisement("AA", -60, sep) }   // 400 s, close
        assertEquals(Level.ALERT, e.snapshot().tracker.level)
        assertEquals(1, alerts.count { it.first == "tracker" })
        assertFalse(alerts.joinToString().contains("AA"))                 // push text is generic
    }
    @Test fun weakTrackerNeverAlerts() {
        val e = engine()
        repeat(12) { t += 40_000; e.onAdvertisement("BB", -85, sep) }
        assertEquals(Level.WATCH, e.snapshot().tracker.level)
        assertTrue(alerts.isEmpty())
    }
    @Test fun ambientAppleIgnored() {
        val e = engine()
        repeat(5) { e.onAdvertisement("CC", -40, Classification(Kind.AMBIENT, "owner nearby")) }
        val s = e.snapshot()
        assertEquals(Level.OK, s.tracker.level); assertEquals(5, s.ambientIgnored)
    }
    @Test fun trackerClearsAfterSilence() {
        val e = engine()
        e.onAdvertisement("DD", -60, sep)
        t += 301_000
        assertEquals(Level.OK, e.snapshot().tracker.level)
    }
    @Test fun droneIsAClaimAndOperatorIsNotLogged() {
        val e = engine()
        val info = RemoteIdInfo(basicId = "D1", lat = 33.1, lon = -85.1, operatorLat = 33.2, operatorLon = -85.2)
        e.onAdvertisement("X", -70, Classification(Kind.DRONE, "Remote ID broadcast", info))
        val s = e.snapshot()
        assertEquals(Level.ALERT, s.drone.level)
        assertTrue(s.drone.message.contains("CLAIMS"))
        assertTrue(s.drone.message.contains("33.20000"))                   // live view shows operator
        assertTrue(s.events.none { it.msg.contains("33.2") || it.msg.contains("-85.2") }) // log never does
        assertEquals("A drone signal was detected near the house.", alerts.single { it.first == "drone" }.second)
    }
}
