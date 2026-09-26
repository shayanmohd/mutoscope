package com.mohdshayan.mutoscope.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * A lasso selection lifted off the working cel. The pixels inside the outline are cut out when
 * it is lifted, follow the finger while it is dragged, and are pasted back on [placeInto].
 */
class FloatingSelection private constructor(
    val bitmap: Bitmap,
    val left: Float,
    val top: Float,
    val outline: Path,
) {
    var dx = 0f
    var dy = 0f

    fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawBitmap(bitmap, left + dx, top + dy, paint)
    }

    fun movedOutline(): Path = Path(outline).apply { offset(dx, dy) }

    fun contains(x: Float, y: Float): Boolean {
        val b = RectF()
        movedOutline().computeBounds(b, true)
        b.inset(-24f, -24f)
        return b.contains(x, y)
    }

    fun placeInto(working: Bitmap) {
        Canvas(working).drawBitmap(bitmap, left + dx, top + dy, null)
    }

    companion object {
        /** Cuts the area inside [path] out of [working]. Null when the outline encloses nothing. */
        fun lift(working: Bitmap, path: Path): FloatingSelection? {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            val l = bounds.left.toInt().coerceIn(0, working.width)
            val t = bounds.top.toInt().coerceIn(0, working.height)
            val r = (bounds.right.toInt() + 1).coerceIn(0, working.width)
            val b = (bounds.bottom.toInt() + 1).coerceIn(0, working.height)
            if (r - l < 2 || b - t < 2) return null
            val piece = Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888)
            Canvas(piece).apply {
                translate(-l.toFloat(), -t.toFloat())
                clipPath(path)
                drawBitmap(working, 0f, 0f, null)
            }
            val clear = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                style = Paint.Style.FILL
            }
            Canvas(working).drawPath(path, clear)
            return FloatingSelection(piece, l.toFloat(), t.toFloat(), Path(path))
        }
    }
}
