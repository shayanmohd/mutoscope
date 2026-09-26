package com.mohdshayan.mutoscope.core.sample

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ScriptBrush { PENCIL, INK, MARKER }

/**
 * One scripted mark. [points] are x, y pairs on a 1080 by 1080 canvas. When [fillArgb] is set
 * the closed shape is filled first and the line drawn over it.
 */
class ScriptStroke(
    val brush: ScriptBrush,
    val argb: Int,
    val width: Float,
    val points: FloatArray,
    val fillArgb: Int? = null,
    val closed: Boolean = false,
)

class ScriptReel(val name: String, val hold: Int, val frames: List<List<ScriptStroke>>)

/**
 * "Lamp and ball": a 12-frame bouncing ball over a 4-frame flickering lamp, drawn from these
 * scripts on first run so the app ships no images. The two lengths realign every 12 frames.
 */
object SampleScripts {

    const val NAME = "Lamp and ball"
    const val FPS = 12

    private const val GRAPHITE = 0xFF2B2A33.toInt()
    private const val BALL = 0xFFD9482B.toInt()
    private const val BULB = 0xFFF2C14E.toInt()
    private const val GLOW = 0x40F2C14E
    private const val SHADE = 0xFF3A3947.toInt()
    private const val SHADOW = 0x332B2A33

    fun lampAndBall(): List<ScriptReel> = listOf(lamp(), ball())

    private fun lamp(): ScriptReel {
        val flicker = floatArrayOf(1f, 0.72f, 1.08f, 0.84f)
        val frames = flicker.map { f ->
            val strokes = ArrayList<ScriptStroke>()
            // Light cone first, so everything else sits on top of it.
            strokes += ScriptStroke(
                ScriptBrush.MARKER, GLOW, 2f,
                floatArrayOf(250f, 300f, 450f, 300f, 560f + 60f * f, 880f, 140f - 60f * f, 880f),
                fillArgb = (((0x30 * f).toInt().coerceIn(0, 255)) shl 24) or (BULB and 0xFFFFFF),
                closed = true,
            )
            strokes += ScriptStroke(ScriptBrush.PENCIL, GRAPHITE, 5f, floatArrayOf(350f, 0f, 351f, 120f, 349f, 210f))
            strokes += ScriptStroke(
                ScriptBrush.INK, GRAPHITE, 7f,
                floatArrayOf(310f, 210f, 390f, 210f, 450f, 300f, 250f, 300f),
                fillArgb = SHADE, closed = true,
            )
            strokes += ScriptStroke(ScriptBrush.INK, GRAPHITE, 5f, ellipse(350f, 318f, 34f, 30f), fillArgb = BULB, closed = true)
            for (k in 0 until 5) {
                val a = PI * (0.2 + 0.15 * k)
                val r0 = 62f
                val r1 = 62f + 46f * f * (if (k % 2 == 0) 1f else 0.7f)
                strokes += ScriptStroke(
                    ScriptBrush.PENCIL, GRAPHITE, 4f,
                    floatArrayOf(
                        350f + r0 * cos(a).toFloat(), 318f + r0 * sin(a).toFloat(),
                        350f + r1 * cos(a).toFloat(), 318f + r1 * sin(a).toFloat(),
                    ),
                )
            }
            // The floor belongs to the lamp reel so it holds still under the ball.
            strokes += ScriptStroke(ScriptBrush.PENCIL, GRAPHITE, 5f, floatArrayOf(60f, 882f, 540f, 879f, 1020f, 883f))
            strokes
        }
        return ScriptReel("Lamp", 1, frames)
    }

    /** Ball height for frame i of 12: on the floor at 0, at the top at 6. */
    fun ballLift(i: Int): Float {
        val t = i / 12f
        return 1f - (2f * t - 1f) * (2f * t - 1f)
    }

    private fun ball(): ScriptReel {
        val frames = (0 until 12).map { i ->
            val lift = ballLift(i)
            val (sx, sy) = when (i) {
                0 -> 1.28f to 0.74f
                1, 11 -> 0.9f to 1.14f
                else -> 1f to 1f
            }
            val r = 72f
            val cx = 700f
            val cy = 880f - r * sy - 440f * lift
            val shadowW = 90f - 45f * lift
            listOf(
                ScriptStroke(ScriptBrush.MARKER, SHADOW, 2f, ellipse(cx, 888f, shadowW, 12f), fillArgb = SHADOW, closed = true),
                ScriptStroke(ScriptBrush.INK, GRAPHITE, 7f, ellipse(cx, cy, r * sx, r * sy), fillArgb = BALL, closed = true),
                ScriptStroke(
                    ScriptBrush.PENCIL, GRAPHITE, 4f,
                    arc(cx, cy, r * sx * 0.62f, r * sy * 0.62f, PI * 1.1, PI * 1.45),
                ),
            )
        }
        return ScriptReel("Ball", 1, frames)
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 48): FloatArray =
        FloatArray(n * 2) { j ->
            val a = 2 * PI * (j / 2) / n
            if (j % 2 == 0) cx + rx * cos(a).toFloat() else cy + ry * sin(a).toFloat()
        }

    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double, n: Int = 12): FloatArray =
        FloatArray((n + 1) * 2) { j ->
            val a = from + (to - from) * (j / 2) / n
            if (j % 2 == 0) cx + rx * cos(a).toFloat() else cy + ry * sin(a).toFloat()
        }
}
