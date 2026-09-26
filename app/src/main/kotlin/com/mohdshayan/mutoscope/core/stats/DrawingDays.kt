package com.mohdshayan.mutoscope.core.stats

/**
 * The opt-in "drawing days in the last 30" count. Days are epoch days (LocalDate.toEpochDay in the
 * phone's own zone), so a DST change never splits or merges a day. A stored day after today (the
 * clock was set back, or the phone flew west over midnight) is outside the window and does not
 * count, instead of counting forever.
 */
object DrawingDays {

    const val WINDOW = 30

    /** The stored list after drawing on [today]: sorted, no repeats, only days inside the window. */
    fun record(days: List<Long>, today: Long): List<Long> =
        (days + today).filter { inWindow(it, today) }.distinct().sorted()

    fun countRecent(days: List<Long>, today: Long): Int = days.distinct().count { inWindow(it, today) }

    private fun inWindow(day: Long, today: Long): Boolean = today - day in 0 until WINDOW
}
