package com.mohdshayan.mutoscope.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.data.prefs.ExportFormat
import com.mohdshayan.mutoscope.di.ServiceLocator
import com.mohdshayan.mutoscope.export.ExportState
import com.mohdshayan.mutoscope.ui.components.AppSheet
import com.mohdshayan.mutoscope.ui.components.ChoiceRow
import com.mohdshayan.mutoscope.ui.components.PrimaryButton
import com.mohdshayan.mutoscope.ui.components.QuietButton
import com.mohdshayan.mutoscope.ui.components.plural
import kotlinx.coroutines.launch
import java.util.Locale

private val Sizes = listOf(512, 720, 1080)
private val Seconds = listOf(3, 6, 10, 15)

@Composable
fun ExportSheet(
    doc: ProjectDoc,
    beforeStart: suspend () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val exports = ServiceLocator.exports
    val prefs = ServiceLocator.appPrefs
    val state by exports.state.collectAsStateWithLifecycle()
    val savedFormat by prefs.exportFormat.collectAsStateWithLifecycle(ExportFormat.GIF)
    val savedSize by prefs.exportSize.collectAsStateWithLifecycle(720)
    var format by rememberSaveable(savedFormat) { mutableStateOf(savedFormat) }
    var size by rememberSaveable(savedSize) { mutableStateOf(savedSize) }
    var seconds by rememberSaveable { mutableStateOf(6) }
    var savedNote by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cycle = doc.cycle
    val gifOneCycle = !LoopClock.isOverCap(cycle)

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val done = exports.state.value as? ExportState.Done ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val ok = runCatching { exports.saver.copyTo(done.file, uri) }.isSuccess
                val note = if (ok) "Saved ${done.displayName}" else "Save stopped: that location is full or read-only. Pick another folder."
                savedNote = note
                onMessage(note)
            }
        }
    }

    fun start(longEdge: Int) {
        scope.launch {
            prefs.setExportFormat(format)
            prefs.setExportSize(longEdge)
            beforeStart()
            val secs = if (format == ExportFormat.GIF && gifOneCycle) null else seconds
            exports.start(doc.project.id, format, longEdge, secs)
        }
    }

    AppSheet("Export", onDismiss = {
        exports.reset()
        onDismiss()
    }) {
        when (val s = state) {
            is ExportState.Running -> {
                val label = if (s.pass == 1) "Reading colours: frame ${s.frame} of ${s.total}" else "Frame ${s.frame} of ${s.total}"
                val done = (if (s.passes == 2) s.pass - 1 else 0) * s.total + s.frame
                Text("Exporting ${s.format.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (done.toFloat() / (s.total * s.passes)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                QuietButton("Stop export", { exports.cancel() }, Modifier.fillMaxWidth())
            }
            is ExportState.Done -> {
                LaunchedEffect(s) {
                    if (s.askForReview) {
                        prefs.setReviewAsked()
                        context.findActivity()?.let { activity ->
                            runCatching {
                                val manager = ReviewManagerFactory.create(activity)
                                manager.launchReview(activity, manager.requestReview())
                            }
                        }
                    }
                }
                val isGif = s.format == ExportFormat.GIF
                Text("Exported ${s.format.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${s.displayName}, ${formatBytes(s.file.length())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.unreadableFrames > 0) {
                    Text(
                        "${plural(s.unreadableFrames, "frame", "frames")} could not be read and went out blank.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (exports.saver.canSaveToGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PrimaryButton("Save to gallery", {
                            scope.launch {
                                val r = runCatching { exports.saver.saveToGallery(s.file, isGif, s.displayName) }
                                val note = r.getOrNull()?.let { "Saved to $it" }
                                    ?: "Save stopped: the gallery would not take the file. Try Share instead."
                                savedNote = note
                                onMessage(note)
                            }
                        }, Modifier.weight(1f))
                    } else {
                        PrimaryButton("Save as file", { saveAs.launch(s.displayName) }, Modifier.weight(1f))
                    }
                    QuietButton("Share", { share(context, s.file, isGif) }, Modifier.weight(1f))
                }
                savedNote?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                QuietButton("Export again", { savedNote = null; exports.reset() }, Modifier.fillMaxWidth())
            }
            is ExportState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                val retry = s.retryAt
                if (retry != null) {
                    PrimaryButton("Export at $retry", { exports.reset(); size = retry; start(retry) }, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton("Try again", { exports.reset() }, Modifier.fillMaxWidth())
                }
            }
            ExportState.Idle -> {
                Column {
                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(ExportFormat.entries.toList(), format, { it.name }, { format = it })
                    Spacer(Modifier.height(16.dp))
                    Text("Size", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(Sizes, size, { "$it px" }, { size = it })
                    Spacer(Modifier.height(16.dp))
                    Text("Length", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (format == ExportFormat.GIF && gifOneCycle) {
                        val secs = cycle.toFloat() / doc.project.fps
                        Text(
                            "One cycle: ${plural(cycle.toInt(), "frame", "frames")}, ${String.format(Locale.US, "%.1f", secs)} s. The file loops with no jump.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ChoiceRow(Seconds, seconds, { "$it s" }, { seconds = it })
                        if (format == ExportFormat.GIF) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This cycle runs past 600 frames, so the GIF uses a set length.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Export ${format.name}", { start(size) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No watermark. The file stays on this phone until you save or share it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun share(context: Context, file: java.io.File, isGif: Boolean) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (isGif) "image/gif" else "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private fun formatBytes(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1f MB", n / 1_000_000.0)
    n >= 1_000 -> "${n / 1_000} KB"
    else -> "$n bytes"
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
