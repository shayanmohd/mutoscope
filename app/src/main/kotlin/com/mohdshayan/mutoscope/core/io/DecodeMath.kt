package com.mohdshayan.mutoscope.core.io

/**
 * Size arithmetic for the photo import path, kept apart from BitmapFactory so the numbers that
 * decide whether a decode can run out of memory are tested on the JVM.
 */
object DecodeMath {

    const val MAX_PIXELS = 50_000_000L

    fun isTooLarge(width: Int, height: Int): Boolean = width.toLong() * height.toLong() > MAX_PIXELS

    /**
     * Largest power-of-two sample size that still leaves the decoded image at least as large as
     * the target box on both axes, so the later exact scale only ever shrinks.
     */
    fun sampleSize(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        require(srcWidth > 0 && srcHeight > 0 && targetWidth > 0 && targetHeight > 0)
        val fit = fitInside(srcWidth, srcHeight, targetWidth, targetHeight)
        var sample = 1
        while (srcWidth / (sample * 2) >= fit.first && srcHeight / (sample * 2) >= fit.second) sample *= 2
        return sample
    }

    /** The largest size with the source's aspect ratio that fits inside the box, never upscaled. */
    fun fitInside(srcWidth: Int, srcHeight: Int, boxWidth: Int, boxHeight: Int): Pair<Int, Int> {
        val scale = minOf(boxWidth.toDouble() / srcWidth, boxHeight.toDouble() / srcHeight, 1.0)
        return Pair(
            (srcWidth * scale).toInt().coerceAtLeast(1),
            (srcHeight * scale).toInt().coerceAtLeast(1),
        )
    }

    /** Width and height as displayed once an EXIF rotation is applied. */
    fun orientedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 != 0) Pair(height, width) else Pair(width, height)
}
