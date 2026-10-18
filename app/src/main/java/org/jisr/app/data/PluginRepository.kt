package org.jisr.app.data

import android.content.Context
import org.jisr.app.net.httpRequestRaw
import org.jisr.app.sandbox.PluginRuntime
import org.jisr.app.sandbox.PluginSettingField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

internal fun parseSavedSettings(raw: String?): Map<String, JsonPrimitive> {
    if (raw.isNullOrBlank()) return emptyMap()
    val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
    return obj.mapNotNull { (k, v) ->
        val p = v as? JsonPrimitive ?: return@mapNotNull null
        if (p.isString || p.booleanOrNull != null) k to p else null
    }.toMap()
}

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val repositoryUrl: String,
    val supportedTypes: List<String>,
    val enabled: Boolean,
    val sizeBytes: Int,

    val manifestEnabled: Boolean = true,

    val hasSettings: Boolean = false,
)

data class RepositoryInfo(
    val manifestUrl: String,
    val name: String,
    val description: String,
    val version: String,
    val lastUpdated: Long,
    val pluginCount: Int,
)

data class PluginSource(val id: String, val name: String, val code: String, val settingsJson: String = "{}")

sealed interface AddRepositoryResult {
    data class Added(val repository: RepositoryInfo) : AddRepositoryResult
    data object InvalidUrl : AddRepositoryResult
    data object AlreadyAdded : AddRepositoryResult
    data object Unreachable : AddRepositoryResult
    data object NotAManifest : AddRepositoryResult
    data object NoPlugins : AddRepositoryResult
}

