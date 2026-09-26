package com.mohdshayan.mutoscope.ui.components

import com.mohdshayan.mutoscope.core.loop.LoopClock

/** "Cycle: 12 frames", or "Cycle: over 600 frames" past the cap. */
fun cycleLabel(cycle: Long): String = when {
    LoopClock.isOverCap(cycle) -> "Cycle: over ${LoopClock.CYCLE_CAP} frames"
    cycle == 1L -> "Cycle: 1 frame"
    else -> "Cycle: $cycle frames"
}

fun plural(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"
