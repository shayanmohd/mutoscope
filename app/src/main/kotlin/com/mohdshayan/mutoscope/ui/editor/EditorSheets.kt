package com.mohdshayan.mutoscope.ui.editor

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mohdshayan.mutoscope.data.db.ProjectEntity
import com.mohdshayan.mutoscope.data.model.ReelDoc
import com.mohdshayan.mutoscope.data.prefs.EditorPrefs
import com.mohdshayan.mutoscope.data.prefs.OnionTint
import com.mohdshayan.mutoscope.data.prefs.Tool
import com.mohdshayan.mutoscope.ui.components.AppSheet
import com.mohdshayan.mutoscope.ui.components.ChoiceRow
import com.mohdshayan.mutoscope.ui.components.GlyphButton
import com.mohdshayan.mutoscope.ui.components.LabeledSlider
import com.mohdshayan.mutoscope.ui.components.PlainTextButton
import com.mohdshayan.mutoscope.ui.components.PrimaryButton
import com.mohdshayan.mutoscope.ui.components.QuietButton
import com.mohdshayan.mutoscope.ui.components.SwitchRow
import com.mohdshayan.mutoscope.ui.components.plural
import com.mohdshayan.mutoscope.ui.theme.CellShape
import com.mohdshayan.mutoscope.ui.theme.ControlShape
import com.mohdshayan.mutoscope.ui.theme.PaperChoices
import com.mohdshayan.mutoscope.ui.theme.PaperNames
import kotlin.math.roundToInt

fun toolName(t: Tool): String = when (t) {
    Tool.PENCIL -> "Pencil"
    Tool.INK -> "Ink"
    Tool.MARKER -> "Marker"
    Tool.ERASER -> "Eraser"
    Tool.FILL -> "Fill"
    Tool.LASSO -> "Lasso"
}

