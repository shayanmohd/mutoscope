package com.mohdshayan.mutoscope.ink

import android.graphics.Bitmap
import android.graphics.Path
import com.mohdshayan.mutoscope.core.paint.StrokeSmoother
import com.mohdshayan.mutoscope.data.model.CelDoc
import com.mohdshayan.mutoscope.data.model.ReelDoc
import com.mohdshayan.mutoscope.data.prefs.Tool

/** Why a touch on the canvas did not draw. */
enum class Blocked { LOADING, LOCKED, HIDDEN, REFERENCE, PLAYING }

/** What the canvas asks of its owner. The editor's ViewModel implements it. */
interface InkHost {
    fun scene(): Scene?
    fun bitmapFor(reel: ReelDoc, cel: CelDoc, half: Boolean): Bitmap?
    fun blocked(): Blocked?
    fun onBlockedTouch(reason: Blocked)
    fun onStrokeFinished(stroke: LiveStroke)
    fun onFill(x: Float, y: Float)
    fun onEyedrop(x: Float, y: Float)
    fun onLasso(path: Path)
    fun onSelectionMoved()
    fun onUndoGesture()
    fun onRedoGesture()
}

/**
 * The drawing state that must survive rotation: the tool settings, the stroke in progress, the
 * lasso outline and the floating selection. Lives in the editor's ViewModel; the view only turns
 * touches into calls here and draws what it is told.
 */
class InkController(val host: InkHost) {

    var tool: Tool = Tool.PENCIL
    var colour: Int = 0xFF1C1D24.toInt()
    var size: Float = 5f
    var opacity: Float = 1f
    var streamline: Int = 40
    var eyedropArmed: Boolean = false

    /** True while the stroke in progress is drawn by the front buffer instead of the canvas view. */
    var frontBufferActive: Boolean = false

    var live: LiveStroke? = null
        private set
    var lassoPath: Path? = null
        private set
    var floating: FloatingSelection? = null

    private var smoother: StrokeSmoother? = null
    private var lassoPoints = 0
    private var dragFrom: FloatArray? = null

    /** Set by the view so state changes made here can repaint it. */
    var invalidate: () -> Unit = {}

    fun down(x: Float, y: Float, time: Long) {
        if (eyedropArmed) return
        if (floating != null) {
            dragFrom = floatArrayOf(x, y)
            return
        }
        host.blocked()?.let {
            host.onBlockedTouch(it)
            return
        }
        when (tool) {
            Tool.PENCIL, Tool.INK, Tool.MARKER, Tool.ERASER -> {
                val s = LiveStroke(tool, colour, size, opacity)
                val sm = StrokeSmoother(streamline, spacing = (size / 4f).coerceIn(0.75f, 6f))
                s.points += sm.begin(x, y, time)
                smoother = sm
                live = s
            }
            Tool.LASSO -> {
                lassoPath = Path().apply { moveTo(x, y) }
                lassoPoints = 1
            }
            Tool.FILL -> Unit
        }
        invalidate()
    }

    fun move(x: Float, y: Float, time: Long) {
        dragFrom?.let { from ->
            val f = floating ?: return
            f.dx += x - from[0]
            f.dy += y - from[1]
            from[0] = x
            from[1] = y
            invalidate()
            return
        }
        live?.let { s ->
            s.points += smoother?.add(x, y, time).orEmpty()
            invalidate()
            return
        }
        lassoPath?.let {
            it.lineTo(x, y)
            lassoPoints++
            invalidate()
        }
    }

    fun up(x: Float, y: Float, time: Long, wasTap: Boolean) {
        if (eyedropArmed) {
            eyedropArmed = false
            host.onEyedrop(x, y)
            return
        }
        if (dragFrom != null) {
            dragFrom = null
            host.onSelectionMoved()
            return
        }
        live?.let { s ->
            s.points += smoother?.end(x, y, time).orEmpty()
            live = null
            smoother = null
            host.onStrokeFinished(s)
            invalidate()
            return
        }
        lassoPath?.let { path ->
            lassoPath = null
            if (lassoPoints >= 3) {
                path.close()
                host.onLasso(path)
            }
            invalidate()
            return
        }
        if (tool == Tool.FILL && wasTap && host.blocked() == null) host.onFill(x, y)
    }

    /** A second finger landed: whatever one finger started is dropped, not committed. */
    fun cancel() {
        live = null
        smoother = null
        lassoPath = null
        dragFrom = null
        invalidate()
    }

    fun undoGesture() = host.onUndoGesture()
    fun redoGesture() = host.onRedoGesture()
}
