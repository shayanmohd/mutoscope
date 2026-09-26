package com.mohdshayan.mutoscope.core.gif

/**
 * GIF frame delays are whole centiseconds. At 30 fps a frame lasts 3.33 cs, so a flat 3 cs would
 * run the loop ten percent fast. Each delay here is the difference between two rounded-down
 * timestamps, so the remainder carries forward and the total matches the true running time.
 */
object DelaySchedule {

    fun centiseconds(frameCount: Int, fps: Int): IntArray {
        require(fps in 1..100) { "fps out of range" }
        require(frameCount >= 0) { "frame count cannot be negative" }
        return IntArray(frameCount) { i ->
            val start = (i.toLong() * 100) / fps
            val end = ((i + 1).toLong() * 100) / fps
            (end - start).toInt()
        }
    }
}
