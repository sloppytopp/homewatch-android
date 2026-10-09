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


class WifiTest {
    private var t = 5_000_000L
    private fun engine(alerts: MutableList<String> = mutableListOf()) =
        Engine({ t }, { d, _ -> alerts += d }).also { it.running = true; it.wifiEnabled = true }

    private val ridIe = byteArrayOf(0x0D, 7) + pack(basicMsg("WIFIDRONE9"), locMsg(10.0, 20.0), sysMsg(11.0, 21.0))

    @Test fun remoteIdBeaconIsDroneClaim() {
        val alerts = mutableListOf<String>()
        val e = engine(alerts)
        e.onWifiScan(listOf(WifiObs("de:ad:be:ef:00:01", "", -60, listOf(ridIe))))
        val s = e.snapshot()
        assertEquals(Level.ALERT, s.drone.level)
        assertTrue(s.drone.message.contains("CLAIMS"))
        assertEquals("WIFIDRONE9", s.fixes.single().id)
        assertTrue(s.events.none { it.msg.contains("11.0000") || it.msg.contains("21.0000") }) // operator never logged
        assertEquals(listOf("drone"), alerts)
    }
    @Test fun droneSsidAndCameraSsid() {
        val e = engine()
        e.onWifiScan(listOf(WifiObs("aa:bb:cc:00:00:01", "DJI-MAVIC3-ABC", -60), WifiObs("02:11:22:33:44:55", "HDWifiCam_8F2A", -40)))
        val s = e.snapshot()
        assertEquals(Level.ALERT, s.drone.level)
        assertEquals(Level.ALERT, s.camera.level) // -40 dBm = very close
        assertTrue(s.camera.message.contains("VERY CLOSE"))
    }
    @Test fun weakCameraIsWatchOnly() {
        val e = engine()
        e.onWifiScan(listOf(WifiObs("aa:bb:cc:00:00:02", "Cams", -92)))
        assertEquals(Level.WATCH, e.snapshot().camera.level)
    }
    @Test fun sisterNetworksFromOneRadioLogOnce() {
        val e = engine(); t = 1_700_000_000_000L   // a real-looking clock: emit's cooldown counts from 0
        repeat(6) { t += 40 * 60_000L; e.onWifiScan(listOf(WifiObs("e0:b2:60:41:b1:79", "Cams", -88), WifiObs("e0:b2:60:41:b1:89", "Cams", -90))); e.snapshot() }
        assertEquals(1, e.snapshot().events.count { it.domain == "camera" })   // 4 h, two sister BSSIDs: still one entry
    }
    @Test fun markedCameraSourceIsNotFlagged() {
        val mine = setOf("wifi:e0:b2:60:41:b1")
        val e = Engine({ t }, { _, _ -> }, isMine = { it in mine }).also { it.running = true; it.wifiEnabled = true }
        e.onWifiScan(listOf(WifiObs("e0:b2:60:41:b1:79", "Cams", -88), WifiObs("e0:b2:60:41:b1:89", "Cams", -40)))
        assertEquals(Level.OK, e.snapshot().camera.level)
    }
    @Test fun flaggedItemOffersItsMineKey() {
        val e = engine()
        e.onWifiScan(listOf(WifiObs("E0:B2:60:41:B1:79", "Cams", -88)))
        assertEquals("wifi:e0:b2:60:41:b1", e.snapshot().camera.mineKey)
    }
    @Test fun ordinaryNetworksAreOk() {
        val e = engine()
        e.onWifiScan(listOf(WifiObs("aa:bb:cc:00:00:03", "HomeWiFi", -50), WifiObs("aa:bb:cc:00:00:04", "Neighbor", -75)))
        val s = e.snapshot()
        assertEquals(Level.OK, s.camera.level); assertEquals(Level.OK, s.drone.level)
        assertEquals(2, s.wifi.size)
    }
    @Test fun ouiLookupClearsLocallyAdministeredBit() {
        WifiClassifier.loadCsv(sequenceOf("2857BE,Zhejiang Dahua Technology,camera"))
        assertEquals("camera", WifiClassifier.lookup("28:57:be:00:00:01")!!.klass)
        assertEquals("camera", WifiClassifier.lookup("2a:57:be:00:00:01")!!.klass) // virtual AP of the same maker
        assertNull(WifiClassifier.lookup("10:5a:95:39:ef:88"))
        WifiClassifier.loadCsv(emptySequence())
    }
    @Test fun staleWifiScanGoesQuiet() {
        val e = engine()
        e.onWifiScan(listOf(WifiObs("aa:bb:cc:00:00:02", "Cams", -92)))
        t += 200_000
        assertEquals(Level.OFF, e.snapshot().camera.level)
    }
}

