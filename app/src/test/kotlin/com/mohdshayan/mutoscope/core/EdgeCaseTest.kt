package com.mohdshayan.mutoscope.core

import com.mohdshayan.mutoscope.core.archive.ManifestException
import com.mohdshayan.mutoscope.core.archive.ProjectManifest
import com.mohdshayan.mutoscope.core.archive.ReelManifest
import com.mohdshayan.mutoscope.core.io.DecodeMath
import com.mohdshayan.mutoscope.core.loop.Aspect
import com.mohdshayan.mutoscope.core.loop.ExportMath
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.core.paint.FloodFill
import com.mohdshayan.mutoscope.core.paint.GrainNoise
import com.mohdshayan.mutoscope.core.paint.StrokeSmoother
import com.mohdshayan.mutoscope.core.sample.SampleScripts
import com.mohdshayan.mutoscope.core.stats.DrawingDays
import com.mohdshayan.mutoscope.core.undo.UndoModel
import com.mohdshayan.mutoscope.core.video.Yuv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zero, negative, huge, boundary and damaged inputs for the pure logic. */
class EdgeCaseTest {

    // ---- LoopClock ------------------------------------------------------------------------

    @Test
    fun negativeTicksAndOffsetsStillLandInsideTheReel() {
        assertEquals(3, LoopClock.celIndex(-1, 1, 0, 4))
        assertEquals(3, LoopClock.celIndex(0, 1, -1, 4))
        assertEquals(1, LoopClock.celIndex(-1, 2, 0, 2)) // tick -1 on twos is the second half of cel 2
        assertEquals(Math.floorMod(Int.MAX_VALUE.toLong(), 7L).toInt(), LoopClock.celIndex(0, 1, Int.MAX_VALUE, 7))
        assertEquals(Math.floorMod(Int.MIN_VALUE.toLong(), 7L).toInt(), LoopClock.celIndex(0, 1, Int.MIN_VALUE, 7))
        val far = LoopClock.celIndex(Long.MAX_VALUE, 3, 0, 240)
        assertTrue(far in 0 until 240)
        assertEquals(0, LoopClock.celIndex(123_456, 4, 99, 1))
    }

