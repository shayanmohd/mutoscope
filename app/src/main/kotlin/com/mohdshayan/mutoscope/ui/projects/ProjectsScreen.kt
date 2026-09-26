package com.mohdshayan.mutoscope.ui.projects

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.mutoscope.data.ProjectTile
import com.mohdshayan.mutoscope.di.ServiceLocator
import com.mohdshayan.mutoscope.ui.components.EmptyState
import com.mohdshayan.mutoscope.ui.components.GlyphButton
import com.mohdshayan.mutoscope.ui.components.PlainTextButton
import com.mohdshayan.mutoscope.ui.components.PrimaryButton
import com.mohdshayan.mutoscope.ui.components.cycleLabel
import com.mohdshayan.mutoscope.ui.components.plural
import com.mohdshayan.mutoscope.ui.theme.ControlShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArchiveTypes = arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")

@Composable
fun ProjectsScreen(
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: ProjectsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val defaultFps by vm.defaultFps.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var overflow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri, restoring = true)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = exportFor
        exportFor = null
        if (uri != null && id != null) vm.exportTo(id, uri)
    }

    val tiles = (state as? ProjectsState.Ready)?.tiles.orEmpty()
    fun tile(id: Long?) = tiles.firstOrNull { it.project.id == id }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mutoscope", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    Box {
                        GlyphButton(Icons.Rounded.MoreVert, "More options", { overflow = true })
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Import a project file") },
                                onClick = {
                                    overflow = false
                                    importer.launch(ArchiveTypes)
                                },
                            )
                        }
                    }
                    GlyphButton(Icons.Rounded.Settings, "Settings", onSettings)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            if (tiles.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    PrimaryButton("New loop", { showNew = true }, Modifier.widthIn(min = 160.dp))
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val columns = when {
                maxWidth < 600.dp -> 2
                maxWidth < 840.dp -> 3
                else -> 4
            }
            when (val s = state) {
                ProjectsState.Loading -> SkeletonGrid(columns)
                ProjectsState.Failed -> EmptyState(
                    title = "Projects could not be opened",
                    body = "Restore a backup file to get them back.",
                    actionLabel = "Restore from backup",
                    onAction = { restorer.launch(ArchiveTypes) },
                    modifier = Modifier.fillMaxSize(),
                )
                is ProjectsState.Ready -> if (s.tiles.isEmpty()) {
                    EmptyState(
                        title = "Your loops live here",
                        body = "Draw a loop frame by frame. Every reel you add repeats on its own length.",
                        actionLabel = "New loop",
                        onAction = { showNew = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(s.tiles, key = { it.project.id }) { t ->
                            Box {
                                ProjectTileView(t, onClick = { onOpen(t.project.id) }, onLongClick = { menuFor = t.project.id })
                                DropdownMenu(expanded = menuFor == t.project.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renameFor = t.project.id })
                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuFor = null; vm.duplicate(t.project.id) })
                                    DropdownMenuItem(
                                        text = { Text("Export project") },
                                        onClick = {
                                            menuFor = null
                                            exportFor = t.project.id
                                            exporter.launch(ServiceLocator.archive.fileNameFor(t.project))
                                        },
                                    )
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; deleteFor = t.project.id })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewLoopSheet(
            suggestedName = "Loop ${tiles.count { !it.project.isSample } + 1}",
            defaultFps = defaultFps,
            onDismiss = { showNew = false },
            onCreate = { name, aspect, paper, fps ->
                showNew = false
                vm.create(name, aspect, paper, fps, onOpen)
            },
        )
    }

    tile(renameFor)?.let { t ->
        var name by rememberSaveable(t.project.id) { mutableStateOf(t.project.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Rename loop", style = MaterialTheme.typography.titleLarge) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Name") }, shape = ControlShape) },
            confirmButton = { PlainTextButton("Rename", { vm.rename(t.project.id, name); renameFor = null }) },
            dismissButton = { PlainTextButton("Cancel", { renameFor = null }) },
        )
    }

    tile(deleteFor)?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete ${t.project.name}?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Its frames are removed from this phone. A backup file you saved keeps its own copy.") },
            confirmButton = { PlainTextButton("Delete loop", { vm.delete(t.project.id, t.project.name); deleteFor = null }) },
            dismissButton = { PlainTextButton("Keep it", { deleteFor = null }) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTileView(t: ProjectTile, onClick: () -> Unit, onLongClick: () -> Unit) {
    val path = t.project.thumbnailPath
    val thumb by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } }
    }
    val detail = "${cycleLabel(t.cycle)}, ${plural(t.reelCount, "reel", "reels")}"
    Column(
        Modifier
            .clip(ControlShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Project actions")
            .semantics(mergeDescendants = true) { contentDescription = "${t.project.name}. $detail" },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ControlShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ControlShape),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Loading: tile outlines in Lead at 30 percent, in the grid's own shape. */
@Composable
private fun SkeletonGrid(columns: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled = false,
        modifier = Modifier.semantics { contentDescription = "Opening projects" },
    ) {
        items(columns * 2) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ControlShape))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(96.dp).height(14.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ControlShape))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(140.dp).height(12.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ControlShape))
            }
        }
    }
}
