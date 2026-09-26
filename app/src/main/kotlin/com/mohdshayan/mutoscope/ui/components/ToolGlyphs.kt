package com.mohdshayan.mutoscope.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The drawing tools' own glyphs, 24dp with a 2dp rounded stroke, so they read as one family.
 * Everything else uses Material Icons Rounded. Icon() tints them with the content colour.
 */
object ToolGlyphs {

    private fun glyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    val Pencil: ImageVector = glyph("pencil") {
        moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close()
        moveTo(13f, 7.5f); lineTo(16.5f, 11f)
        moveTo(5f, 15.5f); lineTo(8.5f, 19f)
    }

    val Nib: ImageVector = glyph("nib") {
        moveTo(12f, 21f); lineTo(6.5f, 12f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(17.5f, 12f); close()
        moveTo(12f, 21f); lineTo(12f, 13.5f)
        moveTo(10.5f, 11.5f); arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f); arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
    }

    val Marker: ImageVector = glyph("marker") {
        moveTo(10f, 3f); lineTo(14f, 5f); lineTo(14f, 9f); lineTo(10f, 9f); close()
        moveTo(8f, 9f); lineTo(16f, 9f); lineTo(16f, 21f); lineTo(8f, 21f); close()
        moveTo(8f, 13f); lineTo(16f, 13f)
    }

    val Eraser: ImageVector = glyph("eraser") {
        moveTo(8f, 20f); lineTo(4f, 16f); lineTo(14f, 6f); lineTo(20f, 12f); lineTo(12f, 20f); close()
        moveTo(9f, 11f); lineTo(15f, 17f)
        moveTo(12f, 20f); lineTo(20f, 20f)
    }

    val Lasso: ImageVector = glyph("lasso") {
        moveTo(20f, 9f)
        arcToRelative(8f, 5f, 0f, true, true, -16f, 0f)
        arcToRelative(8f, 5f, 0f, true, true, 16f, 0f)
        moveTo(7f, 13f)
        curveTo(5.5f, 15.5f, 9.5f, 17f, 8f, 21f)
    }

    val Onion: ImageVector = glyph("onion") {
        moveTo(4f, 9f); lineTo(15f, 9f); lineTo(15f, 20f); lineTo(4f, 20f); close()
        moveTo(7f, 9f); lineTo(7f, 6f); lineTo(18f, 6f); lineTo(18f, 17f); lineTo(15f, 17f)
        moveTo(10f, 6f); lineTo(10f, 3f); lineTo(21f, 3f); lineTo(21f, 14f); lineTo(18f, 14f)
    }

    val Fill: ImageVector = glyph("fill") {
        moveTo(11f, 3f); lineTo(19f, 11f); lineTo(12f, 18f); lineTo(4f, 10f); close()
        moveTo(4f, 10f); lineTo(19f, 11f)
        moveTo(20f, 15f); curveTo(21.5f, 17f, 21.5f, 19.5f, 20f, 19.5f); curveTo(18.5f, 19.5f, 18.5f, 17f, 20f, 15f)
    }
}
