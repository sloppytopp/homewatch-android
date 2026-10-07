package io.github.sloppytopp.homewatch

import io.github.sloppytopp.homewatch.detect.*
import org.junit.Assert.*
import org.junit.Test

class FollowTest {
    private val min = 60_000L
    private fun place(vararg ids: String) = ids.toSet()
    private val home = place("a1", "a2", "a3", "a4")
    private val work = place("b1", "b2", "b3", "b4")
    private val shop = place("c1", "c2", "c3", "c4")

    private fun s(t: Long, key: String, p: Set<String>) = Sight(t * min, key, "Tile tracker", p)

    @Test fun trackerAtThreePlacesOverAnHourIsFollowing() {
        val sights = listOf(s(0, "T", home), s(1, "T", home), s(30, "T", work), s(31, "T", work), s(60, "T", shop), s(61, "T", shop))
        val hits = Follow.analyze(sights)
        assertEquals(1, hits.size)
        assertEquals(3, hits[0].visits.size)
        assertTrue(hits[0].spanMs >= 60 * min)
    }

    @Test fun trackerThatStaysAtHomeIsNotFollowing() {
        val sights = (0..300 step 5).map { s(it.toLong(), "T", home) }
        assertTrue(Follow.analyze(sights).isEmpty())
    }

    @Test fun twoPlacesIsNotEnough() {
        val sights = listOf(s(0, "T", home), s(1, "T", home), s(60, "T", work), s(61, "T", work))
        assertTrue(Follow.analyze(sights).isEmpty())
    }

    @Test fun threePlacesInTenMinutesIsNotEnough() {
        val sights = listOf(s(0, "T", home), s(1, "T", home), s(4, "T", work), s(5, "T", work), s(8, "T", shop), s(9, "T", shop))
        assertTrue(Follow.analyze(sights).isEmpty())
    }

    @Test fun driveByPlaceWithOneSightingDoesNotCount() {
        val sights = listOf(s(0, "T", home), s(1, "T", home), s(30, "T", work), s(60, "T", shop), s(61, "T", shop))
        assertTrue(Follow.analyze(sights).isEmpty())
    }

    @Test fun returningToAPlaceReusesItsNumber() {
        val sights = listOf(s(0, "T", home), s(30, "T", work), s(60, "T", home))
        assertEquals(listOf(1, 2, 1), Follow.placeNumbers(sights))
    }

    @Test fun slightlyDifferentWifiViewIsStillTheSamePlace() {
        val homeLater = place("a1", "a2", "a3", "zz")
        assertTrue(Follow.same(home, homeLater))
        assertTrue(Follow.same(home, place("a2", "a4")))      // a subset of the home networks is still home
        assertFalse(Follow.same(home, work))
    }

    @Test fun noWifiMeansNoPlace() {
        assertFalse(Follow.same(emptySet(), emptySet()))
        assertEquals(listOf(-1), Follow.placeNumbers(listOf(s(0, "T", emptySet()))))
        assertEquals(listOf(-1), Follow.placeNumbers(listOf(s(0, "T", place("a1")))))   // one network is too little to say where you are
    }

    /** Regression: a phone sitting at home all night heard a few weak networks in changing combinations; that is ONE place, not four. */
    @Test fun flickeringHomeScansAreOnePlaceNotFollowing() {
        val a = "a1"; val b = "a2"; val c = "a3"; val d = "a4"
        val variants = listOf(place(a, b, c, d), place(b, a, d), place(a, b), place(c, d), place(b, c, d), place(a, c, d), place(d, a), place(c, b))
        val sights = (0 until 300).map { i -> s(i.toLong() * 1, "T", variants[(i * 7) % variants.size]) }
        assertTrue(Follow.analyze(sights).isEmpty())
        assertTrue(Follow.placeNumbers(sights).toSet().size <= 2)
    }

    /** Regression (review finding): a route where each stretch overlaps the last must not collapse into one ever-growing "place". */
    @Test fun slowRouteWithOverlappingNetworksStillSplitsIntoPlaces() {
        // every network is heard for ~4 scans, a new one enters as the oldest leaves: consecutive scans always overlap by 3 of 4
        val route = (0 until 240).map { i -> s(i.toLong(), "T", (i / 4 until i / 4 + 4).map { "n$it" }.toSet()) }
        val places = Follow.placeNumbers(route).filter { it > 0 }.toSet()
        assertTrue("route collapsed into $places", places.size >= 3)
        assertEquals(1, Follow.analyze(route).size)
    }

