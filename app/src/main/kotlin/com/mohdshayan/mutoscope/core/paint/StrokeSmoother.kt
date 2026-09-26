package com.mohdshayan.mutoscope.core.paint

import kotlin.math.hypot

/** One resampled point on a stroke. [speed] is in canvas pixels per millisecond. */
data class StrokePoint(val x: Float, val y: Float, val speed: Float)

/**
 * Turns raw touch samples into an evenly spaced, smoothed line.
 *
 * Streamline (0 to 100) pulls a lagging point toward each sample: at 0 the line follows the
 * finger exactly, at 100 it trails far behind and irons out wobble. The lagged points are then
 * joined with a Catmull-Rom spline and resampled every [spacing] pixels, so brush stamps land at
 * an even pitch whatever the touch rate.
 */
class StrokeSmoother(streamline: Int, private val spacing: Float) {

    private val follow: Float = 1f - (streamline.coerceIn(0, 100) / 100f) * 0.85f
    private val control = ArrayList<FloatArray>() // x, y, time
    private var leftover = 0f
    private var lastEmitted: StrokePoint? = null
    private var lagX = 0f
    private var lagY = 0f
    private var lastSpeed = 0f
    private var reached = 0

    init {
        require(spacing > 0f)
    }

    fun begin(x: Float, y: Float, timeMs: Long): List<StrokePoint> {
        control.clear()
        leftover = 0f
        lagX = x
        lagY = y
        lastSpeed = 0f
        reached = 0
        control.add(floatArrayOf(x, y, timeMs.toFloat()))
        val first = StrokePoint(x, y, 0f)
        lastEmitted = first
        return listOf(first)
    }

    fun add(x: Float, y: Float, timeMs: Long): List<StrokePoint> {
        lagX += (x - lagX) * follow
        lagY += (y - lagY) * follow
        val prev = control.last()
        if (hypot(lagX - prev[0], lagY - prev[1]) < 0.5f) return emptyList()
        control.add(floatArrayOf(lagX, lagY, timeMs.toFloat()))
        if (control.size < 3) return emptyList()
        // With p0..p3 known, the segment p1 to p2 is final. The first segment uses p0 doubled.
        val i = control.size - 3
        reached = i + 1
        return segmentAt(i)
    }

    /** Finishes the line at the finger's last position, not at the lagging point. */
    fun end(x: Float, y: Float, timeMs: Long): List<StrokePoint> {
        val prev = control.last()
        if (hypot(x - prev[0], y - prev[1]) >= 0.5f) control.add(floatArrayOf(x, y, timeMs.toFloat()))
        val out = ArrayList<StrokePoint>()
        for (i in reached until control.size - 1) out += segmentAt(i)
        reached = control.size - 1
        return out
    }

    /** The spline segment from control point i to i + 1, with clamped neighbours. */
    private fun segmentAt(i: Int): List<StrokePoint> {
        val last = control.size - 1
        return segment(
            control[(i - 1).coerceAtLeast(0)],
            control[i],
            control[(i + 1).coerceAtMost(last)],
            control[(i + 2).coerceAtMost(last)],
        )
    }

    private fun segment(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray): List<StrokePoint> {
        val length = hypot(p2[0] - p1[0], p2[1] - p1[1])
        if (length <= 0f) return emptyList()
        val dt = (p2[2] - p1[2]).coerceAtLeast(1f)
        val speed = length / dt
        lastSpeed = lastSpeed * 0.6f + speed * 0.4f
        val out = ArrayList<StrokePoint>()
        // Sample the curve finely, then walk it emitting a point every `spacing` pixels.
        val steps = (length / 0.75f).toInt().coerceIn(4, 400)
        var px = p1[0]
        var py = p1[1]
        var dist = leftover
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val cx = catmull(p0[0], p1[0], p2[0], p3[0], t)
            val cy = catmull(p0[1], p1[1], p2[1], p3[1], t)
            var seg = hypot(cx - px, cy - py)
            var sx = px
            var sy = py
            while (dist + seg >= spacing) {
                val need = spacing - dist
                val f = need / seg
                sx += (cx - sx) * f
                sy += (cy - sy) * f
                seg -= need
                dist = 0f
                val p = StrokePoint(sx, sy, lastSpeed)
                out += p
                lastEmitted = p
            }
            dist += seg
            px = cx
            py = cy
        }
        leftover = dist
        return out
    }

    private fun catmull(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
    }
}
