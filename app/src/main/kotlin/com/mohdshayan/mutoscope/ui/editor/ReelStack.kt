package com.mohdshayan.mutoscope.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.data.model.ReelDoc
import com.mohdshayan.mutoscope.ui.components.cycleLabel
import com.mohdshayan.mutoscope.ui.theme.LocalReducedMotion
import kotlin.math.floor
import kotlin.math.roundToLong

val CellWidth: Dp = 44.dp
val RowHeight: Dp = 48.dp
private val TabWidth: Dp = 92.dp
private val HeaderHeight: Dp = 28.dp

/**
 * The reel stack. Every reel is a strip of numbered cells repeated along one shared time axis, so
 * a 4-frame reel visibly repeats against a 12-frame reel. One orchid rule crosses every strip at
 * each point where all visible reels realign. Dragging scrubs; release lands on a whole frame.
 */
@Composable
fun ReelStack(
    doc: ProjectDoc,
    tick: Long,
    activeReelId: Long?,
    replaced: Set<Long>,
    visibleRows: Int,
    onSelectCell: (reelId: Long, tick: Long) -> Unit,
    onOpenReel: (reelId: Long) -> Unit,
    onCellMenu: (reelId: Long, tick: Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onAddReel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cycle = doc.cycle
    val reduced = LocalReducedMotion.current
    val showMarker = cycle > 1 && !LoopClock.isOverCap(cycle)
    val markerAt by animateFloatAsState(
        targetValue = if (showMarker) cycle.toFloat() else 0f,
        animationSpec = if (reduced) snap() else tween(200),
        label = "marker",
    )
    // Keep the playhead inside the visible columns: the strips follow it.
    var firstColumn by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    val colors = StripColors(
        accent = MaterialTheme.colorScheme.primary,
        onAccent = MaterialTheme.colorScheme.onPrimary,
        ink = MaterialTheme.colorScheme.onSurface,
        lead = MaterialTheme.colorScheme.onSurfaceVariant,
        hairline = MaterialTheme.colorScheme.outlineVariant,
        drawn = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
    val numberStyle = MaterialTheme.typography.labelSmall

    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surface)) {
        val density = LocalDensity.current
        val cellPx = with(density) { CellWidth.toPx() }
        val stripWidthPx = with(density) { (maxWidth - TabWidth).toPx() }.coerceAtLeast(cellPx)
        val visibleCols = stripWidthPx / cellPx
        if (tick < firstColumn + 0.5f || tick > firstColumn + visibleCols - 1.5f) {
            firstColumn = (tick - visibleCols * 0.3f).coerceAtLeast(0f).let { floor(it) }
        }
        val scrubState = rememberUpdatedState(tick)

        val reels = doc.reels.asReversed() // top of the stack first
        Column {
            // Header: the cycle label and the frame counter, both in tabular figures.
            Row(
                Modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cycleLabel(cycle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                val shown = if (!LoopClock.isOverCap(cycle)) (tick % cycle) + 1 else tick + 1
                Text(
                    "Frame $shown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.heightIn(max = RowHeight * visibleRows)) {
                items(reels, key = { it.id }) { reel ->
                    ReelRow(
                        reel = reel,
                        tick = tick,
                        active = reel.id == activeReelId,
                        replaced = replaced,
                        firstColumn = firstColumn,
                        markerAt = if (showMarker) markerAt else -1f,
                        cycle = cycle,
                        colors = colors,
                        measurer = measurer,
                        numberStyle = numberStyle,
                        onTab = { onOpenReel(reel.id) },
                        onTapColumn = { t -> onSelectCell(reel.id, t) },
                        onLongPressColumn = { t -> onCellMenu(reel.id, t) },
                        scrub = Modifier.pointerInput(reel.id) {
                            var startTick = 0L
                            var acc = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    startTick = scrubState.value
                                    acc = 0f
                                    onScrubStart()
                                },
                                onDragEnd = onScrubEnd,
                                onDragCancel = onScrubEnd,
                            ) { change, dx ->
                                change.consume()
                                acc += dx
                                onScrub((startTick + (acc / cellPx).roundToLong()).coerceAtLeast(0L))
                            }
                        },
                    )
                }
                item(key = "add") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .clickable(onClickLabel = "Add reel", role = Role.Button, onClick = onAddReel)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Add reel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private data class StripColors(
    val accent: Color,
    val onAccent: Color,
    val ink: Color,
    val lead: Color,
    val hairline: Color,
    val drawn: Color,
)

@Composable
private fun ReelRow(
    reel: ReelDoc,
    tick: Long,
    active: Boolean,
    replaced: Set<Long>,
    firstColumn: Float,
    markerAt: Float,
    cycle: Long,
    colors: StripColors,
    measurer: TextMeasurer,
    numberStyle: TextStyle,
    onTab: () -> Unit,
    onTapColumn: (Long) -> Unit,
    onLongPressColumn: (Long) -> Unit,
    scrub: Modifier,
) {
    val celNow = if (reel.isReference) 0 else reel.celIndexAt(tick)
    val description = when {
        reel.isReference -> "${reel.name}, locked photo"
        else -> "${reel.name}, frame ${celNow + 1} of ${reel.length}" +
            (if (reel.hidden) ", hidden" else "") +
            (if (reel.locked) ", locked" else "") +
            (if (active) ", current" else "")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(if (active) colors.accent.copy(alpha = 0.06f) else Color.Transparent),
    ) {
        // Name tab: tapping it opens the reel sheet.
        Row(
            Modifier
                .width(TabWidth)
                .fillMaxHeight()
                .clickable(onClickLabel = "Open ${reel.name} settings", role = Role.Button, onClick = onTab)
                .padding(start = 12.dp, end = 4.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    // The strip is drawn, not composed, so TalkBack reaches its frames from here.
                    if (!reel.isReference) {
                        customActions = listOf(
                            CustomAccessibilityAction("Next frame") { onTapColumn(tick + reel.hold); true },
                            CustomAccessibilityAction("Previous frame") { onTapColumn((tick - reel.hold).coerceAtLeast(0L)); true },
                            CustomAccessibilityAction("Frame actions") { onLongPressColumn(tick); true },
                        )
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    reel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!reel.isReference) {
                    val holdText = when (reel.hold) {
                        1 -> ""
                        2 -> ", twos"
                        3 -> ", threes"
                        else -> ", fours"
                    }
                    Text(
                        "${reel.length}f$holdText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            when {
                reel.isReference -> Icon(Icons.Rounded.Photo, contentDescription = null, tint = colors.lead, modifier = Modifier.size(16.dp))
                reel.hidden -> Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = colors.lead, modifier = Modifier.size(16.dp))
                reel.locked -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.lead, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(scrub)
                .pointerInput(reel.id, firstColumn) {
                    val cellPx = CellWidth.toPx()
                    detectTapGestures(
                        onTap = { o -> onTapColumn((firstColumn + o.x / cellPx).toLong()) },
                        onLongPress = { o -> onLongPressColumn((firstColumn + o.x / cellPx).toLong()) },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                clipRect {
                    if (reel.isReference) {
                        drawReferenceBar(colors)
                    } else {
                        drawStrip(reel, tick, active, replaced, firstColumn, colors, measurer, numberStyle)
                    }
                    if (markerAt > 0f) drawMarkers(markerAt, cycle, firstColumn, colors.accent)
                }
            }
        }
    }
}

private fun DrawScope.drawReferenceBar(colors: StripColors) {
    val inset = 4.dp.toPx()
    drawRoundRect(
        color = colors.hairline,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
}

private fun DrawScope.drawStrip(
    reel: ReelDoc,
    tick: Long,
    active: Boolean,
    replaced: Set<Long>,
    firstColumn: Float,
    colors: StripColors,
    measurer: TextMeasurer,
    numberStyle: TextStyle,
) {
    val cellPx = CellWidth.toPx()
    val inset = 2.dp.toPx()
    val vInset = 4.dp.toPx()
    val radius = CornerRadius(4.dp.toPx())
    val first = floor(firstColumn).toLong()
    val cols = (size.width / cellPx).toInt() + 2
    val nowStep = Math.floorDiv(tick, reel.hold.toLong())
    // Walk hold-sized steps so a cel on twos draws as one wide cell.
    var step = Math.floorDiv(first, reel.hold.toLong())
    val lastTick = first + cols
    while (step * reel.hold <= lastTick) {
        val t0 = step * reel.hold
        val idx = reel.celIndexAt(t0.coerceAtLeast(0))
        val cel = reel.cels[idx]
        val x = (t0 - firstColumn) * cellPx + inset
        val w = reel.hold * cellPx - 2 * inset
        val topLeft = Offset(x, vInset)
        val sz = Size(w, size.height - 2 * vInset)
        val current = active && step == nowStep
        val atPlayhead = step == nowStep
        when {
            current -> drawRoundRect(colors.accent, topLeft, sz, radius)
            cel.file != null -> drawRoundRect(colors.drawn, topLeft, sz, radius)
            else -> drawRoundRect(colors.hairline, topLeft, sz, radius, style = Stroke(1.dp.toPx()))
        }
        if (cel.id in replaced) {
            val gap = 6.dp.toPx()
            clipRect(x, vInset, x + w, size.height - vInset) {
                var hx = x - sz.height
                while (hx < x + w) {
                    drawLine(colors.lead, Offset(hx, size.height - vInset), Offset(hx + sz.height, vInset), 1.dp.toPx())
                    hx += gap
                }
            }
        }
        if (atPlayhead && !current) {
            drawRoundRect(colors.ink, topLeft, sz, radius, style = Stroke(1.5.dp.toPx()))
        }
        val label = measurer.measure((idx + 1).toString(), numberStyle.copy(color = if (current) colors.onAccent else colors.lead))
        drawText(
            label,
            topLeft = Offset(x + (w - label.size.width) / 2f, (size.height - label.size.height) / 2f),
        )
        step++
    }
}

/** The cycle marker: a 2dp orchid rule at every multiple of the cycle in view. */
private fun DrawScope.drawMarkers(markerAt: Float, cycle: Long, firstColumn: Float, accent: Color) {
    val cellPx = CellWidth.toPx()
    val width = 2.dp.toPx()
    val lastCol = firstColumn + size.width / cellPx
    var k = maxOf(1L, floor(firstColumn / markerAt).toLong())
    while (k * markerAt <= lastCol + 1) {
        val x = (k * markerAt - firstColumn) * cellPx
        drawLine(accent, Offset(x, 0f), Offset(x, size.height), width)
        k++
        if (cycle <= 0) break
    }
}