    @Test fun fingerprintHashesAndKeepsStrongest() {
        val obs = (1..12).map { WifiObs("AA:BB:CC:DD:EE:%02X".format(it), "n$it", -40 - it) } + WifiObs("11:22:33:44:55:66", "weak", -95)
        val fp = Follow.fingerprint(obs)
        assertEquals(8, fp.size)
        assertTrue(fp.none { it.contains(":") })
    }

    @Test fun engineRaisesAlertWhenTrackerFollows() {
        var now = 0L
        val e = Engine(clock = { now })
        e.running = true; e.wifiEnabled = true
        fun at(tMin: Long, vararg macs: String) {
            now = (1000 + tMin) * min
            e.onWifiScan(macs.mapIndexed { i, m -> WifiObs(m, "n$i", -50 - i) })
            e.onAdvertisement("T1", -60, Classification(Kind.TRACKER, "Tile tracker"))
            e.snapshot()
        }
        val H = arrayOf("00:00:00:00:00:01", "00:00:00:00:00:02", "00:00:00:00:00:03")
        val W = arrayOf("00:00:00:00:01:01", "00:00:00:00:01:02", "00:00:00:00:01:03")
        val S = arrayOf("00:00:00:00:02:01", "00:00:00:00:02:02", "00:00:00:00:02:03")
        at(0, *H); at(2, *H); at(32, *W); at(34, *W); at(64, *S)
        at(66, *S)
        val snap = e.snapshot()
        assertEquals(Level.ALERT, snap.tracker.level)
        assertTrue(snap.tracker.message, snap.tracker.message.contains("FOLLOWED"))
        assertEquals(1, snap.follow.size)
    }
}

class EvidenceTest {
    private val ev = listOf(
        EventRow(1_000_000, "tracker", Level.WATCH, "Tile tracker nearby: AA rssi=-71"),
        EventRow(2_000_000, "tracker", Level.ALERT, "Tile tracker has FOLLOWED you"),
        EventRow(3_000_000, "host", Level.OK, "ignored info line"),
    )
    private val utc = java.util.TimeZone.getTimeZone("UTC")

    @Test fun reportVerifiesAndOnlyListsFlaggedEvents() {
        val r = Evidence.build(5_000_000, ev, emptyList(), emptyList(), utc)
        assertTrue(Evidence.verify(r))
        assertTrue(r.contains("2 watch/alert events"))
        assertFalse(r.contains("ignored info line"))
    }

    @Test fun editingALineBreaksTheChain() {
        val r = Evidence.build(5_000_000, ev, emptyList(), emptyList(), utc)
        assertFalse(Evidence.verify(r.replace("rssi=-71", "rssi=-99")))
    }

    @Test fun deletingALineBreaksTheChain() {
        val r = Evidence.build(5_000_000, ev, emptyList(), emptyList(), utc)
        val cut = r.lines().filterNot { it.startsWith("0001 |") }.joinToString("\n")
        assertFalse(Evidence.verify(cut))
    }

    @Test fun emptyReportStillVerifies() {
        assertTrue(Evidence.verify(Evidence.build(5_000_000, emptyList(), emptyList(), emptyList(), utc)))
    }

    @Test fun editingTheSummaryOrTrailBreaksTheChain() {
        val hit = FollowHit("T", "Tile tracker", listOf(Visit(1, 0, 60_000, 3), Visit(2, 1_800_000, 1_860_000, 3), Visit(3, 3_600_000, 3_660_000, 3)))
        val r = Evidence.build(5_000_000, ev, listOf(hit), emptyList(), utc)
        assertTrue(Evidence.verify(r))
        assertFalse(Evidence.verify(r.replace("3 different places", "1 different places")))
        assertFalse(Evidence.verify(r.replace("place 2:", "place 9:")))
        assertFalse(Evidence.verify(r.replace("Generated: 1970", "Generated: 2024")))
    }

    @Test fun clearTrailForgetsFollowing() {
        var now = 0L
        val e = Engine(clock = { now })
        e.running = true; e.wifiEnabled = true
        val min = 60_000L
        fun at(t: Long, p: Int) {
            now = (1000 + t) * min
            e.onWifiScan((1..3).map { WifiObs("00:00:00:00:%02X:%02X".format(p, it), "n", -50 - it) })
            e.onAdvertisement("T1", -60, Classification(Kind.TRACKER, "Tile tracker")); e.snapshot()
        }
        at(0, 1); at(2, 1); at(32, 2); at(34, 2); at(64, 3); at(66, 3)
        assertEquals(1, e.snapshot().follow.size)
        e.clearTrail()
        assertTrue(e.snapshot().follow.isEmpty())
    }
}