class ReportAndGeoTest {
    @Test fun beepWithNothingNearby() {
        val r = Report.build(listOf(1_000_000L), emptyList(), 5, 2_000_000L)
        assertTrue(r.any { it.contains("0 of 1 beeps") }); assertTrue(r.any { it.contains("likely cause") })
    }
    @Test fun beepNearDetectionAndBaseline() {
        val ev = listOf(EventRow(1_100_000L, "tracker", Level.WATCH, "Tile nearby"))
        val r = Report.build(listOf(1_000_000L), ev, 5, 2_000_000L)
        assertTrue(r.any { it.contains("1 of 1 beeps") }); assertTrue(r.any { it.contains("Background") })
    }
    @Test fun geoDistanceAndBearing() {
        val d = Geo.distanceM(40.0018, -100.0, 40.0, -100.0)
        assertEquals(200.0, d, 1.5)
        assertEquals("N", Geo.compass(Geo.bearingDeg(40.0018, -100.0, 40.0, -100.0)))
        assertEquals("E", Geo.compass(Geo.bearingDeg(40.0, -99.998, 40.0, -100.0)))
        assertTrue(Geo.radarFraction(2.0) < 0.33 && Geo.radarFraction(12.0) in 0.33..0.66 && Geo.radarFraction(100.0) > 0.9)
    }
}


class DirectionTest {
    @Test fun strongestSectorWins() {
        val d = DirectionFinder()
        for (h in 0 until 360 step 10) {                        // full turn, loudest around 90 degrees (east)
            val rssi = if (h in 60..120) -60 else -78
            d.add(h.toDouble(), rssi); d.add(h + 3.0, rssi)
        }
        val r = d.result()!!
        assertEquals(90.0, r.bearingDeg, 25.0)
        assertTrue(r.confident)
    }
    @Test fun noClearSideIsNotConfident() {
        val d = DirectionFinder()
        for (h in 0 until 360 step 10) { d.add(h.toDouble(), -70); d.add(h + 3.0, -71) }
        assertFalse(d.result()!!.confident)
    }
    @Test fun incompleteTurnGivesNoAnswer() {
        val d = DirectionFinder()
        for (h in 0..90 step 5) d.add(h.toDouble(), -60)
        assertNull(d.result())
    }
    @Test fun trendWarmerColderSteady() {
        assertEquals("warmer", Trend.of(listOf(-85, -84, -84, -78, -76, -75)))
        assertEquals("colder", Trend.of(listOf(-60, -61, -62, -70, -72, -73)))
        assertEquals("steady", Trend.of(listOf(-70, -71, -70, -70, -71, -70)))
        assertNull(Trend.of(listOf(-70, -71)))
    }
}


class MineAndSmoothTest {
    private var t = 9_000_000L
    private val sep = Classification(Kind.TRACKER, "Tile tracker")

    @Test fun ownTrackerNeverFlags() {
        val mine = setOf("AA")
        val alerts = mutableListOf<String>()
        val e = Engine({ t }, { d, _ -> alerts += d }, isMine = { it in mine }).also { it.running = true }
        repeat(12) { t += 40_000; e.onAdvertisement("AA", -55, sep) }   // persistent AND close - but it is yours
        val s = e.snapshot()
        assertEquals(Level.OK, s.tracker.level)
        assertTrue(s.tracker.message.contains("your own"))
        assertTrue(s.trackers.single().mine)
        assertTrue(alerts.isEmpty())
    }
    @Test fun unknownTrackerStillFlagsNextToYours() {
        val e = Engine({ t }, isMine = { it == "AA" }).also { it.running = true }
        e.onAdvertisement("AA", -50, sep); e.onAdvertisement("BB", -60, sep)
        val s = e.snapshot()
        assertEquals(Level.WATCH, s.tracker.level)
        assertTrue(s.tracker.message.contains("BB"))
    }
    @Test fun smootherHandlesWrapAround() {
        val sm = AngleSmoother(0.5)
        sm.update(359.0)
        val x = sm.update(1.0)
        assertTrue("wrap should stay near north, was $x", x > 350 || x < 10)
    }
}


