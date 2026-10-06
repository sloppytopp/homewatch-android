package io.github.sloppytopp.homewatch.detect

import java.security.MessageDigest

/** One time a (non-yours) tracker was heard, tagged with a coarse "where was I" Wi-Fi fingerprint. No GPS, no coordinates. */
data class Sight(val ts: Long, val key: String, val label: String, val place: Set<String>)

data class Visit(val placeNo: Int, val first: Long, val last: Long, val n: Int)
data class FollowHit(val key: String, val label: String, val visits: List<Visit>) {
    val spanMs get() = (visits.maxOf { it.last } - visits.minOf { it.first })
}

/**
 * "Following" detection: the same tracker heard at several different places over a long enough time.
 * A place is a cluster of similar Wi-Fi fingerprints (hashed IDs of the strongest nearby networks), so it needs no location permission
 * and stores no neighbor MAC addresses. Staying at home never counts as following.
 */
object Follow {
    const val MIN_PLACES = 3
    const val MIN_SPAN_MS = 30 * 60_000L
    const val MIN_SIGHTS_PER_PLACE = 2
    const val SAME_PLACE = 0.50      // share of a fingerprint's networks that must already belong to the place
    const val REGULAR_SHARE = 0.15   // a network describes a place only if heard in this share of the scans there
    const val BOOTSTRAP = 12         // a new place trusts every network for its first scans
    const val MIN_NETWORKS = 2       // a fingerprint of 0-1 networks says too little about where you are

    fun fingerprint(obs: List<WifiObs>): Set<String> =
        obs.filter { it.level >= -85 }.sortedByDescending { it.level }.take(8).map { hash(it.bssid) }.toSet()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.uppercase().toByteArray()).take(4).joinToString("") { "%02x".format(it) }

    /** One place: how often each network was heard there. Only networks heard regularly describe the place, so it cannot grow forever and swallow a whole route. */
    private class Cluster(f: Set<String>) {
        val counts = HashMap<String, Int>().also { m -> f.forEach { m[it] = 1 } }
        var n = 1
        fun add(f: Set<String>) { f.forEach { counts[it] = (counts[it] ?: 0) + 1 }; n++ }
        /** Early on every network counts; later only those heard in at least [REGULAR_SHARE] of the scans here (and at least twice). */
        fun rep(): Set<String> = if (n < BOOTSTRAP) counts.keys else counts.filter { it.value >= 2 && it.value.toDouble() / n >= REGULAR_SHARE }.keys
    }

    /** Does [f] (one scan's fingerprint) belong to the place described by [rep]? A scan that hears only some of the place's networks still belongs. */
    fun same(rep: Set<String>, f: Set<String>): Boolean {
        if (rep.isEmpty() || f.size < MIN_NETWORKS) return false
        return f.intersect(rep).size.toDouble() / f.size >= SAME_PLACE
    }

    /** Group sightings into numbered places (in order first visited). Too-thin fingerprints get place -1. */
    fun placeNumbers(sights: List<Sight>): List<Int> {
        val clusters = ArrayList<Cluster>()
        return sights.map { s ->
            if (s.place.size < MIN_NETWORKS) -1 else {
                val i = clusters.indexOfFirst { same(it.rep(), s.place) }
                if (i >= 0) { clusters[i].add(s.place); i + 1 } else { clusters += Cluster(s.place); clusters.size }
            }
        }
    }

    fun analyze(sights: List<Sight>): List<FollowHit> {
        val sorted = sights.sortedBy { it.ts }
        val nums = placeNumbers(sorted)
        val out = ArrayList<FollowHit>()
        for ((key, idx) in sorted.indices.groupBy { sorted[it].key }) {
            val visits = idx.filter { nums[it] > 0 }.groupBy { nums[it] }.map { (p, ix) ->
                Visit(p, ix.minOf { sorted[it].ts }, ix.maxOf { sorted[it].ts }, ix.size)
            }.filter { it.n >= MIN_SIGHTS_PER_PLACE }.sortedBy { it.first }
            if (visits.size >= MIN_PLACES && visits.maxOf { it.last } - visits.minOf { it.first } >= MIN_SPAN_MS)
                out += FollowHit(key, sorted[idx.last()].label, visits)
        }
        return out
    }
}
