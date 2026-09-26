package com.mohdshayan.mutoscope.ui.preview

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.di.ServiceLocator
import com.mohdshayan.mutoscope.ink.CelCompositor
import com.mohdshayan.mutoscope.ink.Scene
import com.mohdshayan.mutoscope.ui.components.EmptyState
import com.mohdshayan.mutoscope.ui.components.GlyphButton
import com.mohdshayan.mutoscope.ui.nav.Preview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class PreviewUi(
    val doc: ProjectDoc? = null,
    val missing: Boolean = false,
    val tick: Long = 0,
    val playing: Boolean = true,
) {
    val empty: Boolean get() = doc?.reels?.filter { !it.hidden }?.all { r -> r.cels.all { it.file == null } } ?: false
}

class PreviewViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val projectId = handle.toRoute<Preview>().projectId
    private val store = ServiceLocator.celStore
    private val _ui = MutableStateFlow(PreviewUi())
    val ui: StateFlow<PreviewUi> = _ui
    val loads = store.loads
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val doc = ServiceLocator.projects.load(projectId)
            _ui.update { it.copy(doc = doc, missing = doc == null) }
            if (doc != null) play()
        }
    }

    fun bitmap(reelFile: String?) = reelFile?.let { store.peek(projectId, it, true) }

    private fun play() {
        job?.cancel()
        _ui.update { it.copy(playing = true) }
        job = viewModelScope.launch {
            while (true) {
                val u = _ui.value
                val doc = u.doc ?: break
                delay(1000L / doc.project.fps)
                val cycle = doc.cycle
                _ui.update { it.copy(tick = if (cycle == LoopClock.CYCLE_OVERFLOW) it.tick + 1 else (it.tick + 1) % cycle) }
            }
        }
    }

    fun toggle() {
        if (_ui.value.playing) {
            job?.cancel()
            _ui.update { it.copy(playing = false) }
        } else {
            play()
        }
    }

    fun scrubBy(frames: Long, from: Long) {
        job?.cancel()
        _ui.update { it.copy(playing = false, tick = (from + frames).coerceAtLeast(0)) }
    }
}

@Composable
fun PreviewScreen(onBack: () -> Unit, vm: PreviewViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val loads by vm.loads.collectAsStateWithLifecycle()
    val compositor = androidx.compose.runtime.remember { CelCompositor() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val doc = ui.doc
        when {
            ui.missing -> EmptyState("This loop is gone", "It may have been deleted.", Modifier.fillMaxSize(), "Back to drawing", onBack)
            doc != null && ui.empty -> EmptyState(
                "Nothing to play yet",
                "Draw on a frame or two, then come back to watch the loop.",
                Modifier.fillMaxSize(),
                "Back to drawing",
                onBack,
            )
            doc != null -> {
                var dragStart = 0L
                var acc = 0f
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .semantics { contentDescription = if (ui.playing) "Loop playing. Tap to pause." else "Loop paused on frame ${ui.tick + 1}. Tap to play." }
                        .pointerInput(Unit) { detectTapGestures { vm.toggle() } }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragStart = vm.ui.value.tick; acc = 0f },
                            ) { change, dx ->
                                change.consume()
                                acc += dx
                                vm.scrubBy((acc / 24.dp.toPx()).roundToLong(), dragStart)
                            }
                        },
                ) {
                    loads.hashCode()
                    val p = doc.project
                    val s = minOf(size.width / p.widthPx, size.height / p.heightPx)
                    val left = (size.width - p.widthPx * s) / 2f
                    val top = (size.height - p.heightPx * s) / 2f
                    drawIntoCanvas { c ->
                        val n = c.nativeCanvas
                        n.save()
                        n.translate(left, top)
                        n.scale(s, s)
                        n.clipRect(0f, 0f, p.widthPx.toFloat(), p.heightPx.toFloat())
                        compositor.draw(n, Scene(doc, ui.tick, forExport = true, proxies = true)) { _, cel, _ -> vm.bitmap(cel.file) }
                        n.restore()
                    }
                }
                if (!ui.playing) {
                    Text(
                        "Frame ${ui.tick % maxOf(1L, doc.cycle) + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            GlyphButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to drawing", onBack)
        }
    }
}
