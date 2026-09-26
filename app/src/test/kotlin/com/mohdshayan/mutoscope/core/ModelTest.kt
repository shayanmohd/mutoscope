package com.mohdshayan.mutoscope.core

import com.mohdshayan.mutoscope.core.archive.ManifestException
import com.mohdshayan.mutoscope.core.archive.ProjectManifest
import com.mohdshayan.mutoscope.core.archive.ReelManifest
import com.mohdshayan.mutoscope.core.io.DecodeMath
import com.mohdshayan.mutoscope.core.loop.ExportMath
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.core.sample.SampleScripts
import com.mohdshayan.mutoscope.core.undo.UndoModel
import com.mohdshayan.mutoscope.core.video.Yuv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelTest {

    @Test
    fun undoKeepsTheLatestHundredAndRedoIsClearedByANewEdit() {
        val u = UndoModel<Int>(100)
        for (i in 0 until 150) u.push(i)
        assertEquals(100, u.undoDepth)
        var state = 150
        var steps = 0
        while (true) {
            state = u.undo(state) ?: break
            steps++
        }
        assertEquals(100, steps)
        assertEquals(50, state)
        assertEquals(51, u.redo(state))
        u.push(999)
        assertFalse(u.canRedo)
        assertNull(u.redo(0))
    }

    @Test
    fun manifestRoundTripsAndRejectsBadFiles() {
        val m = ProjectManifest(
            name = "Walk cycle", aspect = "SQUARE", widthPx = 1080, heightPx = 1080, fps = 12,
            paperArgb = 0xFFFCFCFA.toInt(),
            reels = listOf(
                ReelManifest("Walk", cels = (0 until 8).map { ProjectManifest.celEntry(0, it) }),
                ReelManifest("Flicker", hold = 2, phaseOffset = 1, opacity = 0.5f, cels = listOf(null, ProjectManifest.celEntry(1, 1))),
            ),
        )
        assertEquals(m, ProjectManifest.decode(ProjectManifest.encode(m)))

        fun rejects(bad: ProjectManifest) {
            try {
                ProjectManifest.decode(ProjectManifest.encode(bad))
                fail("accepted $bad")
            } catch (_: ManifestException) {
            }
        }
        rejects(m.copy(format = 2))
        rejects(m.copy(reels = listOf(ReelManifest("Long", cels = List(241) { null }))))
        rejects(m.copy(reels = listOf(ReelManifest("Escape", cels = listOf("../../databases/app.db")))))
        try {
            ProjectManifest.decode("{not json")
            fail()
        } catch (_: ManifestException) {
        }
    }

    @Test
    fun decodeMathRefusesHugePhotosAndPicksASafeSampleSize() {
        assertTrue(DecodeMath.isTooLarge(12000, 9000))
        assertFalse(DecodeMath.isTooLarge(8000, 6000))
        // 8000 x 6000 into a 1080 square: the fit is 1080 x 810; halving to 1000 would be too small.
        assertEquals(4, DecodeMath.sampleSize(8000, 6000, 1080, 1080))
        assertEquals(1, DecodeMath.sampleSize(800, 600, 1080, 1080))
        assertEquals(Pair(1080, 810), DecodeMath.fitInside(8000, 6000, 1080, 1080))
        assertEquals(Pair(3000, 4000), DecodeMath.orientedSize(4000, 3000, 90))
    }

    @Test
    fun yuvOfPureColoursMatchesBt601() {
        val white = Yuv.toI420(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2)
        assertEquals(235, white[0].toInt() and 0xFF)
        assertEquals(128, white[4].toInt() and 0xFF)
        assertEquals(128, white[5].toInt() and 0xFF)
        val black = Yuv.toI420(IntArray(4) { 0xFF000000.toInt() }, 2, 2)
        assertEquals(16, black[0].toInt() and 0xFF)
        val red = Yuv.toI420(IntArray(4) { 0xFFFF0000.toInt() }, 2, 2)
        assertEquals(82, red[0].toInt() and 0xFF)
        assertEquals(90, red[4].toInt() and 0xFF)
        assertEquals(240, red[5].toInt() and 0xFF)
        val nv12 = Yuv.toNv12(IntArray(4) { 0xFFFF0000.toInt() }, 2, 2)
        assertEquals(90, nv12[4].toInt() and 0xFF)
        assertEquals(240, nv12[5].toInt() and 0xFF)
    }

    @Test
    fun exportSizesAreEvenAndAlign() {
        assertEquals(Pair(720, 404), ExportMath.outputSize(1080, 608, 720))
        assertEquals(Pair(410, 512), ExportMath.outputSize(864, 1080, 512))
        assertEquals(Pair(720, 400), ExportMath.aligned16(720, 404))
        assertEquals(500_000L, ExportMath.frameTimeUs(6, 12))
    }

    @Test
    fun sampleIsTwelveOverFourAndBallTouchesTheFloorOnFrameZero() {
        val reels = SampleScripts.lampAndBall()
        assertEquals(listOf(4, 12), reels.map { it.frames.size })
        assertEquals(12L, LoopClock.cycle(reels.map { LoopClock.period(it.hold, it.frames.size) }))
        assertEquals(0f, SampleScripts.ballLift(0), 1e-6f)
        assertEquals(1f, SampleScripts.ballLift(6), 1e-6f)
    }
}
