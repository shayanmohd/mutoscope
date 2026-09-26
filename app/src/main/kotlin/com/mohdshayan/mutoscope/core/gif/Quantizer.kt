package com.mohdshayan.mutoscope.core.gif

/**
 * Octree colour quantizer. Every frame of a loop feeds one tree, so the whole GIF shares a
 * single palette of at most 256 colours and nothing flickers from frame to frame. Alpha is
 * ignored: frames are composited over paper before they get here.
 */
class Quantizer(private val maxColors: Int = 256) {

    private class Node(val level: Int) {
        var isLeaf = level == MAX_DEPTH
        var pixelCount = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        val children = arrayOfNulls<Node>(8)
        var nextReducible: Node? = null
    }

    private val reducible = arrayOfNulls<Node>(MAX_DEPTH)
    private var root = Node(0)
    private var leafCount = 0

    init {
        require(maxColors in 8..256) { "an octree reduces to at least 8 colours" }
    }

    fun add(argb: Int) {
        var node = root
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        var level = 0
        while (!node.isLeaf) {
            val shift = 7 - level
            val idx = (((r shr shift) and 1) shl 2) or (((g shr shift) and 1) shl 1) or ((b shr shift) and 1)
            var child = node.children[idx]
            if (child == null) {
                child = Node(level + 1)
                node.children[idx] = child
                if (child.isLeaf) {
                    leafCount++
                } else {
                    child.nextReducible = reducible[level + 1]
                    reducible[level + 1] = child
                }
            }
            node = child
            level++
        }
        node.pixelCount++
        node.r += r
        node.g += g
        node.b += b
        while (leafCount > maxColors) reduce()
    }

    fun addAll(pixels: IntArray, step: Int = 1) {
        var i = 0
        while (i < pixels.size) {
            add(pixels[i])
            i += step
        }
    }

    private fun reduce() {
        var level = MAX_DEPTH - 1
        while (level > 0 && reducible[level] == null) level--
        val node = reducible[level] ?: return
        reducible[level] = node.nextReducible
        var removed = 0
        for (i in 0 until 8) {
            val c = node.children[i] ?: continue
            node.r += c.r
            node.g += c.g
            node.b += c.b
            node.pixelCount += c.pixelCount
            node.children[i] = null
            removed++
        }
        node.isLeaf = true
        leafCount -= removed - 1
    }

    /** The palette as opaque ARGB, at most [maxColors] entries. */
    fun palette(): IntArray {
        val out = ArrayList<Int>()
        collect(root, out)
        if (out.isEmpty()) out.add(0xFF000000.toInt())
        return out.toIntArray()
    }

    private fun collect(node: Node, out: MutableList<Int>) {
        if (node.isLeaf) {
            if (node.pixelCount > 0) {
                val n = node.pixelCount
                out.add(
                    (0xFF shl 24) or
                        ((node.r / n).toInt() shl 16) or
                        ((node.g / n).toInt() shl 8) or
                        (node.b / n).toInt(),
                )
            }
            return
        }
        for (c in node.children) if (c != null) collect(c, out)
    }

    companion object {
        private const val MAX_DEPTH = 8
    }
}

/**
 * Maps any colour to its nearest palette entry through a 15-bit lookup table filled on demand,
 * so a 1080 px frame costs one array read per pixel after the first few thousand misses.
 */
class PaletteMapper(private val palette: IntArray) {

    private val lut = IntArray(1 shl 15) { -1 }

    fun index(argb: Int): Int {
        val key = (((argb shr 19) and 0x1F) shl 10) or (((argb shr 11) and 0x1F) shl 5) or ((argb shr 3) and 0x1F)
        val cached = lut[key]
        if (cached >= 0) return cached
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val p = palette[i]
            val dr = ((p shr 16) and 0xFF) - r
            val dg = ((p shr 8) and 0xFF) - g
            val db = (p and 0xFF) - b
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        lut[key] = best
        return best
    }

    fun map(pixels: IntArray, out: ByteArray) {
        for (i in pixels.indices) out[i] = index(pixels[i]).toByte()
    }
}