class RoomsTest {
    private fun scan(room: String, vararg i: RoomItem) = RoomScan(room, 1L, i.toList())
    private fun w(key: String, label: String, rssi: Double) = RoomItem("wifi:$key", "wifi", label, rssi)
    private fun t(key: String, rssi: Double) = RoomItem("ble:$key", "tracker", "Tile tracker", rssi)

    @Test fun collectorAverages() {
        val c = SweepCollector()
        fun snap(r: Int) = Snapshot(wifi = listOf(WifiRow("Net", "aa", r, "", "other")))
        c.sample(snap(-60)); c.sample(snap(-70))
        assertEquals(-65.0, c.result("Bedroom", 5).items.single().rssi, 1e-9)
    }
    @Test fun diffFindsNewGoneAndLouder() {
        val before = scan("Bedroom", w("a", "Home", -50.0), w("b", "Old", -60.0), w("c", "Printer", -80.0))
        val now = scan("Bedroom", w("a", "Home", -49.0), w("c", "Printer", -62.0), t("x", -55.0))
        val d = RoomBook.diff(before, now)
        assertEquals(listOf("ble:x"), d.new.map { it.key })
        assertEquals(listOf("wifi:b"), d.gone.map { it.key })
        assertEquals(listOf("wifi:c"), d.louder.map { it.first.key })
    }
    @Test fun noPreviousScanMeansNoDiff() {
        val d = RoomBook.diff(null, scan("Bedroom", w("a", "Home", -50.0)))
        assertTrue(d.new.isEmpty() && d.gone.isEmpty())
    }
    @Test fun likelyRoomPicksLoudestWithMargin() {
        val latest = mapOf(
            "Garage" to scan("Garage", t("x", -52.0)),
            "Bedroom" to scan("Bedroom", t("x", -81.0)),
            "Kitchen" to scan("Kitchen", t("x", -75.0)),
        )
        val l = RoomBook.likelyRoom("ble:x", latest)!!
        assertEquals("Garage", l.room); assertEquals(23.0, l.marginDb!!, 1e-9)
        assertEquals("Garage", RoomBook.roomSpecific(latest).single().second.room)
    }
    @Test fun heardInOneRoomOnly() {
        val latest = mapOf("Garage" to scan("Garage", t("x", -60.0)), "Kitchen" to scan("Kitchen"))
        assertNull(RoomBook.likelyRoom("ble:x", latest)!!.marginDb)
    }
}


class SurveyExportTest {
    // walk east along a street; the source is a few metres north of the 3rd stop and loudest there
    private fun pts(): List<SurveyPoint> = listOf(
        SurveyPoint(1_000, "wifi", "wifi:aa:bb:cc:dd:ee:ff", "Cam & Co", -85, 40.00000, -100.00060, 5f, "[WPA2-PSK-CCMP][ESS]", 2437, "Hikvision", "camera"),
        SurveyPoint(2_000, "wifi", "wifi:aa:bb:cc:dd:ee:ff", "Cam & Co", -70, 40.00000, -100.00030, 5f, "[WPA2-PSK-CCMP][ESS]", 2437, "Hikvision", "camera"),
        SurveyPoint(3_000, "wifi", "wifi:aa:bb:cc:dd:ee:ff", "Cam & Co", -50, 40.00000, -100.00000, 5f, "[WPA2-PSK-CCMP][ESS]", 2437, "Hikvision", "camera"),
        SurveyPoint(4_000, "wifi", "wifi:aa:bb:cc:dd:ee:ff", "Cam & Co", -72, 40.00000, -99.99970, 5f, "[WPA2-PSK-CCMP][ESS]", 2437, "Hikvision", "camera"),
    )

