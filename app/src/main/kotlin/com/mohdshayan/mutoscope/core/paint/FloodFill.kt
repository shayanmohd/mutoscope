package com.mohdshayan.mutoscope.core.paint

/**
 * Scanline flood fill over ARGB pixels. A pixel joins the fill when every channel, alpha
 * included, is within [tolerance] of the seed. With [closeGap] above zero the boundary (every
 * pixel that is not similar) is grown by that many pixels first, so a line with a small break
 * still holds the fill in; the returned mask is then grown back by the same amount so the fill
 * tucks under the line instead of stopping short of it.
 */
object FloodFill {

    fun mask(
        pixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        tolerance: Int,
        closeGap: Int,
    ): BooleanArray {
        require(pixels.size == width * height)
        val empty = BooleanArray(width * height)
        if (seedX !in 0 until width || seedY !in 0 until height) return empty
        val seed = pixels[seedY * width + seedX]
        val open = BooleanArray(width * height) { similar(pixels[it], seed, tolerance) }
        if (closeGap > 0) {
            val grown = dilate(invert(open), width, height, closeGap)
            val narrowed = invert(grown)
            if (narrowed[seedY * width + seedX]) {
                val filled = fill(narrowed, width, height, seedX, seedY)
                val back = dilate(filled, width, height, closeGap)
                // Grow back only into pixels the fill could have reached anyway.
                for (i in back.indices) back[i] = back[i] && open[i]
                return back
            }
        }
        return fill(open, width, height, seedX, seedY)
    }

    /**
     * Paints [argb] into every masked pixel, and behind the anti-aliased rim one pixel outside
     * the mask, so the fill meets a soft line without a pale seam.
     */
    fun apply(pixels: IntArray, width: Int, height: Int, mask: BooleanArray, argb: Int) {
        val rim = dilate(mask, width, height, 1)
        for (i in pixels.indices) {
            if (mask[i]) pixels[i] = argb else if (rim[i]) pixels[i] = over(pixels[i], argb)
        }
    }

    /** Non-premultiplied source-over: [top] composited over [bottom]. */
    fun over(top: Int, bottom: Int): Int {
        val ta = (top ushr 24) / 255f
        val ba = (bottom ushr 24) / 255f
        val oa = ta + ba * (1f - ta)
        if (oa <= 0f) return 0
        fun ch(shift: Int): Int {
            val t = (top ushr shift) and 0xFF
            val b = (bottom ushr shift) and 0xFF
            return ((t * ta + b * ba * (1f - ta)) / oa + 0.5f).toInt().coerceIn(0, 255)
        }
        return ((oa * 255f + 0.5f).toInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun similar(a: Int, b: Int, tolerance: Int): Boolean {
        if (a == b) return true
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val d = ((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)
            if (d > tolerance || -d > tolerance) return false
        }
        return true
    }

    private fun invert(a: BooleanArray) = BooleanArray(a.size) { !a[it] }

    private fun fill(open: BooleanArray, width: Int, height: Int, sx: Int, sy: Int): BooleanArray {
        val out = BooleanArray(open.size)
        val stack = IntArrayStack()
        stack.push(sy * width + sx)
        while (stack.isNotEmpty()) {
            val p = stack.pop()
            val y = p / width
            var x = p % width
            if (out[p] || !open[p]) continue
            while (x > 0 && open[y * width + x - 1] && !out[y * width + x - 1]) x--
            var spanAbove = false
            var spanBelow = false
            while (x < width && open[y * width + x] && !out[y * width + x]) {
                val i = y * width + x
                out[i] = true
                if (y > 0) {
                    val up = i - width
                    val can = open[up] && !out[up]
                    if (can && !spanAbove) stack.push(up)
                    spanAbove = can
                }
                if (y < height - 1) {
                    val down = i + width
                    val can = open[down] && !out[down]
                    if (can && !spanBelow) stack.push(down)
                    spanBelow = can
                }
                x++
            }
        }
        return out
    }

    /** Square dilation done as two separable passes, so it costs O(n) per pass whatever the radius. */
    fun dilate(src: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        if (radius <= 0) return src.copyOf()
        val horiz = BooleanArray(src.size)
        for (y in 0 until height) {
            val row = y * width
            var last = -radius - 1
            for (x in 0 until width) {
                if (src[row + x]) last = x
                if (x - last <= radius) horiz[row + x] = true
            }
            last = width + radius + 1
            for (x in width - 1 downTo 0) {
                if (src[row + x]) last = x
                if (last - x <= radius) horiz[row + x] = true
            }
        }
        val out = BooleanArray(src.size)
        for (x in 0 until width) {
            var last = -radius - 1
            for (y in 0 until height) {
                if (horiz[y * width + x]) last = y
                if (y - last <= radius) out[y * width + x] = true
            }
            last = height + radius + 1
            for (y in height - 1 downTo 0) {
                if (horiz[y * width + x]) last = y
                if (last - y <= radius) out[y * width + x] = true
            }
        }
        return out
    }

    private class IntArrayStack {
        private var data = IntArray(1024)
        private var size = 0
        fun push(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun pop(): Int = data[--size]
        fun isNotEmpty() = size > 0
    }
}
