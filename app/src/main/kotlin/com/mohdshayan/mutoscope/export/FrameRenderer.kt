package com.mohdshayan.mutoscope.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.mohdshayan.mutoscope.core.io.DecodeMath
import com.mohdshayan.mutoscope.data.cels.CelStore
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.ink.CelCompositor
import com.mohdshayan.mutoscope.ink.Scene

/**
 * Renders finished frames for export at an output size: paper, then every visible reel, with the
 * reference photo only when it is switched into the export. Cels decode once at the smallest
 * sample size that still covers the output, and stay in a bounded cache while the loop repeats.
 */
class FrameRenderer(
    private val doc: ProjectDoc,
    private val store: CelStore,
    val width: Int,
    val height: Int,
) {
    private val compositor = CelCompositor()
    private val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(frame)
    private val sample = DecodeMath.sampleSize(doc.project.widthPx, doc.project.heightPx, width, height)
    private val cache = object : LruCache<String, Bitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Names of cels that could not be read and were drawn as blank. */
    val unreadable = HashSet<String>()

    fun render(tick: Long, out: IntArray) {
        canvas.save()
        canvas.scale(width.toFloat() / doc.project.widthPx, height.toFloat() / doc.project.heightPx)
        compositor.draw(canvas, Scene(doc, tick, forExport = true)) { _, cel, _ ->
            val name = cel.file ?: return@draw null
            cache.get(name) ?: store.decode(doc.project.id, name, sample)?.also { cache.put(name, it) }
                ?: null.also { unreadable += name }
        }
        canvas.restore()
        frame.getPixels(out, 0, width, 0, 0, width, height)
    }
}
