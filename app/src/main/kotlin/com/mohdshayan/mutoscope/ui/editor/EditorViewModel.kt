package com.mohdshayan.mutoscope.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Path
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.core.paint.FloodFill
import com.mohdshayan.mutoscope.core.undo.UndoModel
import com.mohdshayan.mutoscope.data.ReferenceDecoder
import com.mohdshayan.mutoscope.data.model.CelDoc
import com.mohdshayan.mutoscope.data.model.Ids
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.data.model.ReelDoc
import com.mohdshayan.mutoscope.data.model.ReelKind
import com.mohdshayan.mutoscope.data.prefs.EditorPrefs
import com.mohdshayan.mutoscope.data.prefs.OnionTint
import com.mohdshayan.mutoscope.data.prefs.Tool
import com.mohdshayan.mutoscope.di.ServiceLocator
import com.mohdshayan.mutoscope.ink.Blocked
import com.mohdshayan.mutoscope.ink.Brushes
import com.mohdshayan.mutoscope.ink.FloatingSelection
import com.mohdshayan.mutoscope.ink.InkController
import com.mohdshayan.mutoscope.ink.InkHost
import com.mohdshayan.mutoscope.ink.LiveStroke
import com.mohdshayan.mutoscope.ink.OnionSpec
import com.mohdshayan.mutoscope.ink.Scene
import com.mohdshayan.mutoscope.ui.nav.Editor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val doc: ProjectDoc? = null,
    val tick: Long = 0L,
    val activeReelId: Long? = null,
    val playing: Boolean = false,
    val scrubbing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val workingReady: Boolean = false,
    val replacedCels: Set<Long> = emptySet(),
    val selectionActive: Boolean = false,
    val eyedropArmed: Boolean = false,
) {
    val activeReel: ReelDoc? get() = doc?.reels?.firstOrNull { it.id == activeReelId }
    val cycle: Long get() = doc?.cycle ?: 1L
}

class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app), InkHost {

    val projectId: Long = handle.toRoute<Editor>().projectId

