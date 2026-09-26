package com.mohdshayan.mutoscope.export

import com.mohdshayan.mutoscope.core.gif.DelaySchedule
import com.mohdshayan.mutoscope.core.gif.GifEncoder
import com.mohdshayan.mutoscope.core.gif.PaletteMapper
import com.mohdshayan.mutoscope.core.gif.Quantizer
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Two passes over the loop: the first samples every frame into one octree so the palette is
 * shared (no colour flicker), the second maps and LZW-encodes each frame. The paper is the
 * background, so every pixel is opaque.
 */
object GifWriter {

    suspend fun write(
        file: File,
        renderer: FrameRenderer,
        frames: Int,
        fps: Int,
        progress: (pass: Int, frame: Int) -> Unit,
    ) {
        val w = renderer.width
        val h = renderer.height
        val px = IntArray(w * h)
        val q = Quantizer(256)
        val step = maxOf(1, (w * h) / 60_000)
        for (i in 0 until frames) {
            coroutineContext.ensureActive()
            renderer.render(i.toLong(), px)
            q.addAll(px, step)
            progress(1, i + 1)
        }
        val palette = q.palette()
        val mapper = PaletteMapper(palette)
        val delays = DelaySchedule.centiseconds(frames, fps)
        val indices = ByteArray(w * h)
        BufferedOutputStream(FileOutputStream(file), 256 * 1024).use { out ->
            val enc = GifEncoder(out, w, h, palette)
            enc.start()
            for (i in 0 until frames) {
                coroutineContext.ensureActive()
                renderer.render(i.toLong(), px)
                mapper.map(px, indices)
                enc.addFrame(indices, delays[i])
                progress(2, i + 1)
            }
            enc.finish()
        }
    }
}