    @Test fun estimateLandsNearLoudestStop() {
        val e = Survey.estimate(pts()).single()
        assertEquals(-100.0, e.lon, 0.0002)           // within ~15 m of the loudest stop
        assertTrue(e.radiusM >= 5.0)
        assertEquals(4, e.samples)
    }
    @Test fun noMovementMeansNoEstimate() {
        val still = (1..5).map { SurveyPoint(it * 1000L, "wifi", "wifi:x", "X", -60, 40.0, -100.0, 5f) }
        assertTrue(Survey.estimate(still).isEmpty())
    }
    @Test fun gpsJitterIsNotAWalk() {
        // phone standing still: fixes wander a few metres (0.00004 deg ~ 4 m) with 7 m accuracy
        val jitter = listOf(0.0, 0.00003, -0.00002, 0.00004, -0.00003).mapIndexed { i, d ->
            SurveyPoint(i * 1000L, "wifi", "wifi:x", "X", -60 + i, 40.0 + d, -100.0 + d, 7f)
        }
        assertTrue(Survey.estimate(jitter).isEmpty())
    }
    @Test fun clusteredEstimatesAreFlagged() {
        fun e(k: String, la: Double, lo: Double) = Estimate(k, "wifi", k, la, lo, 10.0, 5, -60, "", "other", "")
        assertTrue(Survey.clustered(listOf(e("a", 40.0, -100.0), e("b", 40.00005, -100.00005), e("c", 40.0001, -100.0))))
        assertFalse(Survey.clustered(listOf(e("a", 40.0, -100.0), e("b", 40.001, -100.0), e("c", 40.0, -100.001))))
        assertFalse(Survey.clustered(listOf(e("a", 40.0, -100.0))))
    }
    @Test fun kmlEscapesAndHasPins() {
        val kml = Export.kml(40.0 to -100.0, pts(), Survey.estimate(pts()), 0L)
        assertTrue(kml.contains("Cam &amp; Co"))
        assertTrue(kml.contains("<name>Home</name>"))
        assertTrue(kml.contains("<LineString>"))
        assertTrue(kml.contains("#red"))
    }
    @Test fun wigleCsvFormat() {
        val csv = Export.wigleCsv(pts(), "0.4", "TCL 5087Z", "11", "dev", "TCL").lines()
        assertTrue(csv[0].startsWith("WigleWifi-1.4,appRelease=0.4"))
        assertEquals("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type", csv[1])
        assertTrue(csv[2].startsWith("aa:bb:cc:dd:ee:ff,Cam & Co,[WPA2-PSK-CCMP][ESS],1970-01-01 00:00:01,6,-85,40.0,-100.0006,0,5.0,WIFI"))
        assertEquals(1, Export.channel(2412)); assertEquals(11, Export.channel(2462)); assertEquals(14, Export.channel(2484)); assertEquals(36, Export.channel(5180))
    }
    @Test fun csvInjectionIsNeutralised() {
        for (evil in listOf("=HYPERLINK(\"http://x\")", "+1+1", "-2+3", "@SUM(A1)", "\t=1")) {
            val line = Export.wigleCsv(listOf(SurveyPoint(0, "wifi", "wifi:aa", evil, -60, 1.0, 2.0, 3f)), "x", "m", "r", "d", "b").lines()[2]
            val ssid = line.substringAfter("aa,").let { if (it.startsWith("\"")) it.drop(1) else it }
            assertTrue("formula start must be defused: $evil -> $ssid", ssid.startsWith("'"))
        }
        // ordinary names stay untouched
        assertTrue(Export.wigleCsv(listOf(SurveyPoint(0, "wifi", "wifi:aa", "HomeWiFi", -60, 1.0, 2.0, 3f)), "x", "m", "r", "d", "b").contains("aa,HomeWiFi,"))
    }
    @Test fun csvQuotesCommas() {
        val p = SurveyPoint(0, "wifi", "wifi:aa", "My, \"net\"", -60, 1.0, 2.0, 3f)
        assertTrue(Export.wigleCsv(listOf(p), "x", "m", "r", "d", "b").contains("\"My, \"\"net\"\"\""))
    }
}


class DemoModeTest {
    @Test fun demoNeverLogsOrAlerts() {
        var t = 1_000L
        val logged = mutableListOf<EventRow>(); val alerts = mutableListOf<String>()
        val e = Engine({ t }, { d, _ -> alerts += d }, { logged += it })
        e.demoMode = true; e.running = true
        e.onAdvertisement("X", -60, Classification(Kind.DRONE, "Remote ID broadcast", RemoteIdInfo(basicId = "D", lat = 1.0, lon = 2.0)))
        val s = e.snapshot()
        assertEquals(Level.ALERT, s.drone.level)      // tiles still show the sample
        assertTrue(s.demo)
        assertTrue(logged.isEmpty() && alerts.isEmpty())   // but nothing is saved or pushed
        e.demoMode = false
        t += 500_000
        e.onAdvertisement("Y", -60, Classification(Kind.DRONE, "Remote ID broadcast", RemoteIdInfo(basicId = "E", lat = 1.0, lon = 2.0)))
        e.snapshot()
        assertEquals(1, logged.size)                  // real sightings are logged again
    }
}
