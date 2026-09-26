package com.mohdshayan.mutoscope.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import com.mohdshayan.mutoscope.core.loop.ExportMath
import com.mohdshayan.mutoscope.core.video.Yuv
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

class EncoderRefusedException(val longEdge: Int) : Exception("encoder refused $longEdge px")

/**
 * H.264 in an MP4 through MediaCodec and MediaMuxer. Frames go in as flexible YUV 4:2:0 images
 * filled from ARGB by [Yuv], one key frame a second, timestamps tick x 1,000,000 / fps.
 */
object Mp4Writer {

    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    /** The size this phone's encoder accepts: as asked, else snapped to 16, else null. */
    fun supportedSize(width: Int, height: Int): Pair<Int, Int>? {
        val caps = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, ignoreCase = true) } }
            .mapNotNull { runCatching { it.getCapabilitiesForType(MIME).videoCapabilities }.getOrNull() }
        if (caps.isEmpty()) return null
        if (caps.any { it.isSizeSupported(width, height) }) return Pair(width, height)
        val a = ExportMath.aligned16(width, height)
        return if (caps.any { it.isSizeSupported(a.first, a.second) }) a else null
    }

    suspend fun write(
        file: File,
        width: Int,
        height: Int,
        fps: Int,
        frames: Int,
        longEdge: Int,
        render: (tick: Long, out: IntArray) -> Unit,
        progress: (frame: Int) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, ExportMath.bitrate(longEdge))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = try {
            MediaCodec.createEncoderByType(MIME).also { it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
        } catch (e: Exception) {
            throw EncoderRefusedException(longEdge)
        }
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()
        val argb = IntArray(width * height)

        fun drain(endOfStream: Boolean) {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing && buf != null) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            codec.start()
            var frame = 0
            while (frame < frames) {
                coroutineContext.ensureActive()
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val image = codec.getInputImage(inIdx) ?: throw EncoderRefusedException(longEdge)
                    render(frame.toLong(), argb)
                    fillImage(image, argb, width, height)
                    codec.queueInputBuffer(inIdx, 0, width * height * 3 / 2, ExportMath.frameTimeUs(frame.toLong(), fps), 0)
                    frame++
                    progress(frame)
                }
                drain(false)
            }
            var eosQueued = false
            while (!eosQueued) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    codec.queueInputBuffer(inIdx, 0, 0, ExportMath.frameTimeUs(frames.toLong(), fps), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eosQueued = true
                } else {
                    drain(false)
                }
            }
            drain(true)
        } catch (e: IllegalStateException) {
            throw EncoderRefusedException(longEdge)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxing) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun fillImage(image: android.media.Image, argb: IntArray, width: Int, height: Int) {
        val i420 = Yuv.toI420(argb, width, height)
        val planes = image.planes
        val ySize = width * height
        val cw = width / 2
        val ch = height / 2
        // Y
        val yPlane = planes[0]
        val yBuf = yPlane.buffer
        for (row in 0 until height) {
            if (yPlane.pixelStride == 1) {
                yBuf.position(row * yPlane.rowStride)
                yBuf.put(i420, row * width, width)
            } else {
                for (col in 0 until width) yBuf.put(row * yPlane.rowStride + col * yPlane.pixelStride, i420[row * width + col])
            }
        }
        // U then V, whatever their stride and interleaving.
        for (p in 1..2) {
            val plane = planes[p]
            val buf = plane.buffer
            val base = ySize + (p - 1) * (ySize / 4)
            for (row in 0 until ch) {
                for (col in 0 until cw) {
                    val pos = row * plane.rowStride + col * plane.pixelStride
                    if (pos < buf.limit()) buf.put(pos, i420[base + row * cw + col])
                }
            }
        }
    }
}
