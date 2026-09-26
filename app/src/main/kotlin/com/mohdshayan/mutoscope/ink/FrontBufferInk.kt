package com.mohdshayan.mutoscope.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.os.Build
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import com.mohdshayan.mutoscope.core.paint.GrainNoise
import com.mohdshayan.mutoscope.data.prefs.Tool

/**
 * Low-latency ink on Android 10 and up. While a finger draws, each new piece of the line goes
 * straight to the front buffer of a transparent SurfaceView laid over the canvas, skipping the
 * normal frame queue. When the finger lifts the stroke is committed into the cel by the ordinary
 * path, the canvas view repaints, and the front buffer is cleared.
 *
 * Only opaque pencil and ink strokes take this path: a translucent marker or the eraser has to
 * be composited as a whole stroke, which the front buffer cannot do without visible build-up.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class FrontBufferInk(val surface: SurfaceView) {

    /** One piece of line, in canvas pixels, with the view transform it was drawn under. */
    class Segment(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val width: Float,
        val colour: Int,
        val grain: Boolean,
        val matrix: Matrix,
        val docW: Float,
        val docH: Float,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var grainColour = 0
    private var grainShader: BitmapShader? = null

    private val renderer: CanvasFrontBufferedRenderer<Segment>

    init {
        surface.setZOrderOnTop(true)
        surface.holder.setFormat(PixelFormat.TRANSLUCENT)
        renderer = CanvasFrontBufferedRenderer(
            surface,
            object : CanvasFrontBufferedRenderer.Callback<Segment> {
                override fun onDrawFrontBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: Segment) {
                    canvas.save()
                    canvas.concat(param.matrix)
                    canvas.clipRect(0f, 0f, param.docW, param.docH)
                    paint.color = param.colour
                    paint.strokeWidth = param.width
                    paint.shader = if (param.grain) grain(param.colour) else null
                    canvas.drawLine(param.x0, param.y0, param.x1, param.y1, paint)
                    canvas.restore()
                }

                override fun onDrawMultiBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, params: Collection<Segment>) {
                    canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
                }
            },
        )
    }

    fun draw(segment: Segment) {
        if (renderer.isValid()) renderer.renderFrontBufferedLayer(segment)
    }

    fun clear() {
        if (renderer.isValid()) renderer.clear()
    }

    fun release() {
        renderer.release(true)
    }

    private fun grain(colour: Int): BitmapShader {
        grainShader?.let { if (grainColour == colour) return it }
        val size = 96
        val alpha = GrainNoise.tile(size, seed = 11)
        val rgb = colour and 0xFFFFFF
        val px = IntArray(size * size) { i -> ((alpha[i] * 255).toInt() shl 24) or rgb }
        val shader = BitmapShader(Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainShader = shader
        grainColour = colour
        return shader
    }

    companion object {
        fun eligible(stroke: LiveStroke): Boolean =
            (stroke.tool == Tool.PENCIL || stroke.tool == Tool.INK) && stroke.opacity >= 0.999f
    }
}
