package com.mohdshayan.mutoscope.core.gif

import java.io.ByteArrayOutputStream

/**
 * The variable-length LZW used by GIF: codes start at minCodeSize + 1 bits, grow to 12, and a
 * clear code resets the table when it fills. Bits are packed least significant first.
 */
object Lzw {

    private const val MAX_BITS = 12
    private const val MAX_CODES = 1 shl MAX_BITS

    fun compress(indices: ByteArray, minCodeSize: Int): ByteArray {
        require(minCodeSize in 2..8) { "GIF minimum code size is 2 to 8" }
        val out = BitWriter()
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var next = eoi + 1
        // Table keyed by (prefix code shl 8) or byte; open addressing keeps it allocation free.
        val table = HashMap<Int, Int>(MAX_CODES * 2)

        out.write(clear, codeSize)
        if (indices.isEmpty()) {
            out.write(eoi, codeSize)
            return out.toByteArray()
        }
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = table[key]
            if (found != null) {
                prefix = found
                continue
            }
            out.write(prefix, codeSize)
            if (next < MAX_CODES) {
                table[key] = next
                // The decoder widens one code later than the encoder assigns, so widen when
                // the code just assigned equals the current limit.
                if (next == (1 shl codeSize) && codeSize < MAX_BITS) codeSize++
                next++
            } else {
                out.write(clear, codeSize)
                table.clear()
                codeSize = minCodeSize + 1
                next = eoi + 1
            }
            prefix = k
        }
        out.write(prefix, codeSize)
        out.write(eoi, codeSize)
        return out.toByteArray()
    }

    private class BitWriter {
        private val bytes = ByteArrayOutputStream()
        private var acc = 0L
        private var bits = 0

        fun write(code: Int, size: Int) {
            acc = acc or (code.toLong() shl bits)
            bits += size
            while (bits >= 8) {
                bytes.write((acc and 0xFF).toInt())
                acc = acc ushr 8
                bits -= 8
            }
        }

        fun toByteArray(): ByteArray {
            if (bits > 0) bytes.write((acc and 0xFF).toInt())
            acc = 0
            bits = 0
            return bytes.toByteArray()
        }
    }
}
