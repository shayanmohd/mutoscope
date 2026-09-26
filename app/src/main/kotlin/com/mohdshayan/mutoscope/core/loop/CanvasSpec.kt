package com.mohdshayan.mutoscope.core.loop

/** The four canvas shapes. Cels are stored at 1080 px on the long edge. */
enum class Aspect(val width: Int, val height: Int) {
    SQUARE(1080, 1080),
    PORTRAIT_4_5(864, 1080),
    LANDSCAPE_16_9(1080, 608),
    VERTICAL_9_16(608, 1080),
    ;

    companion object {
        fun parse(name: String): Aspect = entries.firstOrNull { it.name == name } ?: SQUARE
    }
}

object ExportMath {

    /** Output size with the long edge at [longEdge], both sides rounded to an even number. */
    fun outputSize(width: Int, height: Int, longEdge: Int): Pair<Int, Int> {
        val scale = longEdge.toDouble() / maxOf(width, height)
        return Pair(even(width * scale), even(height * scale))
    }

    /** The same size snapped down to multiples of 16, for encoders that refuse odd macroblocks. */
    fun aligned16(width: Int, height: Int): Pair<Int, Int> =
        Pair((width / 16 * 16).coerceAtLeast(16), (height / 16 * 16).coerceAtLeast(16))

    /** 2, 4 or 8 Mbps by size. */
    fun bitrate(longEdge: Int): Int = when {
        longEdge <= 512 -> 2_000_000
        longEdge <= 720 -> 4_000_000
        else -> 8_000_000
    }

    fun frameTimeUs(tick: Long, fps: Int): Long = tick * 1_000_000L / fps

    /** Frames for a GIF of one full cycle, or for an MP4 of [seconds]. */
    fun framesForSeconds(seconds: Int, fps: Int): Int = seconds * fps

    private fun even(v: Double): Int {
        val r = Math.round(v).toInt()
        return (if (r % 2 == 0) r else r - 1).coerceAtLeast(2)
    }
}
