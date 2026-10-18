package org.jisr.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.jisr.app.net.DohProvider
import org.jisr.app.sandbox.MAX_CONCURRENT_PLUGINS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "jisr_settings")

data class AppSettings(

    val tmdbKey: String = BUILT_IN_TMDB_KEY,

    val customTmdbKey: Boolean = false,
    val port: Int = DEFAULT_PORT,

    val localNetwork: Boolean = true,

    val backgroundRun: Boolean = true,
    val disabledPlugins: Set<String> = emptySet(),

    val dohProvider: String = DohProvider.Cloudflare.id,
    val dohCustomUrl: String = "",

    val concurrency: Int = DEFAULT_CONCURRENCY,

    val globalTimeoutSec: Int = DEFAULT_TIMEOUT_SEC,

    val maxStreamsPerProvider: Int = DEFAULT_STREAMS,

    val enabledResolutions: Set<String> = Resolution.DEFAULT_IDS,
) {
    companion object {
        const val DEFAULT_CONCURRENCY = 27
        const val MIN_CONCURRENCY = 3
        const val MAX_CONCURRENCY = MAX_CONCURRENT_PLUGINS
        const val CONCURRENCY_STEP = 3

        const val DEFAULT_TIMEOUT_SEC = 15
        const val MIN_TIMEOUT_SEC = 10
        const val MAX_TIMEOUT_SEC = 300
        const val TIMEOUT_STEP_SEC = 10

        const val UNLIMITED_STREAMS = 0
        const val DEFAULT_STREAMS = UNLIMITED_STREAMS
        const val MIN_STREAMS = 1
        const val MAX_STREAMS = 10
        const val STREAMS_STEP = 1

        fun stepStreams(current: Int, direction: Int): Int = when {
            direction > 0 -> when {
                current == UNLIMITED_STREAMS -> UNLIMITED_STREAMS
                current + STREAMS_STEP > MAX_STREAMS -> UNLIMITED_STREAMS
                else -> current + STREAMS_STEP
            }
            current == UNLIMITED_STREAMS -> MAX_STREAMS
            else -> (current - STREAMS_STEP).coerceAtLeast(MIN_STREAMS)
        }
        const val DEFAULT_PORT = 8000
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
    }
}

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore get() = appContext.store

    private object Keys {
        val tmdb = stringPreferencesKey("tmdb_enc")
        val port = intPreferencesKey("port")
        val lan = booleanPreferencesKey("lan")
        val background = booleanPreferencesKey("background_run")
        val disabled = stringSetPreferencesKey("disabled_plugins")
        val dohProvider = stringPreferencesKey("doh_provider")
        val dohUrl = stringPreferencesKey("doh_url")
        val concurrency = intPreferencesKey("concurrency")
        val timeoutSec = intPreferencesKey("global_timeout_sec")
        val maxStreams = intPreferencesKey("max_streams_per_provider")
        val resolutions = stringSetPreferencesKey("enabled_resolutions")
    }

    private companion object {
        const val PLUGIN_SETTINGS_PREFIX = "plugin_settings:"
    }

    @Volatile private var decryptedBlob: String? = null
    @Volatile private var decryptedKey: String = ""

    private fun tmdbKeyOf(blob: String?): String {
        if (blob == null) return ""
        if (blob == decryptedBlob) return decryptedKey
        val plain = SecretBox.decrypt(blob).orEmpty()
        decryptedKey = plain
        decryptedBlob = blob
        return plain
    }

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTmdbKey(key: String): Boolean {
        val clean = key.trim().take(512)
        val sealed = if (clean.isEmpty()) null else SecretBox.encrypt(clean) ?: return false
        dataStore.edit { prefs ->
            if (sealed == null) prefs.remove(Keys.tmdb) else prefs[Keys.tmdb] = sealed
        }
        return true
    }

    suspend fun setPort(port: Int) {
        dataStore.edit { it[Keys.port] = port.coerceIn(AppSettings.MIN_PORT, AppSettings.MAX_PORT) }
    }

    suspend fun setLocalNetwork(enabled: Boolean) {
        dataStore.edit { it[Keys.lan] = enabled }
    }

    suspend fun setBackgroundRun(enabled: Boolean) {
        dataStore.edit { it[Keys.background] = enabled }
    }

    suspend fun setDohProvider(id: String) {
        dataStore.edit { it[Keys.dohProvider] = id }
    }

    suspend fun setDohCustomUrl(url: String) {
        dataStore.edit { it[Keys.dohUrl] = url.trim().take(512) }
    }

    suspend fun setGlobalTimeoutSec(value: Int) {
        dataStore.edit { it[Keys.timeoutSec] = value.coerceIn(AppSettings.MIN_TIMEOUT_SEC, AppSettings.MAX_TIMEOUT_SEC) }
    }

    suspend fun setMaxStreamsPerProvider(value: Int) {
        dataStore.edit {
            it[Keys.maxStreams] = if (value <= 0) AppSettings.UNLIMITED_STREAMS else value.coerceIn(AppSettings.MIN_STREAMS, AppSettings.MAX_STREAMS)
        }
    }

    suspend fun setResolutionEnabled(id: String, enabled: Boolean) {
        if (Resolution.entries.none { it.id == id }) return
        dataStore.edit { prefs ->
            val set = prefs[Keys.resolutions] ?: Resolution.DEFAULT_IDS
            prefs[Keys.resolutions] = if (enabled) set + id else set - id
        }
    }

    suspend fun setConcurrency(value: Int) {
        dataStore.edit {
            it[Keys.concurrency] = value.coerceIn(AppSettings.MIN_CONCURRENCY, AppSettings.MAX_CONCURRENCY)
        }
    }

    suspend fun setPluginEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val set = prefs[Keys.disabled].orEmpty()
            prefs[Keys.disabled] = if (enabled) set - id else set + id
        }
    }

    suspend fun setPluginsEnabled(ids: Collection<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val set = prefs[Keys.disabled].orEmpty()
            prefs[Keys.disabled] = if (enabled) set - ids.toSet() else set + ids
        }
    }

    suspend fun pluginSettingsSnapshot(): Map<String, String> {
        val prefs = dataStore.data.first()
        return prefs.asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (name.startsWith(PLUGIN_SETTINGS_PREFIX) && value is String) name.removePrefix(PLUGIN_SETTINGS_PREFIX) to value else null
        }.toMap()
    }

    suspend fun setPluginSettings(id: String, json: String?) {
        val key = stringPreferencesKey(PLUGIN_SETTINGS_PREFIX + id)
        dataStore.edit { prefs -> if (json == null) prefs.remove(key) else prefs[key] = json.take(64 * 1024) }
    }

    suspend fun wipe() {
        dataStore.edit { it.clear() }
        SecretBox.destroy()
    }

    private fun Preferences.toSettings() = AppSettings(
        tmdbKey = tmdbKeyOf(this[Keys.tmdb]).ifEmpty { BUILT_IN_TMDB_KEY },
        customTmdbKey = tmdbKeyOf(this[Keys.tmdb]).isNotEmpty(),
        port = (this[Keys.port] ?: AppSettings.DEFAULT_PORT).coerceIn(AppSettings.MIN_PORT, AppSettings.MAX_PORT),
        localNetwork = this[Keys.lan] ?: true,
        backgroundRun = this[Keys.background] ?: true,
        disabledPlugins = this[Keys.disabled].orEmpty(),
        dohProvider = this[Keys.dohProvider] ?: DohProvider.Cloudflare.id,
        dohCustomUrl = this[Keys.dohUrl].orEmpty(),
        concurrency = (this[Keys.concurrency] ?: AppSettings.DEFAULT_CONCURRENCY)
            .coerceIn(AppSettings.MIN_CONCURRENCY, AppSettings.MAX_CONCURRENCY),
        globalTimeoutSec = (this[Keys.timeoutSec] ?: AppSettings.DEFAULT_TIMEOUT_SEC)
            .coerceIn(AppSettings.MIN_TIMEOUT_SEC, AppSettings.MAX_TIMEOUT_SEC),
        enabledResolutions = this[Keys.resolutions] ?: Resolution.DEFAULT_IDS,
        maxStreamsPerProvider = (this[Keys.maxStreams] ?: AppSettings.DEFAULT_STREAMS).let {
            if (it <= 0) AppSettings.UNLIMITED_STREAMS else it.coerceIn(AppSettings.MIN_STREAMS, AppSettings.MAX_STREAMS)
        },
    )
}
