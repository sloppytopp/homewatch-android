package io.github.sloppytopp.homewatch.detect

/** A small, very bright point on a dark surround: a possible camera-lens glint. Coordinates are 0..1 across the image. */
data class GlintSpot(val x: Float, val y: Float, val radius: Float, val score: Float)

data class LensResult(val spots: List<GlintSpot>, val meanLuma: Int) {
    /** A lit room swamps glints with ordinary reflections; ask the user to dim it. */
    val tooBright get() = meanLuma > 110
}

/**
 * Finds compact, saturated bright points surrounded by darkness in a luma (brightness) image.
 * A camera lens lit by a nearby light throws a tiny bright reflection back at the light; so do screws, jewellery and glass,
 * so every spot is only a place to look, never proof.
 */
object LensSpotter {
    private const val HI = 240            // "saturated" brightness
    private const val RING_MAX = 110      // the surround must be dark
    private const val MIN_FILL = 0.5f     // blob must fill its bounding box (round-ish, not a streak)
    private const val MAX_ASPECT = 2.5f

    /** [luma] is a plane of 0..255 bytes; [rowStride] bytes per row; the image is sampled in [step]-pixel blocks (max of each block, so tiny glints survive). */
    fun find(luma: ByteArray, w: Int, h: Int, rowStride: Int, step: Int = 2, maxSpots: Int = 5): LensResult {
        val gw = w / step; val gh = h / step
        if (gw < 8 || gh < 8) return LensResult(emptyList(), 0)
        val g = IntArray(gw * gh)
        var sum = 0L
        for (y in 0 until gh) for (x in 0 until gw) {
            var m = 0
            for (dy in 0 until step) for (dx in 0 until step) {
                val v = luma[(y * step + dy) * rowStride + x * step + dx].toInt() and 0xFF
                if (v > m) m = v
            }
            g[y * gw + x] = m; sum += m
        }
        val mean = (sum / g.size).toInt()
        val seen = BooleanArray(g.size)
        val maxArea = (gw * gh * 0.004).toInt().coerceAtLeast(6)
        val out = ArrayList<GlintSpot>()
        val stack = IntArray(g.size)
        for (start in g.indices) {
            if (seen[start] || g[start] < HI) continue
            // flood-fill this bright blob (4-connected)
            var sp = 0; stack[sp++] = start; seen[start] = true
            var area = 0; var minX = gw; var maxX = 0; var minY = gh; var maxY = 0; var sx = 0L; var sy = 0L
            while (sp > 0) {
                val i = stack[--sp]; val x = i % gw; val y = i / gw
                area++; sx += x; sy += y
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                if (area > maxArea * 4) { /* huge: keep marking but stop growing stats cost */ }
                if (x > 0 && !seen[i - 1] && g[i - 1] >= HI) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < gw - 1 && !seen[i + 1] && g[i + 1] >= HI) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && !seen[i - gw] && g[i - gw] >= HI) { seen[i - gw] = true; stack[sp++] = i - gw }
                if (y < gh - 1 && !seen[i + gw] && g[i + gw] >= HI) { seen[i + gw] = true; stack[sp++] = i + gw }
            }
            if (area > maxArea) continue
            val bw = maxX - minX + 1; val bh = maxY - minY + 1
            if (area.toFloat() / (bw * bh) < MIN_FILL) continue
            if (maxOf(bw, bh).toFloat() / minOf(bw, bh) > MAX_ASPECT) continue
            // dark surround: mean brightness of the ring 3 cells outside the box
            var rs = 0L; var rn = 0
            for (y in (minY - 3).coerceAtLeast(0)..(maxY + 3).coerceAtMost(gh - 1)) for (x in (minX - 3).coerceAtLeast(0)..(maxX + 3).coerceAtMost(gw - 1)) {
                if (x in minX..maxX && y in minY..maxY) continue
                rs += g[y * gw + x]; rn++
            }
            val ring = if (rn == 0) 255 else (rs / rn).toInt()
            if (ring > RING_MAX) continue
            val contrast = (255 - ring) / 255f
            val score = contrast / (1f + area / 20f) * (area.toFloat() / (bw * bh))
            out += GlintSpot((sx.toFloat() / area + 0.5f) / gw, (sy.toFloat() / area + 0.5f) / gh, maxOf(bw, bh) / 2f / gw + 0.01f, score)
        }
        return LensResult(out.sortedByDescending { it.score }.take(maxSpots), mean)
    }
}