class PluginRepository(
    context: Context,
    private val settings: SettingsRepository,
) {
    private val root = context.applicationContext.filesDir
    private val codeDir = File(root, "plugin_code")
    private val indexFile = File(root, "plugin_repos.json")
    private val lock = Mutex()
    private val revision = MutableStateFlow(0)

    @Volatile private var indexCache: List<StoredRepo>? = null

    private data class StoredScraper(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val supportedTypes: List<String>,
        val file: String,
        val manifestEnabled: Boolean = true,
        val hasSettings: Boolean = false,
    )

    private data class StoredRepo(
        val manifestUrl: String,
        val name: String,
        val description: String,
        val version: String,
        val lastUpdated: Long,
        val scrapers: List<StoredScraper>,
    )

    val repositories: Flow<List<RepositoryInfo>> = revision
        .map { loadIndex().map { it.toInfo() } }
        .flowOn(Dispatchers.IO)

    val plugins: Flow<List<PluginInfo>> =
        combine(settings.settings.map { it.disabledPlugins }.distinctUntilChanged(), revision) { disabled, _ -> disabled }
            .map { disabled -> scan(disabled) }
            .flowOn(Dispatchers.IO)

    suspend fun enabledSources(mediaType: String? = null): List<PluginSource> = withContext(Dispatchers.IO) {
        val disabled = settings.current().disabledPlugins
        val wanted = mediaType?.let(::normalizePluginType)
        val saved = settings.pluginSettingsSnapshot()
        loadIndex().flatMap { repo -> repo.scrapers }
            .filter { it.manifestEnabled && it.id !in disabled && (wanted == null || wanted in it.supportedTypes) }
            .mapNotNull { s -> readCode(s.file)?.let { PluginSource(s.id, s.name, it, saved[s.id] ?: "{}") } }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = settings.setPluginEnabled(id, enabled)

    suspend fun setManyEnabled(ids: Collection<String>, enabled: Boolean) {
        val allowed = if (enabled) {
            val locked = loadIndex().flatMap { it.scrapers }.filter { !it.manifestEnabled }.map { it.id }.toSet()
            ids.filter { it !in locked }
        } else ids.toList()
        settings.setPluginsEnabled(allowed, enabled)
    }

    suspend fun settingsLayout(id: String): List<PluginSettingField> = withContext(Dispatchers.IO) {
        val scraper = loadIndex().flatMap { it.scrapers }.firstOrNull { it.id == id } ?: return@withContext emptyList()
        val code = readCode(scraper.file) ?: return@withContext emptyList()
        try {
            PluginRuntime.settingsLayout(code, id, settings.current().tmdbKey)
        } catch (_: TimeoutCancellationException) {
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun savedSettings(id: String): Map<String, JsonPrimitive> =
        parseSavedSettings(settings.pluginSettingsSnapshot()[id])

    suspend fun saveSettings(id: String, values: Map<String, JsonPrimitive>) {
        val clean = values.filter { (k, v) -> k.isNotBlank() && (v.isString || v.booleanOrNull != null) }
        settings.setPluginSettings(id, if (clean.isEmpty()) null else JsonObject(clean).toString())
    }

    suspend fun addRepository(rawUrl: String): AddRepositoryResult = withContext(Dispatchers.IO) {
        val url = normalizeManifestUrl(rawUrl) ?: return@withContext AddRepositoryResult.InvalidUrl
        if (lock.withLock { loadIndex().any { it.manifestUrl.equals(url, ignoreCase = true) } }) {
            return@withContext AddRepositoryResult.AlreadyAdded
        }
        when (val fetched = fetchRepository(url)) {
            is Fetched.Ok -> lock.withLock {

                if (loadIndex().any { it.manifestUrl.equals(url, ignoreCase = true) }) {
                    fetched.repo.scrapers.forEach { File(codeDir, it.file).delete() }
                    AddRepositoryResult.AlreadyAdded
                } else {
                    saveIndex(loadIndex() + fetched.repo)
                    revision.update { it + 1 }
                    AddRepositoryResult.Added(fetched.repo.toInfo())
                }
            }
            Fetched.Unreachable -> AddRepositoryResult.Unreachable
            Fetched.NotAManifest -> AddRepositoryResult.NotAManifest
            Fetched.NoPlugins -> AddRepositoryResult.NoPlugins
        }
    }

    suspend fun refresh(manifestUrl: String): Boolean = withContext(Dispatchers.IO) { refreshOne(manifestUrl) }

    suspend fun refreshAll(): Int = withContext(Dispatchers.IO) {
        val urls = lock.withLock { loadIndex().map { it.manifestUrl } }
        val gate = Semaphore(REFRESH_PARALLEL_REPOS)
        coroutineScope { urls.map { url -> async { gate.withPermit { refreshOne(url) } } }.awaitAll() }.count { it }
    }

    suspend fun removeRepository(manifestUrl: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            val index = loadIndex()
            val gone = index.firstOrNull { it.manifestUrl == manifestUrl } ?: return@withContext
            saveIndex(index - gone)
            gone.scrapers.forEach { File(codeDir, it.file).delete() }
            gone.scrapers.forEach { settings.setPluginEnabled(it.id, true) }
            gone.scrapers.forEach { settings.setPluginSettings(it.id, null) }
            revision.update { it + 1 }
        }
    }

    suspend fun deleteAll() = lock.withLock {
        withContext(Dispatchers.IO) {
            codeDir.deleteRecursively()
            File(root, "plugins").deleteRecursively()
            indexFile.delete()
            indexCache = null
            revision.update { it + 1 }
        }
    }

    private suspend fun refreshOne(manifestUrl: String): Boolean {
        val old = lock.withLock { loadIndex().firstOrNull { it.manifestUrl == manifestUrl } } ?: return false
        val fetched = fetchRepository(manifestUrl, old) as? Fetched.Ok ?: return false
        return lock.withLock {

            val index = loadIndex()
            val current = index.firstOrNull { it.manifestUrl == manifestUrl }
            if (current == null) {
                fetched.repo.scrapers.forEach { File(codeDir, it.file).delete() }
                return@withLock false
            }
            val keep = fetched.repo.scrapers.map { it.file }.toSet()
            current.scrapers.filter { it.file !in keep }.forEach { File(codeDir, it.file).delete() }
            saveIndex(index.map { if (it.manifestUrl == manifestUrl) fetched.repo else it })
            revision.update { it + 1 }
            true
        }
    }

    private sealed interface Fetched {
        data class Ok(val repo: StoredRepo) : Fetched
        data object Unreachable : Fetched
        data object NotAManifest : Fetched
        data object NoPlugins : Fetched
    }

    private suspend fun fetchRepository(manifestUrl: String, previous: StoredRepo? = null): Fetched {
        val payload = download(manifestUrl, MAX_MANIFEST_BYTES) ?: return Fetched.Unreachable
        val manifest = parseRepositoryManifest(payload) ?: return Fetched.NotAManifest
        codeDir.mkdirs()
        val gate = Semaphore(DOWNLOAD_PARALLELISM)
        val scrapers = coroutineScope {
            manifest.scrapers.map { info ->
                async { gate.withPermit { fetchScraper(manifestUrl, info, previous) } }
            }.awaitAll()
        }.filterNotNull()
        if (scrapers.isEmpty()) return Fetched.NoPlugins
        return Fetched.Ok(
            StoredRepo(manifestUrl, manifest.name, manifest.description, manifest.version, System.currentTimeMillis(), scrapers),
        )
    }

    private suspend fun fetchScraper(manifestUrl: String, info: ManifestScraper, previous: StoredRepo?): StoredScraper? {
        val id = "${manifestUrl.lowercase()}:${info.id}"
        val code = download(resolveScriptUrl(manifestUrl, info.filename), MAX_PLUGIN_BYTES)
            ?: return previous?.scrapers?.firstOrNull { it.id == id && File(codeDir, it.file).isFile }
        val file = sha256(id).take(32) + ".js"

        val temp = File(codeDir, "$file.${System.nanoTime()}.tmp")
        val ok = runCatching {
            temp.writeText(code)
            temp.renameTo(File(codeDir, file))
        }.getOrDefault(false)
        if (!ok) { temp.delete(); return null }
        return StoredScraper(id, info.name, info.description, info.version, info.supportedTypes, file, info.enabled, info.hasSettings)
    }

    private suspend fun download(url: String, maxBytes: Int): String? {
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = mapOf("Accept" to "application/json"),
            body = "",
            maxResponseBodyBytes = maxBytes + 1,
        )
        if (response.status !in 200..299 || response.bodyBytes.size > maxBytes) return null
        return response.body.takeIf { it.isNotBlank() }
    }

    private fun readCode(file: String): String? =
        File(codeDir, file).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    private fun scan(disabled: Set<String>): List<PluginInfo> =
        loadIndex().flatMap { repo ->
            repo.scrapers.map { s ->
                PluginInfo(
                    id = s.id,
                    name = s.name,
                    description = s.description,
                    version = s.version,
                    repositoryUrl = repo.manifestUrl,
                    supportedTypes = s.supportedTypes,
                    enabled = s.manifestEnabled && s.id !in disabled,
                    sizeBytes = File(codeDir, s.file).length().toInt(),
                    manifestEnabled = s.manifestEnabled,
                    hasSettings = s.hasSettings,
                )
            }
        }

    private fun StoredRepo.toInfo() = RepositoryInfo(manifestUrl, name, description, version, lastUpdated, scrapers.size)

    private fun loadIndex(): List<StoredRepo> = indexCache ?: readIndex().also { indexCache = it }

    private fun readIndex(): List<StoredRepo> = runCatching {
        if (!indexFile.isFile) return emptyList()
        Json.parseToJsonElement(indexFile.readText()).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            StoredRepo(
                manifestUrl = o.str("manifestUrl") ?: return@mapNotNull null,
                name = o.str("name").orEmpty(),
                description = o.str("description").orEmpty(),
                version = o.str("version").orEmpty(),
                lastUpdated = (o["lastUpdated"] as? JsonPrimitive)?.longOrNull ?: 0L,
                scrapers = (o["scrapers"] as? JsonArray).orEmpty().mapNotNull { se ->
                    val so = se.jsonObject
                    val file = so.str("file")?.takeIf { SAFE_FILE.matches(it) } ?: return@mapNotNull null
                    StoredScraper(
                        id = so.str("id") ?: return@mapNotNull null,
                        name = so.str("name").orEmpty(),
                        description = so.str("description").orEmpty(),
                        version = so.str("version").orEmpty(),
                        supportedTypes = (so["supportedTypes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                        file = file,
                        manifestEnabled = (so["manifestEnabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                        hasSettings = (so["hasSettings"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    private fun saveIndex(repos: List<StoredRepo>) {
        val json = buildJsonArray {
            repos.forEach { r ->
                add(buildJsonObject {
                    put("manifestUrl", r.manifestUrl)
                    put("name", r.name)
                    put("description", r.description)
                    put("version", r.version)
                    put("lastUpdated", r.lastUpdated)
                    put("scrapers", buildJsonArray {
                        r.scrapers.forEach { s ->
                            add(buildJsonObject {
                                put("id", s.id)
                                put("name", s.name)
                                put("description", s.description)
                                put("version", s.version)
                                put("file", s.file)
                                put("manifestEnabled", s.manifestEnabled)
                                put("hasSettings", s.hasSettings)
                                put("supportedTypes", buildJsonArray { s.supportedTypes.forEach { add(JsonPrimitive(it)) } })
                            })
                        }
                    })
                })
            }
        }
        val temp = File(root, "plugin_repos.json.tmp")
        temp.writeText(json.toString())
        temp.renameTo(indexFile)
        indexCache = repos
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {

        const val MAX_PLUGIN_BYTES = 16 * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
        private const val DOWNLOAD_PARALLELISM = 6
        private const val REFRESH_PARALLEL_REPOS = 2
        private val SAFE_FILE = Regex("^[0-9a-f]{32}\\.js$")
    }
}
