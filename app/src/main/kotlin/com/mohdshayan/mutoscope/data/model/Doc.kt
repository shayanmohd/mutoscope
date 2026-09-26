package com.mohdshayan.mutoscope.data.model

import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.data.db.ProjectEntity
import java.util.concurrent.atomic.AtomicLong

enum class ReelKind { DRAWN, REFERENCE }

/** A cel points at an immutable PNG in the project's cels folder, or at nothing for a blank. */
data class CelDoc(val id: Long, val file: String?)

data class ReelDoc(
    val id: Long,
    val name: String,
    val hold: Int = 1,
    val phaseOffset: Int = 0,
    val opacity: Float = 1f,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val kind: ReelKind = ReelKind.DRAWN,
    val includeInExport: Boolean = true,
    val cels: List<CelDoc>,
) {
    val length: Int get() = cels.size
    val isReference: Boolean get() = kind == ReelKind.REFERENCE

    fun celIndexAt(tick: Long): Int = LoopClock.celIndex(tick, hold, phaseOffset, length)
    fun celAt(tick: Long): CelDoc = cels[celIndexAt(tick)]
}

/** A project in memory. [reels] are in z order: index 0 is drawn first, at the bottom. */
data class ProjectDoc(val project: ProjectEntity, val reels: List<ReelDoc>) {
    val drawnReels: List<ReelDoc> get() = reels.filter { !it.isReference }
    val reference: ReelDoc? get() = reels.firstOrNull { it.isReference }

    /** The realignment cycle of every visible drawn reel. */
    val cycle: Long get() = cycleOf(reels)

    companion object {
        fun cycleOf(reels: List<ReelDoc>): Long =
            LoopClock.cycle(reels.filter { !it.hidden && !it.isReference }.map { LoopClock.period(it.hold, it.length) })
    }
}

/** Client-side ids for reels and cels, so snapshots can be written back without a round trip. */
object Ids {
    private val last = AtomicLong(0)

    /** Never hands out an id at or below [floor], so ids stay unique after the clock is set back. */
    fun seed(floor: Long) {
        last.accumulateAndGet(floor) { a, b -> maxOf(a, b) }
    }

    fun next(): Long {
        while (true) {
            val prev = last.get()
            val candidate = maxOf(prev + 1, System.currentTimeMillis() * 1000)
            if (last.compareAndSet(prev, candidate)) return candidate
        }
    }
}
