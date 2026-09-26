package com.mohdshayan.mutoscope.core

import com.mohdshayan.mutoscope.core.gif.DelaySchedule
import com.mohdshayan.mutoscope.core.gif.GifEncoder
import com.mohdshayan.mutoscope.core.gif.Lzw
import com.mohdshayan.mutoscope.core.gif.PaletteMapper
import com.mohdshayan.mutoscope.core.gif.Quantizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class GifTest {

    /** A reference GIF LZW decoder, written independently of the encoder under test. */
    private fun decode(data: ByteArray, minCodeSize: Int): ByteArray {
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var size = minCodeSize + 1
        val dict = ArrayList<ByteArray>()
        fun reset() {
            dict.clear()
            for (i in 0 until clear) dict.add(byteArrayOf(i.toByte()))
            dict.add(ByteArray(0))
            dict.add(ByteArray(0))
            size = minCodeSize + 1
        }
        reset()
        val out = ByteArrayOutputStream()
        var bitPos = 0
        var prev: ByteArray? = null
        while (true) {
            var code = 0
            for (b in 0 until size) {
                val byte = data[(bitPos + b) / 8].toInt()
                code = code or (((byte shr ((bitPos + b) % 8)) and 1) shl b)
            }
            bitPos += size
            if (code == clear) {
                reset()
                prev = null
                continue
            }
            if (code == eoi) break
            val entry = when {
                code < dict.size -> dict[code]
                prev != null -> prev + prev[0]
                else -> error("bad code")
            }
            out.write(entry)
            if (prev != null && dict.size < 4096) dict.add(prev + entry[0])
            if (dict.size == (1 shl size) && size < 12) size++
            prev = entry
        }
        return out.toByteArray()
    }

    @Test
    fun lzwRoundTripsNoiseAndRunsPastTheTableLimit() {
        val rnd = Random(7)
        // Enough noise to fill the 4096-entry table several times and force clear codes.
        val noise = ByteArray(60_000) { rnd.nextInt(0, 256).toByte() }
        assertArrayEquals(noise, decode(Lzw.compress(noise, 8), 8))
        val runs = ByteArray(50_000) { (it / 700 % 4).toByte() }
        assertArrayEquals(runs, decode(Lzw.compress(runs, 2), 2))
        assertArrayEquals(ByteArray(1), decode(Lzw.compress(ByteArray(1), 2), 2))
    }

    @Test
    fun encoderWritesHeaderLoopBlockAndTrailer() {
        val bytes = ByteArrayOutputStream()
        val enc = GifEncoder(bytes, 4, 2, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF9825A7.toInt()))
        enc.start()
        enc.addFrame(ByteArray(8) { (it % 3).toByte() }, 8)
        enc.finish()
        val b = bytes.toByteArray()
        assertEquals("GIF89a", String(b, 0, 6, Charsets.US_ASCII))
        assertEquals(4, b[6].toInt())
        assertEquals(2, b[8].toInt())
        // Three colours pad to a four-entry table: size field 1, so 12 bytes of colour.
        assertEquals(0xF1, b[10].toInt() and 0xFF)
        val netscape = String(b, 13 + 12 + 3, 11, Charsets.US_ASCII)
        assertEquals("NETSCAPE2.0", netscape)
        val loopCountLo = b[13 + 12 + 3 + 11 + 2].toInt()
        assertEquals(0, loopCountLo)
        assertEquals(0x3B, b.last().toInt())
    }

    @Test
    fun delaysAtThirtyFpsCarryTheRemainder() {
        val d = DelaySchedule.centiseconds(30, 30)
        assertEquals(100, d.sum())
        assertTrue(d.all { it == 3 || it == 4 })
        assertEquals(100, DelaySchedule.centiseconds(7, 7).sum())
        assertEquals(50, DelaySchedule.centiseconds(6, 12).sum())
    }

    @Test
    fun quantizerCapsAt256AndKeepsFlatColoursExact() {
        val q = Quantizer(256)
        val rnd = Random(3)
        repeat(40_000) { q.add(0xFF000000.toInt() or rnd.nextInt(0, 0xFFFFFF)) }
        assertTrue(q.palette().size <= 256)

        val flat = Quantizer(256)
        val colours = intArrayOf(0xFFFCFCFA.toInt(), 0xFF2B2A33.toInt(), 0xFFD9482B.toInt())
        repeat(500) { flat.add(colours[it % 3]) }
        val palette = flat.palette()
        assertEquals(3, palette.size)
        val mapper = PaletteMapper(palette)
        for (c in colours) assertEquals(c, palette[mapper.index(c)])
    }

    /** Walks a finished file and returns the first frame's decoded indices. */
    private fun firstFrameIndices(gif: ByteArray, paletteEntries: Int): ByteArray {
        var pos = 13 + 3 * paletteEntries + 19 // header, table, Netscape block
        pos += 8 // graphic control extension
        assertEquals(0x2C, gif[pos].toInt() and 0xFF)
        pos += 10
        val minCode = gif[pos].toInt()
        pos++
        val data = ByteArrayOutputStream()
        while (true) {
            val n = gif[pos].toInt() and 0xFF
            pos++
            if (n == 0) break
            data.write(gif, pos, n)
            pos += n
        }
        return decode(data.toByteArray(), minCode)
    }

    @Test
    fun lzwHandlesEmptyTinyAndSmallAlphabetsThatRefillTheTable() {
        assertArrayEquals(ByteArray(0), decode(Lzw.compress(ByteArray(0), 2), 2))
        assertArrayEquals(byteArrayOf(3), decode(Lzw.compress(byteArrayOf(3), 2), 2))
        // Four symbols of noise fill the 4096-entry table many times over with 3-bit start codes.
        val rnd = Random(11)
        val small = ByteArray(200_000) { rnd.nextInt(0, 4).toByte() }
        assertArrayEquals(small, decode(Lzw.compress(small, 2), 2))
        // One long run and the top index of an 8-bit palette.
        val run = ByteArray(300_000) { 0xFF.toByte() }
        assertArrayEquals(run, decode(Lzw.compress(run, 8), 8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lzwRejectsACodeSizeGifCannotHold() {
        Lzw.compress(ByteArray(4), 9)
    }

    @Test
    fun oneColourAndFullPalettesDecodeBackToTheSameFrame() {
        for (colours in intArrayOf(1, 2, 256)) {
            val palette = IntArray(colours) { 0xFF000000.toInt() or (it * 0x010101) }
            val frame = ByteArray(33 * 17) { (it % colours).toByte() }
            val bytes = ByteArrayOutputStream()
            GifEncoder(bytes, 33, 17, palette).apply {
                start()
                addFrame(frame, 5)
                finish()
            }
            val entries = maxOf(2, Integer.highestOneBit(colours - 1).let { if (it == 0) 1 else it * 2 })
            assertArrayEquals("palette of $colours", frame, firstFrameIndices(bytes.toByteArray(), entries))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun encoderRefusesAFrameOfTheWrongSize() {
        val enc = GifEncoder(ByteArrayOutputStream(), 4, 4, intArrayOf(0xFF000000.toInt()))
        enc.start()
        enc.addFrame(ByteArray(15), 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encoderRefusesAnEmptyPalette() {
        GifEncoder(ByteArrayOutputStream(), 4, 4, IntArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encoderRefusesAZeroWidth() {
        GifEncoder(ByteArrayOutputStream(), 0, 4, intArrayOf(0xFF000000.toInt()))
    }

    @Test
    fun delaysAtTheEdgesOfTheSpeedRange() {
        assertEquals(0, DelaySchedule.centiseconds(0, 12).size)
        assertTrue(DelaySchedule.centiseconds(8, 4).all { it == 25 })
        // Every frame gets a real delay; none rounds down to zero at the fastest speed.
        val fast = DelaySchedule.centiseconds(600, 30)
        assertTrue(fast.all { it >= 3 })
        assertEquals(2000, fast.sum())
        // A cycle that is not a whole number of seconds still totals its true length, rounded down.
        assertEquals(24 * 100 / 7, DelaySchedule.centiseconds(24, 7).sum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun delaysRejectZeroFps() {
        DelaySchedule.centiseconds(4, 0)
    }

    @Test
    fun quantizerOfNothingStillGivesAPalette() {
        val p = Quantizer(256).palette()
        assertEquals(1, p.size)
        assertEquals(0, PaletteMapper(p).index(0xFFFFFFFF.toInt()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun quantizerRejectsTooFewColours() {
        Quantizer(7)
    }

    @Test
    fun mapperPicksTheNearestEntryAndIgnoresAlpha() {
        val palette = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt())
        val m = PaletteMapper(palette)
        assertEquals(0, m.index(0xFF101010.toInt()))
        assertEquals(1, m.index(0xFFF0F0F0.toInt()))
        assertEquals(2, m.index(0xFFE01010.toInt()))
        assertEquals(m.index(0xFFE01010.toInt()), m.index(0x00E01010))
        val out = ByteArray(3)
        m.map(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt()), out)
        assertArrayEquals(byteArrayOf(0, 1, 2), out)
    }
}
