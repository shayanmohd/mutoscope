package com.mohdshayan.mutoscope.ui.editor

import android.os.Build
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.mutoscope.data.prefs.EditorPrefs
import com.mohdshayan.mutoscope.data.prefs.Tool
import com.mohdshayan.mutoscope.ink.FrontBufferInk
import com.mohdshayan.mutoscope.ink.InkView
import com.mohdshayan.mutoscope.ui.components.EmptyState
import com.mohdshayan.mutoscope.ui.components.GlyphButton
import com.mohdshayan.mutoscope.ui.components.PrimaryButton
import com.mohdshayan.mutoscope.ui.components.QuietButton
import com.mohdshayan.mutoscope.ui.components.ToolGlyphs
import com.mohdshayan.mutoscope.ui.theme.ControlShape
import com.mohdshayan.mutoscope.ui.theme.LocalCanvasColors
import com.mohdshayan.mutoscope.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

private enum class Sheet { BRUSH, ERASER, FILL, COLOUR, ONION, REEL, FRAME, PROJECT, EXPORT }

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onPreview: (Long) -> Unit,
    vm: EditorViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prefs by vm.editorPrefs.collectAsStateWithLifecycle()
    val loads by vm.loads.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var sheetReel by rememberSaveable { mutableStateOf<Long?>(null) }
    var overflow by remember { mutableStateOf(false) }

    val canvasColours = LocalCanvasColors.current
    val scheme = MaterialTheme.colorScheme
    SideEffect {
        vm.setGhostColours(
            canvasColours.ghostBefore.toArgb(),
            canvasColours.ghostAfter.toArgb(),
            scheme.primary.toArgb(),
            scheme.onSurfaceVariant.toArgb(),
        )
    }

    LaunchedEffect(Unit) { vm.onOpened(reduced) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importReference(uri)
    }
    // The sheet closes first, so the result message is not hidden behind it.
    val pickPhoto = {
        sheet = null
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val doc = ui.doc
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            ui.missing -> EmptyState(
                title = "This loop is gone",
                body = "It may have been deleted. Your other loops are on the Projects screen.",
                actionLabel = "Back to loops",
                onAction = onBack,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
            doc == null -> LoadingEditor(onBack)
            // Bars and cutout only: the keyboard of a sheet's text field must not re-lay the editor,
            // or a short phone flips to the wide layout and the open sheet with its field is removed.
            else -> BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                val wide = maxWidth >= 600.dp || maxWidth > maxHeight
                val shortScreen = maxHeight < 500.dp
                val topBar: @Composable () -> Unit = {
                    EditorTopBar(
                        name = doc.project.name,
                        playing = ui.playing,
                        canUndo = ui.canUndo || ui.selectionActive,
                        canRedo = ui.canRedo,
                        onBack = onBack,
                        onUndo = vm::undo,
                        onRedo = vm::redo,
                        onPlay = vm::togglePlay,
                        overflow = overflow,
                        onOverflow = { overflow = it },
                        onPreview = {
                            scope.launch {
                                vm.pause()
                                vm.saveNow()
                                onPreview(doc.project.id)
                            }
                        },
                        onExport = { vm.pause(); sheet = Sheet.EXPORT },
                        onProjectSettings = { sheet = Sheet.PROJECT },
                    )
                }
                val canvas: @Composable (Modifier) -> Unit = { m ->
                    CanvasArea(vm, ui, prefs, loads, m)
                }
                val stack: @Composable (Int) -> Unit = { rows ->
                    ReelStack(
                        doc = doc,
                        tick = ui.tick,
                        activeReelId = ui.activeReelId,
                        replaced = ui.replacedCels,
                        visibleRows = rows,
                        onSelectCell = vm::selectCell,
                        onOpenReel = { id ->
                            vm.selectReel(id)
                            sheetReel = id
                            sheet = Sheet.REEL
                        },
                        onCellMenu = { id, t ->
                            vm.selectCell(id, t)
                            if (doc.reels.firstOrNull { it.id == id }?.isReference == false) sheet = Sheet.FRAME
                        },
                        onScrubStart = vm::scrubStart,
                        onScrub = vm::scrubTo,
                        onScrubEnd = vm::scrubEnd,
                        onAddReel = vm::addReel,
                    )
                }
                val onTool: (ToolButton) -> Unit = { b -> handleTool(b, prefs, vm) { sheet = it } }

                if (!wide) {
                    Column(Modifier.fillMaxSize()) {
                        topBar()
                        canvas(Modifier.weight(1f).fillMaxWidth())
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        stack(4)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ToolBar(prefs, vertical = false, onTool = onTool)
                    }
                } else {
                    val dockReel = sheet == Sheet.REEL
                    Row(Modifier.fillMaxSize()) {
                        if (!prefs.leftHanded) {
                            ToolBar(prefs, vertical = true, onTool = onTool)
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            topBar()
                            if (shortScreen) {
                                // A phone on its side: strips beside the canvas, so the drawing
                                // keeps most of the height instead of a sliver above the stack.
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    canvas(Modifier.weight(1f).fillMaxHeight())
                                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.42f).background(MaterialTheme.colorScheme.surface)) {
                                        stack(5)
                                    }
                                }
                            } else {
                                canvas(Modifier.weight(1f).fillMaxWidth())
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                stack(3)
                            }
                        }
                        if (dockReel) {
                            val reel = doc.reels.firstOrNull { it.id == sheetReel }
                            if (reel != null) {
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(320.dp).fillMaxHeight()) {
                                    Column(Modifier.padding(horizontal = 16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(reel.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            GlyphButton(Icons.Rounded.Close, "Close reel settings", { sheet = null })
                                        }
                                        ReelSettings(reel, vm, pickPhoto) { sheet = null }
                                    }
                                }
                            }
                        }
                        if (prefs.leftHanded) {
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ToolBar(prefs, vertical = true, onTool = onTool)
                        }
                    }
                }

                // Sheets. On wide screens the reel sheet is the docked panel above instead.
                when (sheet) {
                    Sheet.BRUSH -> BrushSheet(prefs, vm) { sheet = null }
                    Sheet.ERASER -> EraserSheet(prefs, vm) { sheet = null }
                    Sheet.FILL -> FillSheet(prefs, vm) { sheet = null }
                    Sheet.ONION -> OnionSheet(prefs, vm) { sheet = null }
                    Sheet.COLOUR -> ColourSheet(
                        current = prefs.colour,
                        recent = prefs.recentColours,
                        onPick = vm::setColour,
                        onEyedropper = {
                            sheet = null
                            vm.armEyedropper()
                        },
                        onDismiss = { sheet = null },
                    )
                    Sheet.REEL -> if (!wide) {
                        val reel = doc.reels.firstOrNull { it.id == sheetReel }
                        if (reel == null) {
                            sheet = null
                        } else {
                            com.mohdshayan.mutoscope.ui.components.AppSheet(reel.name, { sheet = null }, scroll = false) {
                                ReelSettings(reel, vm, pickPhoto) { sheet = null }
                            }
                        }
                    }
                    Sheet.FRAME -> {
                        val reel = ui.activeReel
                        if (reel != null) {
                            FrameSheet("Frame ${reel.celIndexAt(ui.tick) + 1} on ${reel.name}", vm) { sheet = null }
                        }
                    }
                    Sheet.PROJECT -> ProjectSettingsSheet(doc.project, doc.reference, vm, pickPhoto) { sheet = null }
                    Sheet.EXPORT -> ExportSheet(
                        doc = doc,
                        beforeStart = { vm.saveNow() },
                        onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } },
                        onDismiss = { sheet = null },
                    )
                    null -> Unit
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 120.dp))
    }
}

