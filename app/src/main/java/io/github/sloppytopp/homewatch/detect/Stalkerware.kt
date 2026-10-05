package io.github.sloppytopp.homewatch.detect

/** One installed app that matched the public stalkerware indicator list. */
data class StalkerHit(val pkg: String, val family: String)

/** An app that is not a known stalkerware package but holds a power stalkerware typically abuses. Worth a look, not proof. */
data class ReviewApp(val pkg: String, val label: String, val powers: List<String>, val noIcon: Boolean)

data class StalkerResult(val scanned: Int, val hits: List<StalkerHit>, val review: List<ReviewApp>, val listVersion: String)

/**
 * Compares installed package names to the Coalition Against Stalkerware indicator list (CC BY 4.0, bundled; nothing is downloaded).
 * Matching by package name only: a renamed or custom build will not match, so a clean result is NOT a guarantee.
 */
object Stalkerware {
    @Volatile var indicators: Map<String, String> = emptyMap()
    @Volatile var version: String = ""

    fun loadCsv(lines: Sequence<String>) {
        val m = HashMap<String, String>()
        for (l in lines) {
            if (l.startsWith("#")) { Regex("version (\\w+ \\([0-9-]+\\))").find(l)?.let { version = it.groupValues[1] }; continue }
            val p = l.split(",")
            if (p.size >= 2 && p[0].isNotBlank()) m[p[0].trim()] = p[1].trim()
        }
        indicators = m
    }

    fun match(installed: Collection<String>): List<StalkerHit> =
        installed.mapNotNull { p -> indicators[p]?.let { StalkerHit(p, it) } }.sortedBy { it.family }

    /** Apps worth reviewing: not in the list, but with accessibility / device-admin / notification-listener power. Hidden-icon ones first. */
    fun rank(review: List<ReviewApp>): List<ReviewApp> =
        review.sortedWith(compareByDescending<ReviewApp> { it.noIcon }.thenByDescending { it.powers.size }.thenBy { it.label.lowercase() })
}
