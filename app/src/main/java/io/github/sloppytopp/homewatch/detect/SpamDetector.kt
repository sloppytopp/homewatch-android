package io.github.sloppytopp.homewatch.detect

data class SpamState(val level: Level, val message: String, val distinct: Int = 0, val family: String = "", val strongest: Int = -127)

/**
 * Detects a Bluetooth "pairing pop-up flood" (Flipper Zero / phone-app Bluetooth spam): a burst of pairing advertisements
 * (Apple, Google Fast Pair, Windows Swift Pair) from many DIFFERENT, randomly changing addresses. Real devices repeat the same address,
 * so the test is "many distinct addresses AND almost every advertisement from a new one", over a short window.
 * It is a behaviour test, not a fingerprint of one tool: it keeps working when the attacking tool changes its payloads.
 */
class SpamDetector(
    private val windowMs: Long = 30_000,
    private val watchDistinct: Int = 12,
    private val alertDistinct: Int = 30,
    private val alertSustainMs: Long = 60_000,
    private val minUniqueRatio: Double = 0.6,
) {
    private class Ad(val addr: String, val family: String, val rssi: Int, val ts: Long)
    private val ads = ArrayDeque<Ad>()
    private var floodSince = 0L
    private var quietSince = 0L

    @Synchronized fun onAd(addr: String, family: String, rssi: Int, now: Long) {
        ads.addLast(Ad(addr, family, rssi, now))
        while (ads.size > 5000) ads.removeFirst()
    }

    @Synchronized fun clear() { ads.clear(); floodSince = 0; quietSince = 0 }

    @Synchronized fun evaluate(now: Long): SpamState {
        while (ads.isNotEmpty() && now - ads.first().ts > windowMs) ads.removeFirst()
        val ok = SpamState(Level.OK, "No Bluetooth pop-up flood heard (watching Apple, Google and Windows pairing signals)")
        if (ads.isEmpty()) { settle(now, false); return ok }
        val distinct = ads.map { it.addr }.toSet().size
        val ratio = distinct.toDouble() / ads.size
        val flood = distinct >= watchDistinct && ratio >= minUniqueRatio
        settle(now, flood)
        if (!flood) return ok
        val fam = ads.groupingBy { it.family }.eachCount().maxByOrNull { it.value }!!.key
        val strongest = ads.maxOf { it.rssi }
        val alert = distinct >= alertDistinct && now - floodSince >= alertSustainMs
        val msg = "$distinct different fake pairing signals in ${windowMs / 1000} s ($fam), strongest $strongest dBm. " +
            "Consistent with a Bluetooth spam tool (for example a Flipper Zero or a phone app). Nothing is connecting to you; turn Bluetooth off if pop-ups appear."
        return SpamState(if (alert) Level.ALERT else Level.WATCH, msg, distinct, fam, strongest)
    }

    /** Remember when a flood started; forget it after 15 s of quiet so a later burst starts a fresh clock. */
    private fun settle(now: Long, flood: Boolean) {
        if (flood) { if (floodSince == 0L) floodSince = now; quietSince = 0 }
        else if (floodSince != 0L) { if (quietSince == 0L) quietSince = now; if (now - quietSince > 15_000) { floodSince = 0; quietSince = 0 } }
    }
}