    @Test
    fun firstTickOfHandlesNegativeAndOversizedOffsets() {
        for (offset in listOf(-13, -1, 0, 5, 1000)) {
            for (cel in 0 until 5) {
                val t = LoopClock.firstTickOf(cel, 3, offset, 5)
                assertTrue(t >= 0)
                assertEquals(cel, LoopClock.celIndex(t, 3, offset, 5))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyReelIsRefused() {
        LoopClock.celIndex(0, 1, 0, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aZeroHoldIsRefused() {
        LoopClock.celIndex(0, 0, 0, 4)
    }

    @Test
    fun cycleOfNothingIsOneFrameAndIgnoresNonPositivePeriods() {
        assertEquals(1L, LoopClock.cycle(emptyList()))
        assertEquals(1L, LoopClock.cycle(listOf(0L, -4L)))
        assertEquals(12L, LoopClock.cycle(listOf(0L, 12L, 4L)))
        assertEquals(1L, LoopClock.cycle(listOf(1L, 1L)))
    }

    @Test
    fun overflowIsStickyAndTheCapBoundaryIsExact() {
        assertEquals(LoopClock.CYCLE_OVERFLOW, LoopClock.lcm(LoopClock.CYCLE_OVERFLOW, 2))
        assertEquals(LoopClock.CYCLE_OVERFLOW, LoopClock.lcm(3, LoopClock.CYCLE_OVERFLOW))
        assertFalse(LoopClock.isOverCap(600))
        assertTrue(LoopClock.isOverCap(601))
        // The longest single reel: 240 frames on fours.
        assertEquals(960L, LoopClock.period(4, 240))
        assertTrue(LoopClock.isOverCap(LoopClock.cycle(listOf(LoopClock.period(4, 240)))))
        // Large coprime periods near the reel cap never wrap negative.
        val big = listOf(239L, 240L, 241L, 233L, 229L, 227L, 223L, 211L, 199L)
        val c = LoopClock.cycle(big.map { it * 4 })
        assertTrue(c > 0)
    }

    // ---- ExportMath and Aspect --------------------------------------------------------------

    @Test
    fun outputSizesAreEvenForEveryShapeAndSize() {
        for (a in Aspect.entries) {
            for (edge in listOf(512, 720, 1080)) {
                val (w, h) = ExportMath.outputSize(a.width, a.height, edge)
                assertEquals(0, w % 2)
                assertEquals(0, h % 2)
                assertEquals(edge, maxOf(w, h))
                assertTrue(minOf(w, h) >= 2)
            }
        }
        assertEquals(Pair(720, 404), ExportMath.outputSize(1080, 608, 720))
        assertEquals(Pair(16, 16), ExportMath.aligned16(8, 2))
        assertEquals(Pair(400, 720), ExportMath.aligned16(404, 720))
    }

    @Test
    fun bitrateAndTimestampBoundaries() {
        assertEquals(2_000_000, ExportMath.bitrate(512))
        assertEquals(4_000_000, ExportMath.bitrate(513))
        assertEquals(4_000_000, ExportMath.bitrate(720))
        assertEquals(8_000_000, ExportMath.bitrate(721))
        assertEquals(0L, ExportMath.frameTimeUs(0, 12))
        assertEquals(1_000_000L, ExportMath.frameTimeUs(12, 12))
        assertEquals(33_333L, ExportMath.frameTimeUs(1, 30))
        assertEquals(15 * 30, ExportMath.framesForSeconds(15, 30))
        assertEquals(0, ExportMath.framesForSeconds(0, 12))
    }

    @Test
    fun unknownShapeNamesFallBackToSquare() {
        assertEquals(Aspect.SQUARE, Aspect.parse(""))
        assertEquals(Aspect.SQUARE, Aspect.parse("square"))
        assertEquals(Aspect.VERTICAL_9_16, Aspect.parse("VERTICAL_9_16"))
    }

    // ---- DecodeMath ---------------------------------------------------------------------------

    @Test
    fun theFiftyMegapixelLineIsExactAndCannotOverflow() {
        assertFalse(DecodeMath.isTooLarge(10_000, 5_000))
        assertTrue(DecodeMath.isTooLarge(10_000, 5_001))
        assertTrue(DecodeMath.isTooLarge(65_536, 65_536))
        assertTrue(DecodeMath.isTooLarge(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(DecodeMath.isTooLarge(0, 0))
    }

    @Test
    fun sampleSizeNeverUpscalesAndNeverDropsBelowTheBox() {
        assertEquals(1, DecodeMath.sampleSize(800, 600, 1080, 1080))
        assertEquals(1, DecodeMath.sampleSize(1, 1, 1080, 1080))
        for ((w, h) in listOf(12_000 to 9_000, 9_000 to 12_000, 4_032 to 3_024, 1_081 to 1_081)) {
            val s = DecodeMath.sampleSize(w, h, 1080, 1080)
            val fit = DecodeMath.fitInside(w, h, 1080, 1080)
            assertTrue(w / s >= fit.first && h / s >= fit.second)
            assertTrue(Integer.bitCount(s) == 1)
        }
        assertEquals(Pair(1, 1), DecodeMath.fitInside(1, 1, 1080, 1080))
        assertEquals(Pair(1080, 1), DecodeMath.fitInside(20_000, 10, 1080, 1080))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleSizeRefusesAnEmptyImage() {
        DecodeMath.sampleSize(0, 600, 1080, 1080)
    }

    @Test
    fun rotationSwapsOnlyForQuarterTurns() {
        assertEquals(Pair(3000, 4000), DecodeMath.orientedSize(4000, 3000, 90))
        assertEquals(Pair(3000, 4000), DecodeMath.orientedSize(4000, 3000, 270))
        assertEquals(Pair(3000, 4000), DecodeMath.orientedSize(4000, 3000, -90))
        assertEquals(Pair(4000, 3000), DecodeMath.orientedSize(4000, 3000, 180))
        assertEquals(Pair(4000, 3000), DecodeMath.orientedSize(4000, 3000, 360))
    }

    // ---- FloodFill ------------------------------------------------------------------------------

    @Test
    fun fillOutsideTheCanvasDoesNothingAndATinyCanvasFills() {
        val px = IntArray(16)
        assertTrue(FloodFill.mask(px, 4, 4, -1, 0, 0, 2).none { it })
        assertTrue(FloodFill.mask(px, 4, 4, 4, 0, 0, 2).none { it })
        assertTrue(FloodFill.mask(px, 4, 4, 0, 4, 0, 2).none { it })
        assertTrue(FloodFill.mask(IntArray(1), 1, 1, 0, 0, 0, 2).all { it })
        assertTrue(FloodFill.mask(IntArray(0), 0, 0, 0, 0, 0, 0).isEmpty())
    }

    @Test
    fun anEmptyCelFillsCompletelyWithOrWithoutGapClosing() {
        val px = IntArray(40 * 30)
        assertTrue(FloodFill.mask(px, 40, 30, 20, 15, 0, 0).all { it })
        assertTrue(FloodFill.mask(px, 40, 30, 20, 15, 0, 2).all { it })
    }

    @Test
    fun maximumToleranceCrossesEveryLine() {
        val px = IntArray(10 * 10) { if (it % 10 == 5) 0xFF000000.toInt() else 0 }
        assertTrue(FloodFill.mask(px, 10, 10, 0, 0, 255, 0).all { it })
        assertFalse(FloodFill.mask(px, 10, 10, 0, 0, 0, 0)[9])
    }

    @Test
    fun aSeedRightBesideALineStillFillsItsSide() {
        // A wall at x = 10; the seed touches it, so gap closing cannot shrink the region around
        // the seed and must fall back to a plain fill instead of doing nothing.
        val w = 20
        val px = IntArray(w * w) { if (it % w == 10) 0xFF1C1D24.toInt() else 0 }
        val mask = FloodFill.mask(px, w, w, 9, 5, 0, 2)
        assertTrue(mask[5 * w + 9])
        assertTrue(mask[5 * w + 0])
        for (y in 0 until w) for (x in 10 until w) assertFalse(mask[y * w + x])
    }

    @Test
    fun alphaCountsWhenMatchingAndOverHandlesTransparency() {
        assertFalse(FloodFill.similar(0x00FF0000, 0xFFFF0000.toInt(), 10))
        assertTrue(FloodFill.similar(0xF0FF0000.toInt(), 0xFFFF0000.toInt(), 15))
        assertEquals(0, FloodFill.over(0, 0))
        assertEquals(0xFF123456.toInt(), FloodFill.over(0xFF123456.toInt(), 0xFFABCDEF.toInt()))
        assertEquals(0xFFABCDEF.toInt(), FloodFill.over(0x00123456, 0xFFABCDEF.toInt()))
        val d = FloodFill.dilate(booleanArrayOf(true, false, false), 3, 1, 0)
        assertEquals(listOf(true, false, false), d.toList())
    }

    // ---- StrokeSmoother -----------------------------------------------------------------------

    @Test
    fun aTapIsOnePointAndOutOfRangeStreamlineIsClamped() {
        val tap = StrokeSmoother(40, 2f)
        val first = tap.begin(100f, 100f, 0)
        assertEquals(1, first.size)
        assertTrue(tap.end(100f, 100f, 16).isEmpty())
        for (s in listOf(-50, 1000)) {
            val sm = StrokeSmoother(s, 2f)
            sm.begin(0f, 0f, 0)
            val pts = (1..50).flatMap { sm.add(it * 4f, 0f, it * 8L) } + sm.end(200f, 0f, 400)
            assertTrue(pts.isNotEmpty())
            assertTrue(pts.all { it.x.isFinite() && it.y.isFinite() && it.speed.isFinite() })
        }
    }

    @Test
    fun timeRunningBackwardsNeverGivesInfiniteSpeed() {
        val sm = StrokeSmoother(0, 1f)
        sm.begin(0f, 0f, 1000)
        val pts = (1..20).flatMap { sm.add(it * 5f, it * 2f, 1000L - it) } + sm.end(100f, 40f, 0)
        assertTrue(pts.isNotEmpty())
        assertTrue(pts.all { it.speed.isFinite() && it.speed >= 0f })
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSpacingIsRefused() {
        StrokeSmoother(40, 0f)
    }

    // ---- UndoModel ----------------------------------------------------------------------------

    @Test
    fun emptyHistoryAndACapacityOfOne() {
        val u = UndoModel<String>(1)
        assertNull(u.undo("now"))
        assertNull(u.redo("now"))
        u.push("a")
        u.push("b")
        assertEquals(1, u.undoDepth)
        assertEquals("b", u.undo("c"))
        assertNull(u.undo("b"))
        assertEquals("c", u.redo("b"))
        assertFalse(u.canRedo)
        assertEquals(listOf("b"), u.snapshots())
        u.clear()
        assertFalse(u.canUndo)
    }

    // ---- ProjectManifest: damaged files -------------------------------------------------------

    private fun manifest(reels: List<ReelManifest> = listOf(ReelManifest("Walk", cels = listOf("cels/0-0.png", null))), fps: Int = 12, width: Int = 1080, format: Int = 1) =
        ProjectManifest(format = format, name = "Walk", aspect = "SQUARE", widthPx = width, heightPx = 1080, fps = fps, paperArgb = -1, reels = reels)

    private fun rejects(text: String, reason: String? = null) {
        try {
            ProjectManifest.decode(text)
            fail("accepted: $text")
        } catch (e: ManifestException) {
            if (reason != null) assertTrue(e.message ?: "", (e.message ?: "").contains(reason))
        }
    }

    @Test
    fun unreadableJsonIsRefusedWithAReason() {
        rejects("", "not readable")
        rejects("null", "not readable")
        rejects("[]", "not readable")
        rejects("{", "not readable")
        rejects("\u0000\u0001PK", "not readable")
        rejects("{\"name\":\"x\"}", "not readable")
    }

    @Test
    fun outOfRangeValuesAreRefused() {
        rejects(ProjectManifest.encode(manifest(format = 2)), "newer version")
        rejects(ProjectManifest.encode(manifest(fps = 3)), "speed")
        rejects(ProjectManifest.encode(manifest(fps = 31)), "speed")
        rejects(ProjectManifest.encode(manifest(width = 15)), "canvas size")
        rejects(ProjectManifest.encode(manifest(width = 4097)), "canvas size")
        rejects(ProjectManifest.encode(manifest(reels = emptyList())), "no reels")
        rejects(ProjectManifest.encode(manifest(reels = listOf(ReelManifest("R", cels = emptyList())))), "no frames")
        rejects(ProjectManifest.encode(manifest(reels = listOf(ReelManifest("R", cels = List(241) { null })))), "more than 240")
        rejects(ProjectManifest.encode(manifest(reels = listOf(ReelManifest("R", hold = 0, cels = listOf(null))))), "hold")
        rejects(ProjectManifest.encode(manifest(reels = listOf(ReelManifest("R", hold = 5, cels = listOf(null))))), "hold")
        // The edges of every range are accepted.
        ProjectManifest.decode(ProjectManifest.encode(manifest(fps = 4, width = 16)))
        ProjectManifest.decode(ProjectManifest.encode(manifest(fps = 30, width = 4096)))
        ProjectManifest.decode(ProjectManifest.encode(manifest(reels = listOf(ReelManifest("R", hold = 4, cels = List(240) { null })))))
    }

    @Test
    fun framePathsCannotLeaveTheFile() {
        for (bad in listOf("../x.png", "/etc/passwd", "cels/../../x.png", "cels/0-0.png/../../x", "cels/0-0.PNG", "cels/a-b.png", "cels\\0-0.png", "cels/0-0.png\u0000")) {
            rejects(ProjectManifest.encode(manifest(reels = listOf(ReelManifest("R", cels = listOf(bad))))), "outside")
        }
    }

    @Test
    fun unknownKeysAreIgnoredAndMissingOnesTakeDefaults() {
        val text = """{"format":1,"name":"Café loop, 日本","aspect":"SQUARE","widthPx":1080,"heightPx":1080,"fps":12,""" +
            """"paperArgb":-1,"future":{"x":1},"reels":[{"name":"R","cels":[null],"extra":true}]}"""
        val m = ProjectManifest.decode(text)
        assertEquals("Café loop, 日本", m.name)
        val r = m.reels.single()
        assertEquals(1, r.hold)
        assertEquals(0, r.phaseOffset)
        assertEquals("DRAWN", r.kind)
        assertTrue(r.includeInExport)
        assertEquals(m, ProjectManifest.decode(ProjectManifest.encode(m)))
    }

    // ---- Yuv ------------------------------------------------------------------------------------

    @Test
    fun blackAndWhiteHitTheLimitedRangeEnds() {
        val white = Yuv.toI420(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2)
        assertEquals(235, white[0].toInt() and 0xFF)
        assertEquals(128, white[4].toInt() and 0xFF)
        assertEquals(128, white[5].toInt() and 0xFF)
        val black = Yuv.toNv12(IntArray(4) { 0xFF000000.toInt() }, 2, 2)
        assertEquals(16, black[0].toInt() and 0xFF)
        assertEquals(6, black.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oddSizesAreRefused() {
        Yuv.toI420(IntArray(3 * 2), 3, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aShortPixelBufferIsRefused() {
        Yuv.toI420(IntArray(3), 2, 2)
    }

    // ---- GrainNoise and the sample ----------------------------------------------------------

    @Test
    fun grainStaysInRangeRepeatsAndTilesCleanly() {
        val a = GrainNoise.tile(48, 7)
        assertTrue(a.all { it >= 0.35f - 1e-5f && it <= 1f + 1e-5f })
        assertTrue(a.contentEquals(GrainNoise.tile(48, 7)))
        assertFalse(a.contentEquals(GrainNoise.tile(48, 8)))
        assertEquals(0, GrainNoise.tile(0, 1).size)
        assertEquals(GrainNoise.value(0.5f, 0.5f, 3, 16), GrainNoise.value(16.5f, 32.5f, 3, 16), 1e-6f)
        assertEquals(GrainNoise.value(0.5f, 0.5f, 3, 0), GrainNoise.value(1.5f, 0.5f, 3, 0), 1e-6f)
    }

    @Test
    fun theBallIsOnTheFloorAtFrameOneAndHighestHalfwayRound() {
        assertEquals(0f, SampleScripts.ballLift(0), 1e-6f)
        assertEquals(1f, SampleScripts.ballLift(6), 1e-6f)
        for (i in 1 until 6) assertEquals(SampleScripts.ballLift(i), SampleScripts.ballLift(12 - i), 1e-6f)
        val reels = SampleScripts.lampAndBall()
        assertEquals(listOf(4, 12), reels.map { it.frames.size })
    }

    // ---- DrawingDays: time zones and DST ----------------------------------------------------

    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    @Test
    fun theWindowIsThirtyDaysEndingToday() {
        val today = day("2026-09-26")
        val days = listOf(today - 30, today - 29, today - 1)
        assertEquals(2, DrawingDays.countRecent(days, today))
        assertEquals(listOf(today - 29, today - 1, today), DrawingDays.record(days, today))
        assertEquals(listOf(today), DrawingDays.record(emptyList(), today))
        assertEquals(0, DrawingDays.countRecent(emptyList(), today))
    }

    @Test
    fun drawingTwiceOnOneDayCountsOnce() {
        val today = day("2026-09-26")
        val once = DrawingDays.record(emptyList(), today)
        assertEquals(once, DrawingDays.record(once, today))
        assertEquals(1, DrawingDays.countRecent(listOf(today, today), today))
    }

    @Test
    fun aClockSetBackDoesNotCountFutureDaysForever() {
        val today = day("2026-09-26")
        val stored = listOf(today + 3, today - 2)
        assertEquals(1, DrawingDays.countRecent(stored, today))
        assertEquals(listOf(today - 2, today), DrawingDays.record(stored, today))
    }

    @Test
    fun dstChangesNeitherSplitNorMergeADay() {
        val ny = ZoneId.of("America/New_York")
        // 8 March 2026: clocks jump from 02:00 to 03:00 in New York.
        val before = ZonedDateTime.of(LocalDateTime.parse("2026-03-08T01:30"), ny).toLocalDate().toEpochDay()
        val after = ZonedDateTime.of(LocalDateTime.parse("2026-03-08T03:30"), ny).toLocalDate().toEpochDay()
        assertEquals(before, after)
        // 1 November 2026: 01:30 happens twice; both are the same day.
        val fallA = ZonedDateTime.ofLocal(LocalDateTime.parse("2026-11-01T01:30"), ny, java.time.ZoneOffset.ofHours(-4)).toLocalDate().toEpochDay()
        val fallB = ZonedDateTime.ofLocal(LocalDateTime.parse("2026-11-01T01:30"), ny, java.time.ZoneOffset.ofHours(-5)).toLocalDate().toEpochDay()
        assertEquals(fallA, fallB)
        // The evening before the spring change and the morning after it are two drawing days.
        val eve = day("2026-03-07")
        val days = DrawingDays.record(DrawingDays.record(emptyList(), eve), after)
        assertEquals(2, DrawingDays.countRecent(days, after))
        assertEquals(1L, after - eve)
    }

    @Test
    fun oneInstantIsADifferentDayInDifferentZones() {
        val instant = ZonedDateTime.of(LocalDateTime.parse("2026-09-26T20:00"), ZoneId.of("UTC")).toInstant()
        val kolkata = instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toEpochDay()
        val losAngeles = instant.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate().toEpochDay()
        assertNotEquals(kolkata, losAngeles)
        // Drawn in Kolkata after midnight, then the phone lands in Los Angeles the same evening:
        // Kolkata's date is tomorrow there, so it waits outside the window instead of inflating it.
        val stored = DrawingDays.record(emptyList(), kolkata)
        assertEquals(0, DrawingDays.countRecent(stored, losAngeles))
        assertEquals(1, DrawingDays.countRecent(stored, kolkata))
    }
}
