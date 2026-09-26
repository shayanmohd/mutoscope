package com.mohdshayan.mutoscope.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import com.mohdshayan.mutoscope.data.model.CelDoc
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.data.model.ReelDoc

/** Onion skin for the active reel: ghost counts, tints and strength. */
data class OnionSpec(
    val before: Int,
    val after: Int,
    val beforeTint: Int,
    val afterTint: Int,
    val opacity: Float,
)

/**
 * What to draw for one frame. [working] replaces the active reel's current cel when the editor
 * holds it in memory; [live] is a stroke in progress; [floating] a lasso selection being moved.
 */
class Scene(
    val doc: ProjectDoc,
    val tick: Long,
    val activeReelId: Long? = null,
    val working: Bitmap? = null,
    val live: LiveStroke? = null,
    val onion: OnionSpec? = null,
    val lightTable: Boolean = false,
    val proxies: Boolean = false,
    val forExport: Boolean = false,
    val floating: FloatingSelection? = null,
)

/**
 * Paints paper, reference photo, every visible reel in z order, the active reel's ghosts and its
 * working cel. The canvas is in canvas pixels (0..widthPx, 0..heightPx). Not thread safe: each
 * thread that draws keeps its own compositor.
 */
class CelCompositor(private val brushes: Brushes = Brushes()) {

    private val paper = Paint()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val layerPaint = Paint()
    private val dst = RectF()

    fun draw(
        canvas: Canvas,
        scene: Scene,
        bitmapFor: (reel: ReelDoc, cel: CelDoc, half: Boolean) -> Bitmap?,
    ) {
        val p = scene.doc.project
        val w = p.widthPx.toFloat()
        val h = p.heightPx.toFloat()
        dst.set(0f, 0f, w, h)
        paper.color = p.paperArgb
        canvas.drawRect(dst, paper)

        for (reel in scene.doc.reels) {
            if (reel.hidden) continue
            if (reel.isReference && scene.forExport && !reel.includeInExport) continue
            val isActive = reel.id == scene.activeReelId && !scene.forExport
            var alpha = reel.opacity
            if (scene.lightTable && !isActive && !reel.isReference && !scene.forExport) alpha *= LIGHT_TABLE_ALPHA
            val celIdx = reel.celIndexAt(scene.tick)

            if (isActive && scene.onion != null && reel.length > 1) drawGhosts(canvas, reel, celIdx, scene.onion, bitmapFor)

            if (isActive && scene.working != null) {
                layerPaint.alpha = (alpha * 255).toInt()
                canvas.saveLayer(dst, layerPaint)
                canvas.drawBitmap(scene.working, null, dst, bmpPaint)
                scene.floating?.draw(canvas, bmpPaint)
                scene.live?.let { brushes.drawLayered(canvas, it, w, h) }
                canvas.restore()
            } else {
                val cel = reel.cels[celIdx]
                if (cel.file != null) {
                    val bmp = bitmapFor(reel, cel, scene.proxies) ?: continue
                    bmpPaint.alpha = (alpha * 255).toInt()
                    canvas.drawBitmap(bmp, null, dst, bmpPaint)
                    bmpPaint.alpha = 255
                }
                if (isActive) scene.live?.let { brushes.drawLayered(canvas, it, w, h) }
            }
        }
    }

    private fun drawGhosts(
        canvas: Canvas,
        reel: ReelDoc,
        current: Int,
        onion: OnionSpec,
        bitmapFor: (ReelDoc, CelDoc, Boolean) -> Bitmap?,
    ) {
        // On a short reel the frames before and after wrap into each other; draw each cel once,
        // and never the current one.
        val drawn = HashSet<Int>()
        drawn += current
        val before = onion.before.coerceAtMost(reel.length - 1)
        val after = onion.after.coerceAtMost(reel.length - 1)
        // Farthest first, so nearer ghosts sit on top.
        for (k in maxOf(before, after) downTo 1) {
            if (k <= before && drawn.add(Math.floorMod(current - k, reel.length))) {
                drawGhost(canvas, reel, current - k, k, onion.before, onion.beforeTint, onion.opacity, bitmapFor)
            }
            if (k <= after && drawn.add(Math.floorMod(current + k, reel.length))) {
                drawGhost(canvas, reel, current + k, k, onion.after, onion.afterTint, onion.opacity, bitmapFor)
            }
        }
    }

    private fun drawGhost(
        canvas: Canvas,
        reel: ReelDoc,
        rawIndex: Int,
        distance: Int,
        count: Int,
        tint: Int,
        opacity: Float,
        bitmapFor: (ReelDoc, CelDoc, Boolean) -> Bitmap?,
    ) {
        val idx = Math.floorMod(rawIndex, reel.length)
        val cel = reel.cels[idx]
        if (cel.file == null) return
        val bmp = bitmapFor(reel, cel, true) ?: return
        val fade = 1f - (distance - 1).toFloat() / (count + 1)
        ghostPaint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        ghostPaint.alpha = (opacity * fade * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, null, dst, ghostPaint)
    }

    companion object {
        const val LIGHT_TABLE_ALPHA = 0.3f
    }
}
