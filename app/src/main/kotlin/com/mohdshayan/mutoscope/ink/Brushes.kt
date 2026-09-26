package com.mohdshayan.mutoscope.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.mohdshayan.mutoscope.core.paint.GrainNoise
import com.mohdshayan.mutoscope.core.paint.StrokePoint
import com.mohdshayan.mutoscope.core.sample.ScriptBrush
import com.mohdshayan.mutoscope.core.sample.ScriptStroke
import com.mohdshayan.mutoscope.data.prefs.Tool

/** A stroke being drawn or about to be committed, in canvas pixels. */
class LiveStroke(val tool: Tool, val colour: Int, val size: Float, val opacity: Float) {
    val points = ArrayList<StrokePoint>()
    val isEraser: Boolean get() = tool == Tool.ERASER
}

/**
 * The brush engine. One [Brushes] per thread that draws (the editor's UI thread, the export
 * worker), because paints and shaders are reused between calls.
 *
 * Every brush draws its whole stroke opaque into a layer, and the layer is composited once at the
 * brush opacity. That is why a marker pass never builds up where it overlaps itself.
 */
class Brushes {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val layer = Paint()
    private val eraseLayer = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val path = Path()

    private var grainColour = 0
    private var grainShader: BitmapShader? = null

    /** Layer alpha for the stroke: the marker is flat at 70 percent of the chosen opacity. */
    fun layerAlpha(stroke: LiveStroke): Int {
        val o = if (stroke.tool == Tool.MARKER) stroke.opacity * MARKER_OPACITY else stroke.opacity
        return (o.coerceIn(0f, 1f) * 255).toInt()
    }

    /** Draws [stroke] onto [canvas] as its own layer: normal paint, or erasing what is under it. */
    fun drawLayered(canvas: Canvas, stroke: LiveStroke, width: Float, height: Float) {
        if (stroke.points.isEmpty()) return
        val p = if (stroke.isEraser) eraseLayer else layer
        p.alpha = layerAlpha(stroke)
        canvas.saveLayer(0f, 0f, width, height, p)
        drawOpaque(canvas, stroke)
        canvas.restore()
    }

    /** Commits a stroke into a cel bitmap. */
    fun commit(target: Bitmap, stroke: LiveStroke) {
        drawLayered(Canvas(target), stroke, target.width.toFloat(), target.height.toFloat())
    }

    private fun drawOpaque(canvas: Canvas, stroke: LiveStroke) {
        val pts = stroke.points
        val colour = if (stroke.isEraser) 0xFF000000.toInt() else (stroke.colour or 0xFF000000.toInt())
        if (pts.size == 1) {
            dot.color = colour
            dot.shader = if (stroke.tool == Tool.PENCIL) grain(colour) else null
            canvas.drawCircle(pts[0].x, pts[0].y, stroke.size / 2f, dot)
            return
        }
        line.color = colour
        line.shader = null
        when (stroke.tool) {
            Tool.INK -> {
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    line.strokeWidth = stroke.size * inkWidth((a.speed + b.speed) / 2f)
                    canvas.drawLine(a.x, a.y, b.x, b.y, line)
                }
            }
            else -> {
                line.strokeWidth = stroke.size
                if (stroke.tool == Tool.PENCIL) line.shader = grain(colour)
                path.rewind()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                canvas.drawPath(path, line)
                line.shader = null
            }
        }
    }

    /** Draws one scripted mark from the sample: its fill, then its line. */
    fun drawScript(canvas: Canvas, s: ScriptStroke) {
        val n = s.points.size / 2
        if (n == 0) return
        if (s.fillArgb != null && n >= 3) {
            path.rewind()
            path.moveTo(s.points[0], s.points[1])
            for (i in 1 until n) path.lineTo(s.points[2 * i], s.points[2 * i + 1])
            path.close()
            fill.color = s.fillArgb
            canvas.drawPath(path, fill)
        }
        if (s.brush == ScriptBrush.MARKER) return
        val tool = if (s.brush == ScriptBrush.INK) Tool.INK else Tool.PENCIL
        val stroke = LiveStroke(tool, s.argb, s.width, 1f)
        for (i in 0 until n) stroke.points += StrokePoint(s.points[2 * i], s.points[2 * i + 1], 0.3f)
        if (s.closed) stroke.points += StrokePoint(s.points[0], s.points[1], 0.3f)
        drawLayered(canvas, stroke, canvas.width.toFloat(), canvas.height.toFloat())
    }

    private fun grain(colour: Int): BitmapShader {
        grainShader?.let { if (grainColour == colour) return it }
        val size = GRAIN_TILE
        val alpha = GrainNoise.tile(size, seed = 11)
        val rgb = colour and 0xFFFFFF
        val px = IntArray(size * size) { i -> ((alpha[i] * 255).toInt() shl 24) or rgb }
        val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainShader = shader
        grainColour = colour
        return shader
    }

    companion object {
        const val MARKER_OPACITY = 0.7f
        private const val GRAIN_TILE = 96

        /** Ink thins as the hand speeds up: full width when slow, a third when fast. */
        fun inkWidth(speed: Float): Float = (1.15f - speed * 0.22f).coerceIn(0.35f, 1.1f)
    }
}
