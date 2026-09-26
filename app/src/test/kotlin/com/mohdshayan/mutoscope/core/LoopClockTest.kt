package com.mohdshayan.mutoscope.core

import com.mohdshayan.mutoscope.core.loop.LoopClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopClockTest {

    @Test
    fun fourOverTwelveRealignsAtTwelve() {
        val cycle = LoopClock.cycle(listOf(LoopClock.period(1, 4), LoopClock.period(1, 12)))
        assertEquals(12L, cycle)
    }

    @Test
    fun fiveOverTwelveRealignsAtSixty() {
        assertEquals(60L, LoopClock.cycle(listOf(5L, 12L)))
    }

    @Test
    fun threeFramesOnTwosOverAnEightFrameWalkIsTwentyFour() {
        // Flow 2 in the blueprint: a 3-frame reel on twos over an 8-frame walk.
        val cycle = LoopClock.cycle(listOf(LoopClock.period(2, 3), LoopClock.period(1, 8)))
        assertEquals(24L, cycle)
    }

    @Test
    fun holdRepeatsEachCelAndOffsetShiftsThePhase() {
        val onTwos = (0L until 8L).map { LoopClock.celIndex(it, hold = 2, offset = 0, length = 3) }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 0, 0), onTwos)
        val shifted = (0L until 4L).map { LoopClock.celIndex(it, hold = 1, offset = 3, length = 4) }
        assertEquals(listOf(3, 0, 1, 2), shifted)
        val negative = LoopClock.celIndex(0, hold = 1, offset = -1, length = 4)
        assertEquals(3, negative)
    }

    @Test
    fun firstTickOfInvertsCelIndex() {
        for (cel in 0 until 5) {
            val t = LoopClock.firstTickOf(cel, hold = 3, offset = 2, length = 5)
            assertEquals(cel, LoopClock.celIndex(t, hold = 3, offset = 2, length = 5))
            assertTrue(t in 0 until 15)
        }
    }

    @Test
    fun manyCoprimeReelsSaturateInsteadOfOverflowing() {
        // Primes up to 240 on fours: the true LCM is far past Long.MAX_VALUE.
        val primes = (2..240).filter { n -> (2 until n).none { n % it == 0 } }
        val cycle = LoopClock.cycle(primes.map { LoopClock.period(4, it) })
        assertEquals(LoopClock.CYCLE_OVERFLOW, cycle)
        assertTrue(LoopClock.isOverCap(cycle))
    }

    @Test
    fun capBoundaryIsInclusive() {
        assertFalse(LoopClock.isOverCap(600))
        assertTrue(LoopClock.isOverCap(601))
        assertEquals(1L, LoopClock.cycle(emptyList()))
    }
}
