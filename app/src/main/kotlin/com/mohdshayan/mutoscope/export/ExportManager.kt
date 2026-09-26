package com.mohdshayan.mutoscope.export

import android.content.Context
import com.mohdshayan.mutoscope.core.loop.ExportMath
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.data.ProjectRepository
import com.mohdshayan.mutoscope.data.cels.CelStore
import com.mohdshayan.mutoscope.data.prefs.AppPrefs
import com.mohdshayan.mutoscope.data.prefs.ExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportState {
    data object Idle : ExportState

    /** [pass] 1 reads colours (GIF only); pass 2 writes. MP4 has one pass, reported as 2. */
    data class Running(val format: ExportFormat, val pass: Int, val frame: Int, val total: Int, val passes: Int) : ExportState

    data class Done(
        val projectId: Long,
        val format: ExportFormat,
        val file: File,
        val displayName: String,
        val unreadableFrames: Int,
        val askForReview: Boolean,
    ) : ExportState

    data class Failed(val projectId: Long, val format: ExportFormat, val message: String, val retryAt: Int?) : ExportState
}

/**
 * Application-scoped, so an export keeps running through rotation and its progress is still
 * there when the editor comes back. One export at a time.
 */
class ExportManager(
    private val context: Context,
    private val projects: ProjectRepository,
    private val store: CelStore,
    private val prefs: AppPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state
    private var job: Job? = null

    val saver = MediaSaver(context)

    /**
     * Starts an export. A GIF with [seconds] null runs one full cycle; otherwise the loop plays
     * for that many seconds.
     */
    fun start(projectId: Long, format: ExportFormat, longEdge: Int, seconds: Int?) {
        if (_state.value is ExportState.Running) return
        job = scope.launch {
            try {
                store.flush()
                val doc = projects.load(projectId) ?: throw IllegalStateException("gone")
                val p = doc.project
                val cycle = doc.cycle
                val frames = if (seconds == null && !LoopClock.isOverCap(cycle)) cycle.toInt() else ExportMath.framesForSeconds(seconds ?: 6, p.fps)
                val (ow, oh) = ExportMath.outputSize(p.widthPx, p.heightPx, longEdge)
                val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val base = p.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "loop" }.replace(' ', '-')
                val ext = if (format == ExportFormat.GIF) "gif" else "mp4"
                val displayName = "$base-$stamp.$ext"
                val file = File(dir, displayName)
                val unreadable: Int
                when (format) {
                    ExportFormat.GIF -> {
                        _state.value = ExportState.Running(format, 1, 0, frames, 2)
                        val r = FrameRenderer(doc, store, ow, oh)
                        GifWriter.write(file, r, frames, p.fps) { pass, frame ->
                            _state.value = ExportState.Running(format, pass, frame, frames, 2)
                        }
                        unreadable = r.unreadable.size
                    }
                    ExportFormat.MP4 -> {
                        val size = Mp4Writer.supportedSize(ow, oh) ?: throw EncoderRefusedException(longEdge)
                        _state.value = ExportState.Running(format, 2, 0, frames, 1)
                        val r = FrameRenderer(doc, store, size.first, size.second)
                        Mp4Writer.write(file, size.first, size.second, p.fps, frames, longEdge, r::render) { frame ->
                            _state.value = ExportState.Running(format, 2, frame, frames, 1)
                        }
                        unreadable = r.unreadable.size
                    }
                }
                val total = prefs.recordExport()
                val ask = total >= 3 && !prefs.reviewAsked()
                _state.value = ExportState.Done(projectId, format, file, displayName, unreadable, ask)
            } catch (e: CancellationException) {
                _state.value = ExportState.Idle
                throw e
            } catch (e: EncoderRefusedException) {
                _state.value = ExportState.Failed(
                    projectId, format,
                    "MP4 export stopped: this phone's video encoder refused ${e.longEdge} px.",
                    retryAt = if (e.longEdge > 720) 720 else if (e.longEdge > 512) 512 else null,
                )
            } catch (e: OutOfMemoryError) {
                _state.value = ExportState.Failed(projectId, format, "${format.name} export stopped: this phone ran out of memory at $longEdge px.", retryAt = if (longEdge > 512) 512 else null)
            } catch (e: Exception) {
                _state.value = ExportState.Failed(projectId, format, "${format.name} export stopped: the file could not be written. Free some space and try again.", retryAt = null)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ExportState.Idle
    }

    fun reset() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }
}