@Composable
private fun CanvasArea(vm: EditorViewModel, ui: EditorUi, prefs: EditorPrefs, loads: Long, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val frame = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val empty = ui.doc?.reels?.all { r -> r.cels.all { it.file == null } } == true
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                val ink = InkView(ctx, vm.ink)
                FrameLayout(ctx).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val surface = SurfaceView(ctx)
                        val fb = runCatching { FrontBufferInk(surface) }.getOrNull()
                        if (fb != null) {
                            addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                            ink.frontBuffer = fb
                        }
                    }
                    addView(ink, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    tag = ink
                }
            },
            onRelease = { root ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (root.tag as? InkView)?.frontBuffer?.release()
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Canvas" },
            update = { root ->
                val view = root.tag as InkView
                view.chromeColour = primary
                view.frameColour = frame
                // Read everything the drawing depends on so any change repaints it.
                ui.hashCode() + prefs.hashCode() + loads.hashCode()
                view.invalidate()
            },
        )
        if (empty && !ui.playing) {
            Text(
                "Draw on frame 1",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            )
        }
        if (ui.eyedropArmed) {
            Hint("Tap the canvas to pick a colour", Modifier.align(Alignment.TopCenter))
        }
        if (ui.selectionActive) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = ControlShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                    Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietButton("Delete selection", vm::deleteSelection)
                        PrimaryButton("Place", vm::placeSelection)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Surface(modifier.padding(12.dp), shape = ControlShape, color = MaterialTheme.colorScheme.inverseSurface) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** Loading: the canvas outline and empty cells in the editor's own shape, no spinner. */
