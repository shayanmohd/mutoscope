package com.mohdshayan.mutoscope.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mohdshayan.mutoscope.ui.components.AppSheet
import com.mohdshayan.mutoscope.ui.components.QuietButton
import com.mohdshayan.mutoscope.ui.theme.CellShape
import com.mohdshayan.mutoscope.ui.theme.DrawingSwatches

/** The colour sheet: 24 swatches, a hue and saturation-value picker, the eyedropper and the last eight. */
@Composable
fun ColourSheet(
    current: Int,
    recent: List<Int>,
    onPick: (Int) -> Unit,
    onEyedropper: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv = remember(current) { FloatArray(3).also { android.graphics.Color.colorToHSV(current, it) } }
    var hue by remember(current) { mutableStateOf(hsv[0]) }
    var sat by remember(current) { mutableStateOf(hsv[1]) }
    var value by remember(current) { mutableStateOf(hsv[2]) }
    val picked = Color.hsv(hue, sat, value)

    AppSheet("Colour", onDismiss) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(picked, CellShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CellShape))
                Spacer(Modifier.width(12.dp))
                Text(
                    "#%06X".format(picked.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                QuietButton("Pick from canvas", onEyedropper)
            }
            Spacer(Modifier.height(16.dp))
            SwatchGrid(DrawingSwatches.map { it.toArgb() }, current, columns = 8, onPick)
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recent", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SwatchGrid(recent, current, columns = 8, onPick)
            }
            Spacer(Modifier.height(16.dp))
            Text("Mix", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SatValSquare(hue, sat, value, { s, v -> sat = s; value = v }, { onPick(Color.hsv(hue, sat, value).toArgb()) })
            Spacer(Modifier.height(12.dp))
            HueBar(hue, { hue = it }, { onPick(Color.hsv(hue, sat, value).toArgb()) })
        }
    }
}

@Composable
private fun SwatchGrid(colours: List<Int>, current: Int, columns: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        colours.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    val on = (c or 0xFF000000.toInt()) == (current or 0xFF000000.toInt())
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CellShape)
                            .padding(if (on) 4.dp else 1.dp)
                            .background(Color(c), CellShape)
                            .clickable(role = Role.Button, onClickLabel = "Use this colour") { onPick(c) }
                            .semantics { contentDescription = "Colour #%06X".format(c and 0xFFFFFF) },
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SatValSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit, onDone: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = "Saturation and brightness" }
            .pointerInput(hue) {
                fun set(o: Offset) = onChange((o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                detectTapGestures { set(it); onDone() }
            }
            .pointerInput(hue) {
                fun set(o: Offset) = onChange((o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                detectDragGestures(onDragEnd = onDone) { change, _ -> set(change.position) }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, 1f), Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.hsv(0f, 0f, 1f, 0f), Color.hsv(0f, 0f, 0f, 1f))))
        val c = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(ring, radius = 9.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = "Hue" }
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f) * 360f); onDone() }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDone) { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f) }
            },
    ) {
        drawRoundRect(
            Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        )
        val x = hue / 360f * size.width
        drawCircle(ring, radius = 11.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(2.dp.toPx()))
    }
}

/** The colour swatch shown on the tool bar's Colour button. */
@Composable
fun ColourChip(colour: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .background(Color(colour or 0xFF000000.toInt()), CellShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CellShape),
    )
}
