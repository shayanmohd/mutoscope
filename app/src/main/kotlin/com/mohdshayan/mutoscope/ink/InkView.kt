package com.mohdshayan.mutoscope.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The drawing surface. One finger draws with the current tool; two fingers pan, zoom and rotate;
 * a quick two-finger tap undoes and a three-finger tap redoes. Once a stylus has touched the
 * screen, fingers only navigate. Input is requested unbuffered and historical samples are fed
 * through, so a fast line keeps every point the digitiser reported.
 */
@SuppressLint("ViewConstructor")
class InkView(context: Context, private val controller: InkController) : View(context) {

    private val compositor = CelCompositor()
    private val docToView = Matrix()
    private val viewToDoc = Matrix()
    private var userMoved = false
    private val pt = FloatArray(2)
    private val density = resources.displayMetrics.density

    /** Set on Android 10 and up: the overlay that draws strokes in progress with low latency. */
    var frontBuffer: FrontBufferInk? = null
    private var frontIndex = 0
    private var frontMatrix = Matrix()

    var chromeColour: Int = 0xFF9825A7.toInt()
    var frameColour: Int = 0x4D575B68

    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()
    private val docRect = RectF()

    // Gesture state
    private var stylusSeen = false
    private var mode = Mode.IDLE
    private var maxPointers = 0
    private var downTime = 0L
    private var travel = 0f
    private var startCentroid = FloatArray(2)
    private var startSpan = 1f
    private var startAngle = 0f
    private val startMatrix = Matrix()
    private var lastX = 0f
    private var lastY = 0f

    private enum class Mode { IDLE, TOOL, NAVIGATE }

    init {
        controller.invalidate = { postInvalidateOnAnimation() }
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    /** Fits the canvas in the view with a small margin, centred. */
    fun fit() {
        val scene = controller.host.scene() ?: return
        val p = scene.doc.project
        if (width == 0 || height == 0) return
        val margin = 12 * density
        val s = minOf((width - 2 * margin) / p.widthPx, (height - 2 * margin) / p.heightPx)
        docToView.reset()
        docToView.postScale(s, s)
        docToView.postTranslate((width - p.widthPx * s) / 2f, (height - p.heightPx * s) / 2f)
        docToView.invert(viewToDoc)
        userMoved = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val scene = controller.host.scene() ?: return
        val p = scene.doc.project
        if (!userMoved && docToView.isIdentity) fit()
        val scale = docToView.mapRadius(1f).coerceAtLeast(0.01f)
        docRect.set(0f, 0f, p.widthPx.toFloat(), p.heightPx.toFloat())

        canvas.save()
        canvas.concat(docToView)
        val r = 10 * density / scale
        clip.rewind()
        clip.addRoundRect(docRect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        compositor.draw(canvas, scene) { reel, cel, half -> controller.host.bitmapFor(reel, cel, half) }
        canvas.restore()

        framePaint.color = frameColour
        framePaint.strokeWidth = density / scale
        canvas.drawPath(clip, framePaint)

        dash.color = chromeColour
        dash.strokeWidth = 2 * density / scale
        dash.pathEffect = DashPathEffect(floatArrayOf(8 * density / scale, 6 * density / scale), 0f)
        controller.lassoPath?.let { canvas.drawPath(it, dash) }
        controller.floating?.let { canvas.drawPath(it.movedOutline(), dash) }
        canvas.restore()
    }

    private fun toDoc(x: Float, y: Float): FloatArray {
        pt[0] = x
        pt[1] = y
        viewToDoc.mapPoints(pt)
        return pt
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(event.actionIndex)
        if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) stylusSeen = true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                maxPointers = 1
                downTime = event.eventTime
                travel = 0f
                lastX = event.x
                lastY = event.y
                val fingerOnly = stylusSeen && tool == MotionEvent.TOOL_TYPE_FINGER
                if (fingerOnly) {
                    beginNavigate(event)
                } else {
                    mode = Mode.TOOL
                    val d = toDoc(event.x, event.y)
                    controller.down(d[0], d[1], event.eventTime)
                    startFrontBuffer()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
                if (mode == Mode.TOOL) {
                    controller.cancel()
                    stopFrontBuffer(immediately = true)
                }
                beginNavigate(event)
            }
            MotionEvent.ACTION_MOVE -> {
                travel += hypot(event.x - lastX, event.y - lastY)
                lastX = event.x
                lastY = event.y
                when (mode) {
                    Mode.TOOL -> {
                        for (h in 0 until event.historySize) {
                            val d = toDoc(event.getHistoricalX(h), event.getHistoricalY(h))
                            controller.move(d[0], d[1], event.getHistoricalEventTime(h))
                        }
                        val d = toDoc(event.x, event.y)
                        controller.move(d[0], d[1], event.eventTime)
                        feedFrontBuffer()
                    }
                    Mode.NAVIGATE -> navigate(event)
                    Mode.IDLE -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Re-base on the fingers that remain so the canvas does not jump.
                if (mode == Mode.NAVIGATE) beginNavigate(event, excluding = event.actionIndex)
            }
            MotionEvent.ACTION_UP -> {
                val quick = event.eventTime - downTime < TAP_MS && travel < TAP_SLOP * density * maxPointers
                when (mode) {
                    Mode.TOOL -> {
                        val d = toDoc(event.x, event.y)
                        controller.up(d[0], d[1], event.eventTime, wasTap = travel < TAP_SLOP * density)
                        stopFrontBuffer(immediately = false)
                    }
                    Mode.NAVIGATE -> {
                        if (quick && maxPointers == 2) controller.undoGesture()
                        if (quick && maxPointers == 3) controller.redoGesture()
                    }
                    Mode.IDLE -> Unit
                }
                mode = Mode.IDLE
            }
            MotionEvent.ACTION_CANCEL -> {
                controller.cancel()
                stopFrontBuffer(immediately = true)
                mode = Mode.IDLE
            }
        }
        return true
    }

    private fun startFrontBuffer() {
        val fb = frontBuffer ?: return
        val live = controller.live ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q || !FrontBufferInk.eligible(live)) return
        controller.frontBufferActive = true
        frontMatrix = Matrix(docToView)
        frontIndex = 0
        emit(fb, live, 0, 0)
    }