    private val repo = ServiceLocator.projects
    private val store = ServiceLocator.celStore
    private val prefs = ServiceLocator.appPrefs

    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    val editorPrefs: StateFlow<EditorPrefs> = prefs.editorPrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorPrefs())

    /** Repaint signal for the canvas: bitmaps arriving from the cel store. */
    val loads: StateFlow<Long> = store.loads

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val ink = InkController(this)
    private val brushes = Brushes()
    private val undo = UndoModel<List<ReelDoc>>(100)

    private var working: Bitmap? = null
    private var workingKey: Pair<Long, String?>? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var thumbJob: Job? = null
    private var playJob: Job? = null
    private var ghostTintBefore = 0
    private var ghostTintAfter = 0

    init {
        viewModelScope.launch {
            val doc = runCatching { repo.load(projectId) }.getOrNull()
            if (doc == null) {
                _ui.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            repo.collectGarbage(projectId, emptySet())
            val active = doc.drawnReels.lastOrNull() ?: doc.reels.last()
            _ui.update { it.copy(loading = false, doc = doc, activeReelId = active.id) }
            ensureWorking()
        }
        viewModelScope.launch {
            editorPrefs.collect { p -> applyPrefs(p) }
        }
    }

    private fun applyPrefs(p: EditorPrefs) {
        ink.tool = p.tool
        ink.colour = p.colour
        ink.size = p.sizeOf(p.tool)
        ink.opacity = p.brushOpacity
        ink.streamline = p.streamline
        ink.invalidate()
    }

    /** Ghost tints come from the theme, so they are set by the screen for the current mode. */
    fun setGhostColours(before: Int, after: Int, orchid: Int, graphite: Int) {
        val tint = editorPrefs.value.onionTint
        ghostTintBefore = when (tint) {
            OnionTint.LIGHT_TABLE -> before
            OnionTint.ORCHID -> orchid
            OnionTint.GRAPHITE -> graphite
        }
        ghostTintAfter = when (tint) {
            OnionTint.LIGHT_TABLE -> after
            OnionTint.ORCHID -> orchid
            OnionTint.GRAPHITE -> graphite
        }
    }

    // ---- First run --------------------------------------------------------------------------

    /** The sample's one play-through on first open. Waits for Play under reduced motion. */
    fun onOpened(reducedMotion: Boolean) {
        viewModelScope.launch {
            while (_ui.value.loading) delay(30)
            val doc = _ui.value.doc ?: return@launch
            if (!doc.project.isSample || reducedMotion) return@launch
            if (prefs.samplePlayed.first()) return@launch
            prefs.setSamplePlayed()
            delay(400)
            play(oneCycle = true)
        }
    }

    // ---- InkHost ----------------------------------------------------------------------------

    override fun scene(): Scene? {
        val u = _ui.value
        val doc = u.doc ?: return null
        val p = editorPrefs.value
        val moving = u.playing || u.scrubbing
        return Scene(
            doc = doc,
            tick = u.tick,
            activeReelId = u.activeReelId,
            working = if (moving || !u.workingReady) null else working,
            live = if (ink.frontBufferActive) null else ink.live,
            onion = if (moving) null else OnionSpec(p.onionBefore, p.onionAfter, ghostTintBefore, ghostTintAfter, p.onionOpacity),
            lightTable = p.lightTable && !moving,
            proxies = moving,
            floating = ink.floating,
        )
    }

    override fun bitmapFor(reel: ReelDoc, cel: CelDoc, half: Boolean): Bitmap? {
        val f = cel.file ?: return null
        return store.peek(projectId, f, half)
    }

    override fun blocked(): Blocked? {
        val u = _ui.value
        val reel = u.activeReel ?: return Blocked.LOADING
        return when {
            u.playing -> Blocked.PLAYING
            reel.isReference -> Blocked.REFERENCE
            reel.hidden -> Blocked.HIDDEN
            reel.locked -> Blocked.LOCKED
            !u.workingReady -> Blocked.LOADING
            else -> null
        }
    }

    override fun onBlockedTouch(reason: Blocked) {
        val name = _ui.value.activeReel?.name ?: "This reel"
        when (reason) {
            Blocked.LOCKED -> say("$name is locked. Unlock it in the reel sheet to draw.")
            Blocked.HIDDEN -> say("$name is hidden. Show it in the reel sheet to draw.")
            Blocked.REFERENCE -> say("The reference photo is locked. Pick a drawn reel to draw.")
            Blocked.PLAYING -> pause()
            Blocked.LOADING -> Unit
        }
    }

    override fun onStrokeFinished(stroke: LiveStroke) {
        val w = working ?: return
        brushes.commit(w, stroke)
        commitWorking()
    }

    override fun onFill(x: Float, y: Float) {
        val w = working ?: return
        val p = editorPrefs.value
        val colour = ink.colour or 0xFF000000.toInt()
        val alpha = (p.brushOpacity * 255).toInt().coerceIn(1, 255)
        val argb = (alpha shl 24) or (colour and 0xFFFFFF)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                val px = IntArray(w.width * w.height)
                w.getPixels(px, 0, w.width, 0, 0, w.width, w.height)
                val mask = FloodFill.mask(px, w.width, w.height, x.toInt(), y.toInt(), p.fillTolerance, if (p.fillCloseGaps) 2 else 0)
                if (mask.none { it }) return@withContext false
                FloodFill.apply(px, w.width, w.height, mask, argb)
                w.setPixels(px, 0, w.width, 0, 0, w.width, w.height)
                true
            }
            if (ok) commitWorking()
        }
    }

    override fun onEyedrop(x: Float, y: Float) {
        _ui.update { it.copy(eyedropArmed = false) }
        val u = _ui.value
        val doc = u.doc ?: return
        val xi = x.toInt()
        val yi = y.toInt()
        if (xi !in 0 until doc.project.widthPx || yi !in 0 until doc.project.heightPx) return
        // The in-memory working cel belongs to the active drawn reel only once it has loaded.
        val active = if (u.workingReady) working else null
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) {
                for (reel in doc.reels.asReversed()) {
                    if (reel.hidden) continue
                    // A cel evicted from the cache is read from disk, so the pick never falls
                    // through to the paper just because playback pushed that frame out of memory.
                    val bmp = (if (reel.id == u.activeReelId && !reel.isReference) active else null)
                        ?: reel.celAt(u.tick).file?.let { f -> store.peek(projectId, f, false) ?: runCatching { store.decode(projectId, f, 2) }.getOrNull() }
                        ?: continue
                    val sx = xi * bmp.width / doc.project.widthPx
                    val sy = yi * bmp.height / doc.project.heightPx
                    val c = bmp.getPixel(sx.coerceIn(0, bmp.width - 1), sy.coerceIn(0, bmp.height - 1))
                    if ((c ushr 24) > 40) return@withContext c or 0xFF000000.toInt()
                }
                null
            }
            setColour(picked ?: doc.project.paperArgb)
            say("Picked a colour")
        }
    }

    override fun onLasso(path: Path) {
        val w = working ?: return
        val sel = FloatingSelection.lift(w, path) ?: return
        ink.floating = sel
        _ui.update { it.copy(selectionActive = true) }
        ink.invalidate()
    }

    override fun onSelectionMoved() = Unit

    override fun onUndoGesture() = undo()
    override fun onRedoGesture() = redo()

    // ---- Working cel ------------------------------------------------------------------------

    private fun activeCel(u: EditorUi = _ui.value): CelDoc? {
        val reel = u.activeReel ?: return null
        if (reel.isReference) return null
        return reel.celAt(u.tick)
    }

    /** Loads the active cel at full size when it changed and nothing is moving. */
    private fun ensureWorking() {
        val u = _ui.value
        if (u.playing || u.scrubbing) return
        val cel = activeCel(u)
        if (cel == null) {
            _ui.update { it.copy(workingReady = false) }
            return
        }
        val key = cel.id to cel.file
        if (key == workingKey && working != null) {
            if (!u.workingReady) _ui.update { it.copy(workingReady = true) }
            return
        }
        placeSelectionIfAny()
        val doc = u.doc ?: return
        loadJob?.cancel()
        _ui.update { it.copy(workingReady = false) }
        loadJob = viewModelScope.launch {
            val w = doc.project.widthPx
            val h = doc.project.heightPx
            val file = cel.file
            val result = withContext(Dispatchers.IO) {
                if (file == null) {
                    Result.success(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
                } else {
                    runCatching {
                        val bmp = store.readMutable(projectId, file) ?: error("unreadable")
                        if (bmp.width == w && bmp.height == h) {
                            bmp
                        } else {
                            Bitmap.createScaledBitmap(bmp, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
                        }
                    }
                }
            }
            val bmp = result.getOrNull()
            if (bmp == null) {
                replaceUnreadable(cel)
                return@launch
            }
            working = bmp
            workingKey = key
            _ui.update { it.copy(workingReady = true) }
            ink.invalidate()
        }
    }

    /** A cel that fails to decode becomes blank, and its cell is hatched so the loss is visible. */
    private fun replaceUnreadable(cel: CelDoc) {
        val u = _ui.value
        val doc = u.doc ?: return
        val reel = doc.reels.firstOrNull { r -> r.cels.any { it.id == cel.id } } ?: return
        val frame = reel.cels.indexOfFirst { it.id == cel.id } + 1
        val reels = doc.reels.map { r ->
            if (r.id != reel.id) r else r.copy(cels = r.cels.map { if (it.id == cel.id) it.copy(file = null) else it })
        }
        _ui.update { it.copy(doc = doc.copy(reels = reels), replacedCels = it.replacedCels + cel.id) }
        scheduleSave()
        say("Frame $frame on ${reel.name} could not be read and was replaced with a blank frame.")
        ensureWorking()
    }

    /** Writes the working bitmap as a new file for the active cel, as one undo step. */
    private fun commitWorking() {
        val w = working ?: return
        val cel = activeCel() ?: return
        val reelId = _ui.value.activeReelId ?: return
        val name = store.newName(cel.id)
        store.write(projectId, name, w.copy(Bitmap.Config.ARGB_8888, false))
        workingKey = cel.id to name
        mutate { reels ->
            reels.map { r ->
                if (r.id != reelId) r else r.copy(cels = r.cels.map { if (it.id == cel.id) it.copy(file = name) else it })
            }
        }
        viewModelScope.launch { prefs.recordDrawingDay() }
    }

    // ---- Undo and saving --------------------------------------------------------------------

    private fun mutate(change: (List<ReelDoc>) -> List<ReelDoc>) {
        val u = _ui.value
        val doc = u.doc ?: return
        val next = change(doc.reels)
        if (next == doc.reels) return
        undo.push(doc.reels)
        setReels(next)
    }

    private fun setReels(reels: List<ReelDoc>) {
        _ui.update { u ->
            val doc = u.doc ?: return@update u
            val active = reels.firstOrNull { it.id == u.activeReelId }?.id
                ?: reels.lastOrNull { !it.isReference }?.id ?: reels.lastOrNull()?.id
            u.copy(doc = doc.copy(reels = reels), activeReelId = active, canUndo = undo.canUndo, canRedo = undo.canRedo)
        }
        scheduleSave()
        ensureWorking()
        ink.invalidate()
    }

    fun undo() {
        if (ink.floating != null) {
            // Put the lifted pixels back by reloading the untouched file.
            ink.floating = null
            workingKey = null
            _ui.update { it.copy(selectionActive = false) }
            ensureWorking()
            return
        }
        val doc = _ui.value.doc ?: return
        val prev = undo.undo(doc.reels) ?: return
        setReels(prev)
    }

    fun redo() {
        val doc = _ui.value.doc ?: return
        val next = undo.redo(doc.reels) ?: return
        setReels(next)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            saveNow()
        }
        // The Projects tile follows the work too, so a killed app still shows the latest frame.
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch {
            delay(2_000)
            withContext(NonCancellable + Dispatchers.IO) {
                store.flush()
                repo.load(projectId)?.let { repo.writeThumbnail(it) }
            }
        }
    }

    suspend fun saveNow() {
        val doc = _ui.value.doc ?: return
        withContext(NonCancellable) { repo.saveStructure(projectId, doc.reels) }
    }

    override fun onCleared() {
        placeSelectionIfAny()
        val doc = _ui.value.doc
        playJob?.cancel()
        if (doc != null) repo.saveOnClose(projectId, doc.reels)
        super.onCleared()
    }

    // ---- Playhead ---------------------------------------------------------------------------

    fun play(oneCycle: Boolean = false) {
        if (_ui.value.playing) return
        placeSelectionIfAny()
        _ui.update { it.copy(playing = true) }
        playJob = viewModelScope.launch {
            var played = 0L
            while (true) {
                val u = _ui.value
                val fps = u.doc?.project?.fps ?: 12
                delay(1000L / fps)
                val cycle = u.cycle
                val next = if (cycle == LoopClock.CYCLE_OVERFLOW) u.tick + 1 else (u.tick + 1) % cycle
                played++
                if (oneCycle && played >= cycle) {
                    _ui.update { it.copy(tick = 0L) }
                    break
                }
                _ui.update { it.copy(tick = next) }
            }
            pause()
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        _ui.update { it.copy(playing = false) }
        ensureWorking()
    }

    fun togglePlay() = if (_ui.value.playing) pause() else play()

    fun scrubStart() {
        placeSelectionIfAny()
        if (_ui.value.playing) pause()
        _ui.update { it.copy(scrubbing = true) }
    }

    fun scrubTo(tick: Long) {
        _ui.update { it.copy(tick = tick.coerceAtLeast(0)) }
    }

    fun scrubEnd() {
        _ui.update { it.copy(scrubbing = false) }
        ensureWorking()
    }

    /** Tapping a cell selects that reel and moves the playhead to that column. */
    fun selectCell(reelId: Long, tick: Long) {
        placeSelectionIfAny()
        if (_ui.value.playing) pause()
        _ui.update { it.copy(activeReelId = reelId, tick = tick.coerceAtLeast(0)) }
        ensureWorking()
        ink.invalidate()
    }

    fun selectReel(reelId: Long) {
        placeSelectionIfAny()
        _ui.update { it.copy(activeReelId = reelId) }
        ensureWorking()
    }

    // ---- Tools ------------------------------------------------------------------------------

    fun setTool(tool: Tool) {
        if (tool != Tool.LASSO) placeSelectionIfAny()
        ink.tool = tool
        ink.size = editorPrefs.value.sizeOf(tool)
        viewModelScope.launch { prefs.setTool(tool) }
    }

    fun setBrushSize(tool: Tool, size: Float) {
        if (ink.tool == tool) ink.size = size
        viewModelScope.launch { prefs.setBrushSize(tool, size) }
    }

    fun setOpacity(v: Float) {
        ink.opacity = v
        viewModelScope.launch { prefs.setBrushOpacity(v) }
    }

    fun setStreamline(v: Int) {
        ink.streamline = v
        viewModelScope.launch { prefs.setStreamline(v) }
    }

    fun setColour(argb: Int) {
        ink.colour = argb
        viewModelScope.launch { prefs.setColour(argb) }
    }

    fun armEyedropper() {
        ink.eyedropArmed = true
        _ui.update { it.copy(eyedropArmed = true) }
    }

    fun setFill(tolerance: Int, closeGaps: Boolean) {
        viewModelScope.launch { prefs.setFill(tolerance, closeGaps) }
    }

    fun setOnion(before: Int, after: Int, tint: OnionTint, opacity: Float, lightTable: Boolean) {
        viewModelScope.launch { prefs.setOnion(before, after, tint, opacity, lightTable) }
    }

    fun placeSelection() {
        placeSelectionIfAny()
    }

    private fun placeSelectionIfAny() {
        val sel = ink.floating ?: return
        val w = working ?: return
        sel.placeInto(w)
        ink.floating = null
        _ui.update { it.copy(selectionActive = false) }
        commitWorking()
    }

    fun deleteSelection() {
        if (ink.floating == null) return
        ink.floating = null
        _ui.update { it.copy(selectionActive = false) }
        commitWorking()
    }

    // ---- Frames -----------------------------------------------------------------------------

    private fun editActive(change: (ReelDoc, Int) -> Pair<ReelDoc, Int?>) {
        placeSelectionIfAny()
        val u = _ui.value
        val reel = u.activeReel ?: return
        if (reel.isReference) return
        val idx = reel.celIndexAt(u.tick)
        val (next, focus) = change(reel, idx)
        mutate { reels -> reels.map { if (it.id == reel.id) next else it } }
        if (focus != null) {
            val t = LoopClock.firstTickOf(focus, next.hold, next.phaseOffset, next.length)
            _ui.update { it.copy(tick = t) }
            ensureWorking()
        }
    }

    fun addFrameAfter() {
        val reel = _ui.value.activeReel ?: return
        if (reel.length >= MAX_FRAMES) return say("A reel holds up to $MAX_FRAMES frames.")
        editActive { r, i ->
            val cels = r.cels.toMutableList().apply { add(i + 1, CelDoc(Ids.next(), null)) }
            r.copy(cels = cels) to i + 1
        }
    }

    fun duplicateFrame() {
        val reel = _ui.value.activeReel ?: return
        if (reel.length >= MAX_FRAMES) return say("A reel holds up to $MAX_FRAMES frames.")
        editActive { r, i ->
            val cels = r.cels.toMutableList().apply { add(i + 1, CelDoc(Ids.next(), r.cels[i].file)) }
            r.copy(cels = cels) to i + 1
        }
    }

    fun clearFrame() = editActive { r, i ->
        r.copy(cels = r.cels.mapIndexed { j, c -> if (j == i) c.copy(file = null) else c }) to i
    }

    fun deleteFrame() {
        val reel = _ui.value.activeReel ?: return
        if (reel.length <= 1) return clearFrame()
        editActive { r, i ->
            val cels = r.cels.toMutableList().apply { removeAt(i) }
            r.copy(cels = cels) to (i - 1).coerceAtLeast(0).coerceAtMost(cels.size - 1)
        }
    }

    fun moveFrame(by: Int) = editActive { r, i ->
        val j = (i + by).coerceIn(0, r.length - 1)
        if (j == i) return@editActive r to i
        val cels = r.cels.toMutableList()
        val c = cels.removeAt(i)
        cels.add(j, c)
        r.copy(cels = cels) to j
    }

    // ---- Reels ------------------------------------------------------------------------------

    fun addReel() {
        placeSelectionIfAny()
        val u = _ui.value
        val doc = u.doc ?: return
        val n = doc.drawnReels.size + 1
        var name = "Reel $n"
        var k = n
        while (doc.reels.any { it.name == name }) name = "Reel ${++k}"
        val reel = ReelDoc(id = Ids.next(), name = name, cels = listOf(CelDoc(Ids.next(), null)))
        val at = (doc.reels.indexOfFirst { it.id == u.activeReelId } + 1).coerceIn(0, doc.reels.size)
        mutate { reels -> reels.toMutableList().apply { add(at, reel) } }
        _ui.update { it.copy(activeReelId = reel.id) }
        ensureWorking()
    }

    fun updateReel(reelId: Long, change: (ReelDoc) -> ReelDoc) {
        mutate { reels -> reels.map { if (it.id == reelId) change(it) else it } }
    }

    fun setReelLength(reelId: Long, length: Int) {
        val n = length.coerceIn(1, MAX_FRAMES)
        updateReel(reelId) { r ->
            when {
                n > r.length -> r.copy(cels = r.cels + List(n - r.length) { CelDoc(Ids.next(), null) })
                n < r.length -> r.copy(cels = r.cels.take(n), phaseOffset = r.phaseOffset.coerceAtMost(n - 1))
                else -> r
            }
        }
    }

    fun duplicateReel(reelId: Long) {
        val doc = _ui.value.doc ?: return
        val src = doc.reels.firstOrNull { it.id == reelId } ?: return
        val copy = src.copy(id = Ids.next(), name = "${src.name} copy", cels = src.cels.map { CelDoc(Ids.next(), it.file) })
        val at = doc.reels.indexOf(src) + 1
        mutate { reels -> reels.toMutableList().apply { add(at, copy) } }
        _ui.update { it.copy(activeReelId = copy.id) }
        ensureWorking()
    }

    fun deleteReel(reelId: Long) {
        val doc = _ui.value.doc ?: return
        val reel = doc.reels.firstOrNull { it.id == reelId } ?: return
        if (!reel.isReference && doc.drawnReels.size <= 1) return say("A loop keeps at least one reel.")
        mutate { reels -> reels.filter { it.id != reelId } }
        say("Deleted ${reel.name}")
    }

    /** Moves a reel up (toward the viewer) or down in the stack. The reference stays at the bottom. */
    fun moveReel(reelId: Long, up: Boolean) {
        mutate { reels ->
            val i = reels.indexOfFirst { it.id == reelId }
            val j = if (up) i + 1 else i - 1
            if (i < 0 || j !in reels.indices || reels[j].isReference || reels[i].isReference) return@mutate reels
            reels.toMutableList().apply { add(j, removeAt(i)) }
        }
    }

    // ---- Project settings -------------------------------------------------------------------

    fun setFps(fps: Int) = updateProject { it.copy(fps = fps.coerceIn(4, 30)) }
    fun setPaper(argb: Int) = updateProject { it.copy(paperArgb = argb) }
    fun rename(name: String) = updateProject { it.copy(name = name.trim().ifEmpty { it.name }) }

    private fun updateProject(change: (com.mohdshayan.mutoscope.data.db.ProjectEntity) -> com.mohdshayan.mutoscope.data.db.ProjectEntity) {
        val doc = _ui.value.doc ?: return
        _ui.update { it.copy(doc = doc.copy(project = change(doc.project))) }
        viewModelScope.launch { repo.updateProject(projectId, change) }
        ink.invalidate()
    }

    fun importReference(uri: Uri) {
        val doc = _ui.value.doc ?: return
        viewModelScope.launch {
            val bmp = ReferenceDecoder.decode(getApplication(), uri, doc.project.widthPx, doc.project.heightPx)
            if (bmp == null) {
                say("That photo could not be opened. Try a JPEG or PNG under 50 megapixels.")
                return@launch
            }
            val celId = Ids.next()
            val name = store.newName(celId)
            store.write(projectId, name, bmp)
            mutate { reels ->
                val existing = reels.firstOrNull { it.isReference }
                val ref = existing?.copy(cels = listOf(CelDoc(celId, name)))
                    ?: ReelDoc(
                        id = Ids.next(),
                        name = "Reference",
                        opacity = 0.5f,
                        locked = true,
                        kind = ReelKind.REFERENCE,
                        includeInExport = false,
                        cels = listOf(CelDoc(celId, name)),
                    )
                listOf(ref) + reels.filter { !it.isReference }
            }
            say("Reference photo added")
        }
    }

    fun removeReference() {
        val ref = _ui.value.doc?.reference ?: return
        mutate { reels -> reels.filter { it.id != ref.id } }
        say("Reference photo removed")
    }

    fun say(text: String) {
        messageChannel.trySend(text)
    }

    companion object {
        const val MAX_FRAMES = 240
    }
}
