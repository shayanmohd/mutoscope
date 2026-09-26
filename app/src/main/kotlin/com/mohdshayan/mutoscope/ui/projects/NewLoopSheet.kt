package com.mohdshayan.mutoscope.ui.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mohdshayan.mutoscope.core.loop.Aspect
import com.mohdshayan.mutoscope.ui.components.AppSheet
import com.mohdshayan.mutoscope.ui.components.LabeledSlider
import com.mohdshayan.mutoscope.ui.components.PrimaryButton
import com.mohdshayan.mutoscope.ui.theme.CellShape
import com.mohdshayan.mutoscope.ui.theme.ControlShape
import com.mohdshayan.mutoscope.ui.theme.PaperChoices
import com.mohdshayan.mutoscope.ui.theme.PaperNames
import kotlin.math.roundToInt

fun aspectLabel(a: Aspect): String = when (a) {
    Aspect.SQUARE -> "Square"
    Aspect.PORTRAIT_4_5 -> "4:5"
    Aspect.LANDSCAPE_16_9 -> "16:9"
    Aspect.VERTICAL_9_16 -> "9:16"
}


@Composable
fun NewLoopSheet(
    suggestedName: String,
    defaultFps: Int,
    onDismiss: () -> Unit,
    onCreate: (String, Aspect, Int, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    var aspect by rememberSaveable { mutableStateOf(Aspect.SQUARE) }
    var paper by rememberSaveable { mutableIntStateOf(0) }
    var fps by rememberSaveable { mutableFloatStateOf(defaultFps.toFloat()) }

    AppSheet("New loop", onDismiss) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Name") },
                singleLine = true,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Canvas shape", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in Aspect.entries) {
                    ShapeChip(a, a == aspect, Modifier.weight(1f)) { aspect = a }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Paper", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperChoices.forEachIndexed { i, c ->
                    val on = i == paper
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CellShape)
                            .padding(if (on) 5.dp else 3.dp)
                            .background(c, CellShape)
                            .selectable(selected = on, role = Role.RadioButton) { paper = i }
                            .semantics { contentDescription = "${PaperNames[i]} paper" },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LabeledSlider("Speed", "${fps.toInt()} fps", fps, { fps = it.roundToInt().toFloat() }, 4f..30f)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Create loop",
                { onCreate(name, aspect, PaperChoices[paper].toArgb(), fps.toInt()) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A canvas-shape chip: the shape itself drawn as an outline, with its name below. */
@Composable
private fun ShapeChip(a: Aspect, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val line = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, ControlShape)
            .clickable(role = Role.RadioButton, onClickLabel = aspectLabel(a), onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val ratio = a.width.toFloat() / a.height
            val w = if (ratio >= 1f) size.width else size.height * ratio
            val h = if (ratio >= 1f) size.width / ratio else size.height
            drawRoundRect(
                color = line,
                topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(aspectLabel(a), style = MaterialTheme.typography.labelMedium, color = line)
    }
}
