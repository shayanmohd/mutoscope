package com.mohdshayan.mutoscope.ui.projects

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.mutoscope.core.loop.Aspect
import com.mohdshayan.mutoscope.data.ImportException
import com.mohdshayan.mutoscope.data.ProjectTile
import com.mohdshayan.mutoscope.di.ServiceLocator
import com.mohdshayan.mutoscope.ui.components.plural
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProjectsState {
    data object Loading : ProjectsState
    data object Failed : ProjectsState
    data class Ready(val tiles: List<ProjectTile>) : ProjectsState
}

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.projects
    private val archive = ServiceLocator.archive
    private val prefs = ServiceLocator.appPrefs
    private val sampleReady = MutableStateFlow(false)

    val state: StateFlow<ProjectsState> =
        combine(repo.observeTiles(), sampleReady) { tiles, ready ->
            if (ready) ProjectsState.Ready(tiles) else ProjectsState.Loading
        }
            .catch { emit(ProjectsState.Failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsState.Loading)

    val defaultFps: StateFlow<Int> = prefs.defaultFps.stateIn(viewModelScope, SharingStarted.Eagerly, 12)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        viewModelScope.launch {
            runCatching { repo.ensureSample() }
            sampleReady.value = true
        }
    }

    fun create(name: String, aspect: Aspect, paper: Int, fps: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            prefs.setDefaultFps(fps)
            val id = repo.create(name, aspect, paper, fps)
            onCreated(id)
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            repo.duplicate(id)
            messageChannel.send("Duplicated")
        }
    }

    fun delete(id: Long, name: String) {
        viewModelScope.launch {
            repo.delete(id)
            messageChannel.send("Deleted $name")
        }
    }

    fun exportTo(id: Long, uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { archive.exportProject(id, it) }
                    ?: error("no stream")
            }
            _busy.value = false
            messageChannel.send(
                if (result.isSuccess) "Exported project" else "Export stopped: that location is full or read-only. Pick another folder.",
            )
        }
    }

    /** [restoring] picks the verb of the message, so it matches the button that started it. */
    fun importFrom(uri: Uri, restoring: Boolean = false) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { archive.importAny(it) }
                    ?: throw ImportException("That file could not be opened")
            }
            _busy.value = false
            val n = result.getOrNull()
            messageChannel.send(
                when {
                    n != null -> "${if (restoring) "Restored" else "Imported"} ${plural(n, "project", "projects")}"
                    result.exceptionOrNull() is ImportException -> "${result.exceptionOrNull()?.message}. Pick a .mutoscope file or a Mutoscope backup."
                    else -> "That file could not be opened. Pick a .mutoscope file or a Mutoscope backup."
                },
            )
        }
    }
}
