package com.mohdshayan.mutoscope.core.video

/**
 * ARGB to BT.601 limited-range YUV 4:2:0, the layout every AVC encoder accepts. Chroma is the
 * average of each 2 by 2 block. Width and height must be even, which export guarantees.
 */
object Yuv {

    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return clamp(((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
    }

    fun chromaU(r: Int, g: Int, b: Int): Int = clamp(((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)

    fun chromaV(r: Int, g: Int, b: Int): Int = clamp(((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)

    /** Planar I420: the Y plane, then U, then V, each tightly packed. */
    fun toI420(argb: IntArray, width: Int, height: Int): ByteArray {
        require(width % 2 == 0 && height % 2 == 0) { "YUV 4:2:0 needs even dimensions" }
        require(argb.size == width * height)
        val ySize = width * height
        val cSize = ySize / 4
        val out = ByteArray(ySize + 2 * cSize)
        for (i in 0 until ySize) out[i] = luma(argb[i]).toByte()
        val cw = width / 2
        for (cy in 0 until height / 2) {
            for (cx in 0 until cw) {
                val i0 = (cy * 2) * width + cx * 2
                val p0 = argb[i0]
                val p1 = argb[i0 + 1]
                val p2 = argb[i0 + width]
                val p3 = argb[i0 + width + 1]
                val r = (((p0 shr 16) and 0xFF) + ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) + ((p3 shr 16) and 0xFF) + 2) / 4
                val g = (((p0 shr 8) and 0xFF) + ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) + ((p3 shr 8) and 0xFF) + 2) / 4
                val b = ((p0 and 0xFF) + (p1 and 0xFF) + (p2 and 0xFF) + (p3 and 0xFF) + 2) / 4
                val ci = cy * cw + cx
                out[ySize + ci] = chromaU(r, g, b).toByte()
                out[ySize + cSize + ci] = chromaV(r, g, b).toByte()
            }
        }
        return out
    }

    /** Semi-planar NV12: the Y plane, then interleaved U and V. */
    fun toNv12(argb: IntArray, width: Int, height: Int): ByteArray {
        val i420 = toI420(argb, width, height)
        val ySize = width * height
        val cSize = ySize / 4
        val out = ByteArray(i420.size)
        System.arraycopy(i420, 0, out, 0, ySize)
        for (i in 0 until cSize) {
            out[ySize + 2 * i] = i420[ySize + i]
            out[ySize + 2 * i + 1] = i420[ySize + cSize + i]
        }
        return out
    }

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v
}
