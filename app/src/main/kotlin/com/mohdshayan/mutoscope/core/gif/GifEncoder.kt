package com.mohdshayan.mutoscope.core.gif

import java.io.OutputStream

/**
 * A streaming GIF89a writer with one global palette and a Netscape loop block, so the file
 * loops forever and colours never shift between frames. Call [start], then [addFrame] per frame
 * with palette indices, then [finish].
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    palette: IntArray,
) {
    private val paletteBits: Int
    private val paddedPalette: IntArray

    init {
        require(width in 1..65535 && height in 1..65535) { "GIF size out of range" }
        require(palette.isNotEmpty() && palette.size <= 256) { "palette holds 1 to 256 colours" }
        var bits = 1
        while ((1 shl bits) < palette.size) bits++
        paletteBits = bits
        paddedPalette = IntArray(1 shl bits) { i -> if (i < palette.size) palette[i] else 0 }
    }

    fun start() {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        // Global colour table present, colour resolution 8 bits, table size 2^(n+1).
        out.write(0x80 or (0x07 shl 4) or (paletteBits - 1))
        out.write(0) // background colour index
        out.write(0) // pixel aspect ratio
        for (c in paddedPalette) {
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // NETSCAPE2.0 application extension: loop count 0 means forever.
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(0)
        out.write(0)
    }

    fun addFrame(indices: ByteArray, delayCentiseconds: Int) {
        require(indices.size == width * height) { "frame must be width x height indices" }
        // Graphic control extension: no disposal, no transparency.
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0)
        writeShort(delayCentiseconds.coerceIn(0, 65535))
        out.write(0)
        out.write(0)
        // Image descriptor covering the whole screen, no local table.
        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0)
        val minCodeSize = paletteBits.coerceAtLeast(2)
        out.write(minCodeSize)
        val data = Lzw.compress(indices, minCodeSize)
        var pos = 0
        while (pos < data.size) {
            val n = minOf(255, data.size - pos)
            out.write(n)
            out.write(data, pos, n)
            pos += n
        }
        out.write(0)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}