@Composable
fun BrushSheet(prefs: EditorPrefs, vm: EditorViewModel, onDismiss: () -> Unit) {
    val brush = if (prefs.tool in listOf(Tool.PENCIL, Tool.INK, Tool.MARKER)) prefs.tool else prefs.brush
    var size by remember(brush) { mutableFloatStateOf(prefs.sizeOf(brush)) }
    var opacity by remember { mutableFloatStateOf(prefs.brushOpacity) }
    var streamline by remember { mutableFloatStateOf(prefs.streamline.toFloat()) }
    AppSheet("Brush", onDismiss) {
        ChoiceRow(listOf(Tool.PENCIL, Tool.INK, Tool.MARKER), brush, ::toolName, { vm.setTool(it) })
        Spacer(Modifier.height(8.dp))
        Text(
            when (brush) {
                Tool.PENCIL -> "Thin, with a paper grain."
                Tool.INK -> "Thins as your stroke speeds up."
                else -> "Flat at 70 percent, no build-up inside one stroke."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledSlider("Size", "${size.roundToInt()} px", size, { size = it }, 1f..80f, onValueChangeFinished = { vm.setBrushSize(brush, size) })
        LabeledSlider("Opacity", "${(opacity * 100).roundToInt()}%", opacity, { opacity = it }, 0.05f..1f, onValueChangeFinished = { vm.setOpacity(opacity) })
        LabeledSlider("Streamline", "${streamline.roundToInt()}", streamline, { streamline = it }, 0f..100f, onValueChangeFinished = { vm.setStreamline(streamline.roundToInt()) })
    }
}

@Composable
fun EraserSheet(prefs: EditorPrefs, vm: EditorViewModel, onDismiss: () -> Unit) {
    var size by remember { mutableFloatStateOf(prefs.sizeOf(Tool.ERASER)) }
    AppSheet("Eraser", onDismiss) {
        LabeledSlider("Size", "${size.roundToInt()} px", size, { size = it }, 2f..120f, onValueChangeFinished = { vm.setBrushSize(Tool.ERASER, size) })
    }
}

@Composable
fun FillSheet(prefs: EditorPrefs, vm: EditorViewModel, onDismiss: () -> Unit) {
    var tolerance by remember { mutableFloatStateOf(prefs.fillTolerance.toFloat()) }
    AppSheet("Fill", onDismiss) {
        Text("Tap inside a shape on the current frame to fill it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledSlider("Tolerance", "${tolerance.roundToInt()}", tolerance, { tolerance = it }, 0f..128f, onValueChangeFinished = { vm.setFill(tolerance.roundToInt(), prefs.fillCloseGaps) })
        SwitchRow("Close small gaps", prefs.fillCloseGaps, { vm.setFill(tolerance.roundToInt(), it) }, "Holds the fill in when a line has a break of a few pixels.")
    }
}

@Composable
fun OnionSheet(prefs: EditorPrefs, vm: EditorViewModel, onDismiss: () -> Unit) {
    var opacity by remember { mutableFloatStateOf(prefs.onionOpacity) }
    fun set(before: Int = prefs.onionBefore, after: Int = prefs.onionAfter, tint: OnionTint = prefs.onionTint, op: Float = opacity, light: Boolean = prefs.lightTable) =
        vm.setOnion(before, after, tint, op, light)
    AppSheet("Onion skin", onDismiss) {
        Stepper("Frames before", prefs.onionBefore, 0..5, { set(before = it) }, suffix = { plural(it, "ghost", "ghosts") })
        Stepper("Frames after", prefs.onionAfter, 0..5, { set(after = it) }, suffix = { plural(it, "ghost", "ghosts") })
        Spacer(Modifier.height(8.dp))
        Text("Ghost colour", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(
            OnionTint.entries.toList(),
            prefs.onionTint,
            { when (it) { OnionTint.LIGHT_TABLE -> "Red and teal"; OnionTint.ORCHID -> "Orchid"; OnionTint.GRAPHITE -> "Graphite" } },
            { set(tint = it) },
        )
        LabeledSlider("Ghost strength", "${(opacity * 100).roundToInt()}%", opacity, { opacity = it }, 0.1f..1f, onValueChangeFinished = { set(op = opacity) })
        SwitchRow("Light table", prefs.lightTable, { set(light = it) }, "Shows the other reels at 30 percent while you draw.")
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, suffix: (Int) -> String = { it.toString() }) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(suffix(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlyphButton(Icons.Rounded.Remove, "Fewer: $label", { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first)
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            modifier = Modifier.width(36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        GlyphButton(Icons.Rounded.Add, "More: $label", { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last)
    }
}

/** The reel sheet body, shared by the bottom sheet on phones and the docked panel on wide screens. */
@Composable
fun ReelSettings(reel: ReelDoc, vm: EditorViewModel, onReplacePhoto: () -> Unit, onClose: () -> Unit) {
    var name by remember(reel.id) { mutableStateOf(reel.name) }
    var opacity by remember(reel.id) { mutableFloatStateOf(reel.opacity) }
    LaunchedEffect(reel.opacity) { opacity = reel.opacity }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (reel.isReference) {
            Text("A locked photo under every frame. It stays out of exports unless you switch it in.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledSlider("Opacity", "${(opacity * 100).roundToInt()}%", opacity, { opacity = it }, 0.05f..1f, onValueChangeFinished = { vm.updateReel(reel.id) { it.copy(opacity = opacity) } })
            SwitchRow("Include in export", reel.includeInExport, { v -> vm.updateReel(reel.id) { it.copy(includeInExport = v) } })
            SwitchRow("Hide", reel.hidden, { v -> vm.updateReel(reel.id) { it.copy(hidden = v) } })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Replace photo", onReplacePhoto, Modifier.weight(1f))
                QuietButton("Remove photo", { vm.removeReference(); onClose() }, Modifier.weight(1f))
            }
            return@Column
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Name") },
            singleLine = true,
            shape = ControlShape,
            modifier = Modifier.fillMaxWidth(),
        )
        if (name.isNotBlank() && name != reel.name) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PlainTextButton("Rename reel", { vm.updateReel(reel.id) { it.copy(name = name.trim()) } })
            }
        }
        Stepper("Length", reel.length, 1..EditorViewModel.MAX_FRAMES, { vm.setReelLength(reel.id, it) }, suffix = { plural(it, "frame", "frames") })
        Spacer(Modifier.height(4.dp))
        Text("Hold each frame for", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(listOf(1, 2, 3, 4), reel.hold, { when (it) { 1 -> "Ones"; 2 -> "Twos"; 3 -> "Threes"; else -> "Fours" } }, { h -> vm.updateReel(reel.id) { it.copy(hold = h) } })
        Stepper("Offset", reel.phaseOffset, 0..(reel.length - 1).coerceAtLeast(0), { o -> vm.updateReel(reel.id) { it.copy(phaseOffset = o) } }, suffix = { "Starts on frame ${it + 1}" })
        LabeledSlider("Opacity", "${(opacity * 100).roundToInt()}%", opacity, { opacity = it }, 0.05f..1f, onValueChangeFinished = { vm.updateReel(reel.id) { it.copy(opacity = opacity) } })
        SwitchRow("Hide", reel.hidden, { v -> vm.updateReel(reel.id) { it.copy(hidden = v) } }, "Hidden reels leave the cycle and the export.")
        SwitchRow("Lock", reel.locked, { v -> vm.updateReel(reel.id) { it.copy(locked = v) } }, "A locked reel cannot be drawn on.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Move up", { vm.moveReel(reel.id, up = true) }, Modifier.weight(1f))
            QuietButton("Move down", { vm.moveReel(reel.id, up = false) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Duplicate", { vm.duplicateReel(reel.id); onClose() }, Modifier.weight(1f))
            QuietButton("Delete", { vm.deleteReel(reel.id); onClose() }, Modifier.weight(1f))
        }
    }
}

/** Long-press on a cell: the frame actions. */
@Composable
fun FrameSheet(title: String, vm: EditorViewModel, onDismiss: () -> Unit) {
    AppSheet(title, onDismiss) {
        val actions = listOf<Pair<String, () -> Unit>>(
            "Add frame after" to vm::addFrameAfter,
            "Duplicate" to vm::duplicateFrame,
            "Clear" to vm::clearFrame,
            "Delete" to vm::deleteFrame,
            "Move left" to { vm.moveFrame(-1) },
            "Move right" to { vm.moveFrame(1) },
        )
        Column {
            actions.forEachIndexed { i, (label, action) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { action(); onDismiss() }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
fun ProjectSettingsSheet(
    project: ProjectEntity,
    reference: ReelDoc?,
    vm: EditorViewModel,
    onAddPhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var fps by remember { mutableFloatStateOf(project.fps.toFloat()) }
    var name by remember { mutableStateOf(project.name) }
    AppSheet("Project settings", onDismiss) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Name") },
                singleLine = true,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            )
            if (name.isNotBlank() && name != project.name) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PlainTextButton("Rename loop", { vm.rename(name) })
                }
            }
            LabeledSlider("Speed", "${fps.roundToInt()} fps", fps, { fps = it.roundToInt().toFloat() }, 4f..30f, onValueChangeFinished = { vm.setFps(fps.roundToInt()) })
            Text("Paper", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperChoices.forEachIndexed { i, c ->
                    val on = c.toArgb() == project.paperArgb
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CellShape)
                            .padding(if (on) 5.dp else 3.dp)
                            .background(c, CellShape)
                            .selectable(on, role = Role.RadioButton) { vm.setPaper(c.toArgb()) }
                            .semantics { contentDescription = "${PaperNames[i]} paper" },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Reference photo", style = MaterialTheme.typography.titleSmall)
            Text(
                "Pick a photo to trace over. It sits locked under every frame. Mutoscope keeps its own copy, so the original is never changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (reference == null) {
                PrimaryButton("Add reference photo", onAddPhoto, Modifier.fillMaxWidth())
            } else {
                var opacity by remember(reference.id) { mutableFloatStateOf(reference.opacity) }
                LabeledSlider("Photo opacity", "${(opacity * 100).roundToInt()}%", opacity, { opacity = it }, 0.05f..1f, onValueChangeFinished = { vm.updateReel(reference.id) { it.copy(opacity = opacity) } })
                SwitchRow("Include in export", reference.includeInExport, { v -> vm.updateReel(reference.id) { it.copy(includeInExport = v) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("Replace photo", onAddPhoto, Modifier.weight(1f))
                    QuietButton("Remove photo", vm::removeReference, Modifier.weight(1f))
                }
            }
        }
    }
}