@Composable
private fun LoadingEditor(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = "Opening loop" }) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ControlShape))
        }
        repeat(2) {
            Row(Modifier.fillMaxWidth().height(RowHeight).padding(start = 104.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(6) {
                    Box(Modifier.padding(horizontal = 2.dp).size(40.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, com.mohdshayan.mutoscope.ui.theme.CellShape))
                }
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun EditorTopBar(
    name: String,
    playing: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPlay: () -> Unit,
    overflow: Boolean,
    onOverflow: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onProjectSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to loops", onBack)
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        GlyphButton(Icons.AutoMirrored.Rounded.Undo, "Undo", onUndo, enabled = canUndo)
        GlyphButton(Icons.AutoMirrored.Rounded.Redo, "Redo", onRedo, enabled = canRedo)
        IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Box {
            GlyphButton(Icons.Rounded.MoreVert, "More options", { onOverflow(true) })
            DropdownMenu(expanded = overflow, onDismissRequest = { onOverflow(false) }) {
                DropdownMenuItem(text = { Text("Preview") }, onClick = { onOverflow(false); onPreview() })
                DropdownMenuItem(text = { Text("Export") }, onClick = { onOverflow(false); onExport() })
                DropdownMenuItem(text = { Text("Project settings") }, onClick = { onOverflow(false); onProjectSettings() })
            }
        }
    }
}

private enum class ToolButton { BRUSH, ERASER, FILL, LASSO, COLOUR, ONION }

private fun handleTool(b: ToolButton, prefs: EditorPrefs, vm: EditorViewModel, open: (Sheet) -> Unit) {
    val isBrush = prefs.tool == Tool.PENCIL || prefs.tool == Tool.INK || prefs.tool == Tool.MARKER
    when (b) {
        ToolButton.BRUSH -> if (isBrush) open(Sheet.BRUSH) else vm.setTool(prefs.brush)
        ToolButton.ERASER -> if (prefs.tool == Tool.ERASER) open(Sheet.ERASER) else vm.setTool(Tool.ERASER)
        ToolButton.FILL -> if (prefs.tool == Tool.FILL) open(Sheet.FILL) else vm.setTool(Tool.FILL)
        ToolButton.LASSO -> if (prefs.tool != Tool.LASSO) vm.setTool(Tool.LASSO) else vm.say("Draw around what you want to move, then drag it.")
        ToolButton.COLOUR -> open(Sheet.COLOUR)
        ToolButton.ONION -> open(Sheet.ONION)
    }
}

@Composable
private fun ToolBar(prefs: EditorPrefs, vertical: Boolean, onTool: (ToolButton) -> Unit) {
    val isBrush = prefs.tool == Tool.PENCIL || prefs.tool == Tool.INK || prefs.tool == Tool.MARKER
    val brushGlyph = when (if (isBrush) prefs.tool else prefs.brush) {
        Tool.INK -> ToolGlyphs.Nib
        Tool.MARKER -> ToolGlyphs.Marker
        else -> ToolGlyphs.Pencil
    }
    val brushName = toolName(if (isBrush) prefs.tool else prefs.brush)
    val items: List<@Composable () -> Unit> = listOf(
        { ToolSlot(brushGlyph, brushName, isBrush) { onTool(ToolButton.BRUSH) } },
        { ToolSlot(ToolGlyphs.Eraser, "Eraser", prefs.tool == Tool.ERASER) { onTool(ToolButton.ERASER) } },
        { ToolSlot(ToolGlyphs.Fill, "Fill", prefs.tool == Tool.FILL) { onTool(ToolButton.FILL) } },
        { ToolSlot(ToolGlyphs.Lasso, "Lasso", prefs.tool == Tool.LASSO) { onTool(ToolButton.LASSO) } },
        {
            IconButton(onClick = { onTool(ToolButton.COLOUR) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Colour" }) {
                ColourChip(prefs.colour)
            }
        },
        { ToolSlot(ToolGlyphs.Onion, "Onion skin", false) { onTool(ToolButton.ONION) } },
    )
    if (vertical) {
        Column(
            Modifier.width(64.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { items.forEach { it() } }
    } else {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { items.forEach { it() } }
    }
}

@Composable
private fun ToolSlot(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(ControlShape)
            .background(bg, ControlShape)
            .semantics {
                selected = active
                role = Role.Button
            },
    ) {
        Icon(icon, contentDescription = label, tint = fg)
    }
}
