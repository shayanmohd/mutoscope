package com.mohdshayan.mutoscope.core.loop

/**
 * The reel model in numbers. At tick t a reel of `length` cels, held for `hold` ticks each and
 * shifted by `offset` cels, shows cel ((t / hold) + offset) mod length. Every reel repeats every
 * hold x length ticks, and the whole stack realigns at the least common multiple of those periods.
 */
object LoopClock {

    /** Past this many frames the cycle label stops counting and GIF export offers seconds instead. */
    const val CYCLE_CAP = 600L

    /** Returned by [cycle] when the true least common multiple does not fit in a Long. */
    const val CYCLE_OVERFLOW = Long.MAX_VALUE

    fun celIndex(tick: Long, hold: Int, offset: Int, length: Int): Int {
        require(length > 0) { "a reel has at least one cel" }
        require(hold > 0) { "hold is at least one tick" }
        val step = Math.floorDiv(tick, hold.toLong())
        return Math.floorMod(step + offset, length.toLong()).toInt()
    }

    fun period(hold: Int, length: Int): Long = hold.toLong() * length.toLong()

    /** The first tick at or after zero on which [cel] shows. */
    fun firstTickOf(cel: Int, hold: Int, offset: Int, length: Int): Long =
        Math.floorMod((cel - offset).toLong(), length.toLong()) * hold

    fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    /** Least common multiple, saturating at [CYCLE_OVERFLOW] instead of wrapping. */
    fun lcm(a: Long, b: Long): Long {
        if (a == CYCLE_OVERFLOW || b == CYCLE_OVERFLOW) return CYCLE_OVERFLOW
        val g = gcd(a, b)
        return try {
            Math.multiplyExact(a / g, b)
        } catch (_: ArithmeticException) {
            CYCLE_OVERFLOW
        }
    }

    /** Ticks until every period realigns. An empty stack is a one-frame cycle. */
    fun cycle(periods: List<Long>): Long =
        periods.filter { it > 0 }.fold(1L) { acc, p -> lcm(acc, p) }

    fun isOverCap(cycle: Long): Boolean = cycle > CYCLE_CAP
}
