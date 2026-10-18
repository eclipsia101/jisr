package org.jisr.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.jisr.app.data.AppSettings
import org.jisr.app.data.AddRepositoryResult
import org.jisr.app.data.RepositoryInfo
import org.jisr.app.data.PluginInfo
import org.jisr.app.sandbox.PluginSettingField
import org.jisr.app.server.ServerService
import org.jisr.app.server.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JisrApplication).container

    val server: StateFlow<ServerState> = container.server.state
    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val plugins: StateFlow<List<PluginInfo>> =
        container.plugins.plugins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repositories: StateFlow<List<RepositoryInfo>> =
        container.plugins.repositories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val busy = MutableStateFlow(false)
    val working: StateFlow<Boolean> = busy

    fun startServer() = ServerService.start(getApplication())
    fun stopServer() = ServerService.stop(getApplication())
    fun refreshHost() = container.server.refreshHost()

    private val keyFailed = MutableStateFlow(false)
    val tmdbKeyError: StateFlow<Boolean> = keyFailed

    fun setTmdbKey(key: String) = viewModelScope.launch {
        keyFailed.value = !container.settings.setTmdbKey(key)
    }

    fun clearTmdbKeyError() { keyFailed.value = false }

    fun setDohProvider(id: String) = viewModelScope.launch { container.settings.setDohProvider(id) }

    fun setDohCustomUrl(url: String) = viewModelScope.launch { container.settings.setDohCustomUrl(url) }

    fun setPort(port: Int) = viewModelScope.launch {
        container.settings.setPort(port)
        restartIfRunning()
    }

    fun setLocalNetwork(enabled: Boolean) = viewModelScope.launch {
        container.settings.setLocalNetwork(enabled)
        restartIfRunning()
    }

    fun setBackgroundRun(enabled: Boolean) = viewModelScope.launch {
        container.settings.setBackgroundRun(enabled)

    }

    fun onAppBackgrounded() = viewModelScope.launch {
        if (!container.settings.current().backgroundRun && container.server.isRunning()) stopServer()
    }

    fun setGlobalTimeoutSec(value: Int) = viewModelScope.launch { container.settings.setGlobalTimeoutSec(value) }

    fun setMaxStreamsPerProvider(value: Int) = viewModelScope.launch { container.settings.setMaxStreamsPerProvider(value) }

    fun setResolutionEnabled(id: String, enabled: Boolean) = viewModelScope.launch { container.settings.setResolutionEnabled(id, enabled) }

    fun setConcurrency(value: Int) = viewModelScope.launch { container.settings.setConcurrency(value) }

    fun setPluginEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        container.plugins.setEnabled(id, enabled)
    }

    fun setPluginsEnabled(ids: List<String>, enabled: Boolean) = viewModelScope.launch {
        container.plugins.setManyEnabled(ids, enabled)
    }

    fun loadPluginSettings(
        id: String,
        onLoaded: (List<PluginSettingField>, Map<String, JsonPrimitive>) -> Unit,
    ) = viewModelScope.launch {
        val layout = container.plugins.settingsLayout(id)
        onLoaded(layout, container.plugins.savedSettings(id))
    }

    fun savePluginSettings(id: String, values: Map<String, JsonPrimitive>) = viewModelScope.launch {
        container.plugins.saveSettings(id, values)
    }

    fun addRepository(url: String, onResult: (AddRepositoryResult) -> Unit) = viewModelScope.launch {
        busy.value = true
        try {
            onResult(container.plugins.addRepository(url))
        } finally {
            busy.value = false
        }
    }

    fun refreshAll() = viewModelScope.launch {
        busy.value = true
        try {
            container.plugins.refreshAll()
        } finally {
            busy.value = false
        }
    }

    fun removeRepository(url: String) = viewModelScope.launch { container.plugins.removeRepository(url) }

    fun wipeEverything() = viewModelScope.launch {
        container.server.stop()
        ServerService.stop(getApplication())
        container.plugins.deleteAll()
        container.settings.wipe()
    }

    private suspend fun restartIfRunning() {
        if (container.server.isRunning()) container.server.restart()
    }
}