    private fun feedFrontBuffer() {
        val fb = frontBuffer ?: return
        if (!controller.frontBufferActive) return
        val live = controller.live ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        for (i in frontIndex + 1 until live.points.size) emit(fb, live, i - 1, i)
        frontIndex = live.points.size - 1
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun emit(fb: FrontBufferInk, live: LiveStroke, from: Int, to: Int) {
        val a = live.points[from]
        val b = live.points[to]
        val scene = controller.host.scene() ?: return
        val width = if (live.tool == com.mohdshayan.mutoscope.data.prefs.Tool.INK) live.size * Brushes.inkWidth((a.speed + b.speed) / 2f) else live.size
        fb.draw(
            FrontBufferInk.Segment(
                a.x, a.y, b.x, b.y, width, live.colour or 0xFF000000.toInt(),
                grain = live.tool == com.mohdshayan.mutoscope.data.prefs.Tool.PENCIL,
                matrix = frontMatrix,
                docW = scene.doc.project.widthPx.toFloat(),
                docH = scene.doc.project.heightPx.toFloat(),
            ),
        )
    }

    /** Hands the stroke back to the canvas view; the overlay clears once the view has repainted. */
    private fun stopFrontBuffer(immediately: Boolean) {
        val fb = frontBuffer ?: return
        if (!controller.frontBufferActive) return
        controller.frontBufferActive = false
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        invalidate()
        if (immediately) fb.clear() else postOnAnimation { postOnAnimation { fb.clear() } }
    }

    private fun beginNavigate(event: MotionEvent, excluding: Int = -1) {
        mode = Mode.NAVIGATE
        centroid(event, excluding, startCentroid)
        startSpan = span(event, excluding).coerceAtLeast(1f)
        startAngle = angle(event, excluding)
        startMatrix.set(docToView)
    }

    private fun navigate(event: MotionEvent) {
        val c = FloatArray(2)
        centroid(event, -1, c)
        docToView.set(startMatrix)
        if (event.pointerCount >= 2) {
            val scaleBy = (span(event, -1) / startSpan)
            val current = startMatrix.mapRadius(1f)
            val clamped = (current * scaleBy).coerceIn(0.1f, 12f) / current
            docToView.postScale(clamped, clamped, startCentroid[0], startCentroid[1])
            val rot = Math.toDegrees((angle(event, -1) - startAngle).toDouble()).toFloat()
            docToView.postRotate(rot, startCentroid[0], startCentroid[1])
        }
        docToView.postTranslate(c[0] - startCentroid[0], c[1] - startCentroid[1])
        docToView.invert(viewToDoc)
        userMoved = true
        invalidate()
    }

    private fun centroid(e: MotionEvent, excluding: Int, out: FloatArray) {
        var x = 0f
        var y = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == excluding) continue
            x += e.getX(i)
            y += e.getY(i)
            n++
        }
        out[0] = if (n > 0) x / n else 0f
        out[1] = if (n > 0) y / n else 0f
    }

    private fun firstTwo(e: MotionEvent, excluding: Int): IntArray? {
        val idx = (0 until e.pointerCount).filter { it != excluding }
        return if (idx.size >= 2) intArrayOf(idx[0], idx[1]) else null
    }

    private fun span(e: MotionEvent, excluding: Int): Float {
        val two = firstTwo(e, excluding) ?: return 1f
        return hypot(e.getX(two[0]) - e.getX(two[1]), e.getY(two[0]) - e.getY(two[1]))
    }

    private fun angle(e: MotionEvent, excluding: Int): Float {
        val two = firstTwo(e, excluding) ?: return 0f
        return atan2(e.getY(two[1]) - e.getY(two[0]), e.getX(two[1]) - e.getX(two[0]))
    }

    companion object {
        private const val TAP_MS = 280L
        private const val TAP_SLOP = 18f
    }
}
