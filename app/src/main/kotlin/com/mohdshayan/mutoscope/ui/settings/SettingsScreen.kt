package com.mohdshayan.mutoscope.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.mutoscope.R
import com.mohdshayan.mutoscope.core.stats.DrawingDays
import com.mohdshayan.mutoscope.data.ImportException
import com.mohdshayan.mutoscope.data.prefs.Counts
import com.mohdshayan.mutoscope.data.prefs.EditorPrefs
import com.mohdshayan.mutoscope.data.prefs.OnionTint
import com.mohdshayan.mutoscope.di.ServiceLocator
import com.mohdshayan.mutoscope.ui.components.ChoiceRow
import com.mohdshayan.mutoscope.ui.components.GlyphButton
import com.mohdshayan.mutoscope.ui.components.PlainTextButton
import com.mohdshayan.mutoscope.ui.components.PrimaryButton
import com.mohdshayan.mutoscope.ui.components.QuietButton
import com.mohdshayan.mutoscope.ui.components.SwitchRow
import com.mohdshayan.mutoscope.ui.components.plural
import com.mohdshayan.mutoscope.ui.editor.Stepper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val archive = ServiceLocator.archive

    val editor: StateFlow<EditorPrefs> = prefs.editorPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, EditorPrefs())
    val counts: StateFlow<Counts> = prefs.counts.stateIn(viewModelScope, SharingStarted.Eagerly, Counts(false, 0, emptyList()))

    /** Projects whose drawn reels do not all share one length: the loop idea in use. */
    val multiReelCycles: StateFlow<Int> = ServiceLocator.projects.observeTiles()
        .map { tiles -> tiles.count { !it.project.isSample && it.lengths.distinct().size > 1 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages = channel.receiveAsFlow()

    fun setOnion(p: EditorPrefs) = viewModelScope.launch { prefs.setOnion(p.onionBefore, p.onionAfter, p.onionTint, p.onionOpacity, p.lightTable) }
    fun setLeftHanded(v: Boolean) = viewModelScope.launch { prefs.setLeftHanded(v) }
    fun setCounts(v: Boolean) = viewModelScope.launch { prefs.setCountsOptIn(v) }

    fun backup(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        val r = runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { archive.backupAll(it) } ?: error("no stream")
        }
        _busy.value = false
        channel.send(
            r.getOrNull()?.let { "Backed up ${plural(it, "project", "projects")}" }
                ?: "Backup stopped: that location is full or read-only. Pick another folder.",
        )
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        val r = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { archive.importAny(it) } ?: throw ImportException("That file could not be opened")
        }
        _busy.value = false
        channel.send(
            r.getOrNull()?.let { "Restored ${plural(it, "project", "projects")}" }
                ?: "${(r.exceptionOrNull() as? ImportException)?.message ?: "That file could not be opened"}. Pick a Mutoscope backup or .mutoscope file.",
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val prefs by vm.editor.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val multi by vm.multiReelCycles.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var licence by rememberSaveable { mutableStateOf<String?>(null) }
    val policyUrl = stringResource(R.string.privacy_policy_url)

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.backup(uri) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restore(uri) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to loops", onBack)
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .widthIn(max = 640.dp),
        ) {
            Section("Onion skin")
            Stepper("Frames before", prefs.onionBefore, 0..5, { vm.setOnion(prefs.copy(onionBefore = it)) }, suffix = { plural(it, "ghost", "ghosts") })
            Stepper("Frames after", prefs.onionAfter, 0..5, { vm.setOnion(prefs.copy(onionAfter = it)) }, suffix = { plural(it, "ghost", "ghosts") })
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                OnionTint.entries.toList(),
                prefs.onionTint,
                { when (it) { OnionTint.LIGHT_TABLE -> "Red and teal"; OnionTint.ORCHID -> "Orchid"; OnionTint.GRAPHITE -> "Graphite" } },
                { vm.setOnion(prefs.copy(onionTint = it)) },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("Light table", prefs.lightTable, { vm.setOnion(prefs.copy(lightTable = it)) }, "Shows the other reels at 30 percent while you draw.")

            Section("Layout")
            SwitchRow("Left-handed rail", prefs.leftHanded, { vm.setLeftHanded(it) }, "On wide screens and in landscape, tools sit on the right.")

            Section("Backups")
            Text(
                "Everything stays on this phone. A backup is one file you can keep anywhere and restore on another phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Back up all projects", { backup.launch("mutoscope-backup-${LocalDate.now()}.zip") }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            QuietButton("Restore from backup", { restore.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, Modifier.fillMaxWidth())

            Section("Your numbers")
            SwitchRow("Keep simple counts on this phone", counts.optIn, { vm.setCounts(it) }, "Off unless you switch it on. Never sent anywhere.")
            if (counts.optIn) {
                val today = LocalDate.now().toEpochDay()
                NumberRow("Loops with reels of different lengths", multi)
                NumberRow("Successful exports", counts.successfulExports)
                NumberRow("Drawing days in the last 30", DrawingDays.countRecent(counts.drawingDays, today))
            }

            Section("Privacy")
            Text(
                "Mutoscope has no account, no ads and no analytics, and it asks for no permissions. Your drawings and backups never leave the phone unless you share them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlainTextButton("Read the privacy policy", {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl))) }
            })

            Section("Licences")
            LicenceRow("Anybody typeface", "SIL Open Font License 1.1") { licence = "anybody" }
            LicenceRow("Public Sans typeface", "SIL Open Font License 1.1") { licence = "publicsans" }
            LicenceRow("Material Icons", "Apache License 2.0") { licence = "icons" }
            Spacer(Modifier.height(32.dp))
        }
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }

    licence?.let { which ->
        val text = remember(which) { licenceText(context, which) }
        AlertDialog(
            onDismissRequest = { licence = null },
            title = {
                Text(
                    when (which) { "anybody" -> "Anybody"; "publicsans" -> "Public Sans"; else -> "Material Icons" },
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { PlainTextButton("Close", { licence = null }) },
        )
    }
}

private fun licenceText(context: android.content.Context, which: String): String {
    val ofl = runCatching { context.resources.openRawResource(R.raw.ofl).bufferedReader().readText() }.getOrDefault("")
    return when (which) {
        "anybody" -> "Copyright 2020 The Anybody Project Authors.\n\n$ofl"
        "publicsans" -> "Copyright 2015 The Public Sans Project Authors.\n\n$ofl"
        else -> context.getString(R.string.licence_material_icons)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun NumberRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
    }
}

@Composable
private fun LicenceRow(name: String, licence: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Read licence", onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
