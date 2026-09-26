package com.mohdshayan.mutoscope.core

import com.mohdshayan.mutoscope.core.archive.ManifestException
import com.mohdshayan.mutoscope.core.archive.ProjectManifest
import com.mohdshayan.mutoscope.core.archive.ReelManifest
import com.mohdshayan.mutoscope.core.gif.DelaySchedule
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.core.stats.DrawingDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Damaged import files, hostile sizes and calendar edges found in review. */
class ReviewEdgeTest {

    private fun manifest(opacity: Float = 1f) = ProjectManifest(
        name = "Walk", aspect = "SQUARE", widthPx = 1080, heightPx = 1080, fps = 12, paperArgb = -1,
        reels = listOf(ReelManifest("Walk", opacity = opacity, cels = listOf("cels/0-0.png"))),
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** Reads project.json out of a zip the way import does, through the bounded reader. */
    private fun manifestFromZip(zip: ByteArray): ProjectManifest? {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: return null
                if (e.name == ProjectManifest.FILE_NAME) return ProjectManifest.decode(ProjectManifest.readText(z))
            }
        }
    }

    /** An endless stream of one byte, like a zip bomb inflating. */
    private class Endless : InputStream() {
        var served = 0L
        override fun read(): Int = 'a'.code.also { served++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            java.util.Arrays.fill(b, off, off + len, 'a'.code.toByte())
            served += len
            return len
        }
    }

    @Test
    fun anInflatingManifestIsRefusedWithoutReadingItAll() {
        val endless = Endless()
        try {
            ProjectManifest.readText(endless, limit = 64 * 1024)
            fail("read an endless manifest")
        } catch (e: ManifestException) {
            assertTrue(e.message!!.contains("too large"))
        }
        assertTrue(endless.served < 200 * 1024)
    }

    @Test
    fun theManifestLimitIsInclusiveAndEmptyIsEmpty() {
        assertEquals(64, ProjectManifest.readText(ByteArrayInputStream(ByteArray(64) { 'x'.code.toByte() }), limit = 64).length)
        assertEquals("", ProjectManifest.readText(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun aRealManifestSurvivesTheZipRoundTrip() {
        val m = manifest()
        val zip = zipOf(ProjectManifest.FILE_NAME to ProjectManifest.encode(m).toByteArray())
        assertEquals(m, manifestFromZip(zip))
    }

    @Test
    fun garbageInsideAZipIsRefusedWithAReason() {
        for (junk in listOf(ByteArray(0), byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x7F), "{\"format\":1".toByteArray(), ByteArray(3000) { (it * 31).toByte() })) {
            try {
                manifestFromZip(zipOf(ProjectManifest.FILE_NAME to junk))
                fail("accepted a damaged project.json")
            } catch (e: ManifestException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun aFileThatIsNotAZipHoldsNoManifest() {
        val notZip = "This is a text file, not a project".toByteArray()
        assertEquals(null, manifestFromZip(notZip))
        assertEquals(null, manifestFromZip(ByteArray(0)))
        // A real zip cut off halfway: whatever was readable holds no manifest, and nothing throws past the reader.
        val whole = zipOf("cels/0-0.png" to ByteArray(4000) { it.toByte() }, ProjectManifest.FILE_NAME to ProjectManifest.encode(manifest()).toByteArray())
        val cut = whole.copyOf(whole.size / 3)
        try {
            assertEquals(null, manifestFromZip(cut))
        } catch (_: java.io.IOException) {
            // A truncated entry may also surface as an IOException, which import reports as unreadable.
        }
    }

    @Test
    fun opacityOutsideZeroToOneIsRefused() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.01f, 1.01f)) {
            try {
                ProjectManifest.validate(manifest(opacity = bad))
                fail("accepted opacity $bad")
            } catch (e: ManifestException) {
                assertTrue(e.message!!.contains("opacity"))
            }
        }
        ProjectManifest.validate(manifest(opacity = 0f))
        ProjectManifest.validate(manifest(opacity = 1f))
        // NaN written as JSON by a hand-edited file is not valid JSON at all.
        try {
            ProjectManifest.decode(ProjectManifest.encode(manifest()).replace("\"opacity\":1.0", "\"opacity\":NaN"))
            fail("accepted NaN")
        } catch (_: ManifestException) {
        }
    }

    @Test
    fun delaysForNoFramesAreEmptyAndNegativeIsRefused() {
        assertEquals(0, DelaySchedule.centiseconds(0, 12).size)
        try {
            DelaySchedule.centiseconds(-1, 12)
            fail("accepted a negative frame count")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun theLongestCappedGifStillSumsToItsRunningTime() {
        // 600 frames is the most a one-cycle GIF holds; at every speed the delays add up exactly.
        for (fps in 4..30) {
            val d = DelaySchedule.centiseconds(600, fps)
            assertEquals(600L * 100 / fps, d.sumOf { it.toLong() })
            assertTrue(d.all { it >= 3 })
        }
    }

    @Test
    fun theCycleAtTheCapIsExportableAndOneMoreIsNot() {
        // 24 x 25 = 600 exactly; 601 is prime and only reachable as a product past the cap.
        val atCap = LoopClock.cycle(listOf(24L, 25L))
        assertEquals(600L, atCap)
        assertTrue(!LoopClock.isOverCap(atCap))
        assertTrue(LoopClock.isOverCap(LoopClock.cycle(listOf(24L, 25L, 7L))))
        assertEquals(LoopClock.CYCLE_OVERFLOW, LoopClock.lcm(Long.MAX_VALUE / 2 + 1, 3))
    }

    @Test
    fun theDrawingWindowCrossesLeapDaysAndYearEnds() {
        val leap = LocalDate.parse("2028-03-01").toEpochDay()
        val feb29 = LocalDate.parse("2028-02-29").toEpochDay()
        assertEquals(1L, leap - feb29)
        val newYear = LocalDate.parse("2027-01-01").toEpochDay()
        val days = listOf(LocalDate.parse("2026-12-03").toEpochDay(), LocalDate.parse("2026-12-02").toEpochDay(), newYear)
        // 3 December is 29 days before 1 January, so it counts; 2 December is 30 days before and does not.
        assertEquals(2, DrawingDays.countRecent(days, newYear))
        assertEquals(1, DrawingDays.countRecent(listOf(Long.MIN_VALUE, Long.MAX_VALUE, newYear), newYear))
    }
}
