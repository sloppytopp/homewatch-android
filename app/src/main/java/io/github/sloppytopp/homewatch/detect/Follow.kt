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
    const val MIN_NETWORKS = 2       // a fingerprint of 0-1 networks says too little about where you are

    fun fingerprint(obs: List<WifiObs>): Set<String> =
        obs.filter { it.level >= -85 }.sortedByDescending { it.level }.take(8).map { hash(it.bssid) }.toSet()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.uppercase().toByteArray()).take(4).joinToString("") { "%02x".format(it) }

    /** Is [f] (one scan's fingerprint) the place described by [rep] (every network ever seen there)? Subsets count: a scan that only hears 2 of 4 home networks is still home. */
    fun same(rep: Set<String>, f: Set<String>): Boolean {
        if (rep.isEmpty() || f.size < MIN_NETWORKS) return false
        return f.intersect(rep).size.toDouble() / f.size >= SAME_PLACE
    }

    /** Group sightings into numbered places (in order first visited). Too-thin fingerprints get place -1. A place remembers every network heard there, so flicker at one spot stays one place. */
    fun placeNumbers(sights: List<Sight>): List<Int> {
        val reps = ArrayList<MutableSet<String>>()
        return sights.map { s ->
            if (s.place.size < MIN_NETWORKS) -1 else {
                val i = reps.indexOfFirst { same(it, s.place) }
                if (i >= 0) { reps[i] += s.place; i + 1 } else { reps += s.place.toMutableSet(); reps.size }
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