class StalkerwareTest {
    @Test fun matchesKnownPackagesOnly() {
        Stalkerware.loadCsv(sequenceOf("# list version abc1234 (2026-10-03)", "com.thetruth,TheTruthSpy,stalkerware", "com.fone,TheTruthSpy,stalkerware"))
        val hits = Stalkerware.match(listOf("com.android.chrome", "com.thetruth", "org.fdroid.fdroid"))
        assertEquals(listOf(StalkerHit("com.thetruth", "TheTruthSpy")), hits)
        assertTrue(Stalkerware.match(listOf("com.example.safe")).isEmpty())
    }

    @Test fun reviewListPutsHiddenIconAppsFirst() {
        val a = ReviewApp("a", "Alpha", listOf("accessibility"), noIcon = false)
        val b = ReviewApp("b", "Beta", listOf("device admin"), noIcon = true)
        assertEquals(listOf("b", "a"), Stalkerware.rank(listOf(a, b)).map { it.pkg })
    }
}

class NewNetworkTest {
    private fun scan(vararg macs: String) = macs.mapIndexed { i, m -> WifiObs(m, "net$i", -50 - i) }

    @Test fun firstScanLearnsSilentlyThenNewNetworkIsLogged() {
        var now = 1_000_000L
        val saved = ArrayList<String>()
        val e = Engine(clock = { now }, netSink = { saved += it })
        e.running = true; e.wifiEnabled = true
        e.loadKnownNets(emptySet())
        e.onWifiScan(scan("AA:00:00:00:00:01", "AA:00:00:00:00:02"))
        assertTrue(e.snapshot().events.none { it.msg.contains("New Wi-Fi") })
        assertEquals(2, saved.size)
        now += 60_000
        e.onWifiScan(scan("AA:00:00:00:00:01", "AA:00:00:00:00:02", "BB:00:00:00:00:09"))
        val ev = e.snapshot().events.filter { it.msg.contains("New Wi-Fi network appeared") }
        assertEquals(1, ev.size)
        assertTrue(ev[0].msg.contains("BB:00:00:00:00:09"))
        now += 60_000
        e.onWifiScan(scan("BB:00:00:00:00:09"))   // already known: no repeat
        assertEquals(1, e.snapshot().events.count { it.msg.contains("New Wi-Fi network appeared") })
    }

    @Test fun neverLoadedMeansNoNewNetworkEvents() {
        val e = Engine(clock = { 5_000_000L })
        e.running = true; e.wifiEnabled = true
        e.onWifiScan(scan("AA:00:00:00:00:01")); e.onWifiScan(scan("AA:00:00:00:00:01", "CC:00:00:00:00:01"))
        assertTrue(e.snapshot().events.isEmpty())
    }

    /** A report produced by the Linux tool (n0rma evidence) must verify here too, and fail when edited. */
    @Test fun reportFromTheLinuxToolVerifies() {
        val linux = "N0RMA EVIDENCE REPORT\nGenerated: 1970-02-27 15:53:20 EST\n\nSUMMARY (plain language)\n- This report lists what this computer's Bluetooth, Wi-Fi and network checks saw: nearby trackers, drone broadcasts and new or unknown devices.\n- 2 watch/alert events are listed below, from 1970-01-12 08:46:40 EST to 1970-01-23 22:33:20 EST.\n- Limits: this is signal evidence, not proof of who placed a device. A computer stays in one place, so this report cannot show a tracker following you. Cellular/GPS trackers cannot be heard. Remote ID drone broadcasts can be faked.\n\nEVENT LOG  (line number | time | level | area | detail | chained hash)\n0001 | 1970-01-12 08:46:40 EST | watch | tracker | Tile nearby rssi=-71 | 4bdda4f3b786\n0002 | 1970-01-23 22:33:20 EST | alert | tracker | Tile persistent | 35031361394e\n\nCHAIN START: 5caaefd8bfa6  (generated-at 5000000000 ms)\nCHAIN END (final hash): 35031361394eb4a7ac6178b97699d8bdfdae6c2999c3e753d725edc326b00799\nTo keep this tamper-evident, email or text the CHAIN END value to yourself or an advocate right now: it fixes the time and content.\n"
        assertTrue(Evidence.verify(linux))
        assertFalse(Evidence.verify(linux.replace("rssi=-71", "rssi=-99")))
    }

    @Test fun textAddedAfterTheChainFailsVerification() {
        val r = Evidence.build(5_000_000, listOf(EventRow(1_000_000, "tracker", Level.ALERT, "Tile persistent")), emptyList(), emptyList(), java.util.TimeZone.getTimeZone("UTC"))
        assertTrue(Evidence.verify(r))
        assertFalse(Evidence.verify(r + "NOTE: everything above is fine.\n"))
        assertFalse(Evidence.verify(r.replace("fixes the time and content.", "is optional.")))
    }
}
