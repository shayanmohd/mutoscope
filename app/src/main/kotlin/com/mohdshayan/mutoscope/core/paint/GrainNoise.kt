package com.mohdshayan.mutoscope.core.paint

/**
 * Seeded value noise for the pencil's paper grain, generated in code so no texture file ships.
 * Two octaves of smoothed lattice noise give the tooth; the result is an alpha from [floor] to 1.
 */
object GrainNoise {

    fun tile(size: Int, seed: Int, floor: Float = 0.35f): FloatArray {
        val out = FloatArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = 0.65f * value(x / 3f, y / 3f, seed, size / 3) + 0.35f * value(x.toFloat(), y.toFloat(), seed + 101, size)
                out[y * size + x] = floor + (1f - floor) * n
            }
        }
        return out
    }

    /** Smooth noise in 0..1 that wraps every [period] lattice cells, so the tile repeats cleanly. */
    fun value(x: Float, y: Float, seed: Int, period: Int): Float {
        val x0 = kotlin.math.floor(x).toInt()
        val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val p = period.coerceAtLeast(1)
        val a = hash(Math.floorMod(x0, p), Math.floorMod(y0, p), seed)
        val b = hash(Math.floorMod(x0 + 1, p), Math.floorMod(y0, p), seed)
        val c = hash(Math.floorMod(x0, p), Math.floorMod(y0 + 1, p), seed)
        val d = hash(Math.floorMod(x0 + 1, p), Math.floorMod(y0 + 1, p), seed)
        val top = a + (b - a) * sx
        val bottom = c + (d - c) * sx
        return top + (bottom - top) * sy
    }

    fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 144665
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }
}
