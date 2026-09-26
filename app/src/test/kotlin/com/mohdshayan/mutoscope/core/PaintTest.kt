package com.mohdshayan.mutoscope.core

import com.mohdshayan.mutoscope.core.paint.FloodFill
import com.mohdshayan.mutoscope.core.paint.StrokeSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PaintTest {

    private val ink = 0xFF1C1D24.toInt()

    /** A 40 x 40 transparent canvas with a square outline from 10 to 29, with a gap of [gap] px. */
    private fun squareWithGap(gap: Int): IntArray {
        val w = 40
        val px = IntArray(w * w)
        for (i in 10..29) {
            px[10 * w + i] = ink
            px[29 * w + i] = ink
            px[i * w + 10] = ink
            if (i !in 18 until 18 + gap) px[i * w + 29] = ink
        }
        return px
    }

    @Test
    fun fillStaysInsideAClosedShape() {
        val m = FloodFill.mask(squareWithGap(0), 40, 40, 20, 20, tolerance = 16, closeGap = 0)
        assertTrue(m[20 * 40 + 20])
        assertFalse(m[5 * 40 + 5])
        assertEquals(18 * 18, m.count { it })
    }

    @Test
    fun smallGapLeaksWithoutClosingAndHoldsWithIt() {
        val px = squareWithGap(3)
        val leaky = FloodFill.mask(px, 40, 40, 20, 20, tolerance = 16, closeGap = 0)
        assertTrue("the fill escapes through the gap", leaky[2 * 40 + 2])
        val closed = FloodFill.mask(px, 40, 40, 20, 20, tolerance = 16, closeGap = 2)
        assertFalse("the grown boundary holds the fill", closed[2 * 40 + 2])
        assertTrue(closed[20 * 40 + 20])
        // Grown back, the fill reaches the inside edge of the line again.
        assertTrue(closed[11 * 40 + 11])
        assertFalse("never paints over the line itself", closed[10 * 40 + 15])
    }

    @Test
    fun applyFillsTheMaskAndSlipsUnderTheSoftEdge() {
        val w = 5
        val soft = 0x801C1D24.toInt()
        val px = intArrayOf(0, 0, soft, ink, 0) // one row: two empty, a soft edge, the line, outside
        val mask = booleanArrayOf(true, true, false, false, false)
        val red = 0xFFD9482B.toInt()
        FloodFill.apply(px, w, 1, mask, red)
        assertEquals(red, px[0])
        assertEquals(red, px[1])
        // The half-transparent edge now sits over red and is fully opaque.
        assertEquals(0xFF, px[2] ushr 24)
        assertEquals(ink, px[3])
        assertEquals(0, px[4])
        assertEquals(red, FloodFill.over(0, red))
    }

    @Test
    fun smootherEmitsEvenlySpacedPointsAndReachesTheEnd() {
        val s = StrokeSmoother(streamline = 0, spacing = 4f)
        val pts = ArrayList(s.begin(0f, 0f, 0))
        for (i in 1..50) pts += s.add(i * 6f, (i % 2) * 1f, i * 8L)
        pts += s.end(300f, 0f, 408)
        for (i in 1 until pts.size) {
            val d = hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            assertTrue("gap $d at $i", d in 3.0f..4.3f)
        }
        assertTrue("reaches within one spacing of the end", pts.last().x > 295f)
    }

    @Test
    fun streamlineIronsOutWobble() {
        fun wobble(streamline: Int): Float {
            val s = StrokeSmoother(streamline, 2f)
            val pts = ArrayList(s.begin(0f, 0f, 0))
            for (i in 1..80) pts += s.add(i * 5f, if (i % 2 == 0) 12f else -12f, i * 8L)
            pts += s.end(405f, 0f, 648)
            return pts.maxOf { kotlin.math.abs(it.y) }
        }
        assertTrue(wobble(90) < wobble(0) * 0.5f)
    }
}
