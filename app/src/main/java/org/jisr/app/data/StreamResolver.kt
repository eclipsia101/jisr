package org.jisr.app.data

import org.jisr.app.sandbox.PluginRuntime
import org.jisr.app.sandbox.PluginRuntimeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class ResolvedStream(val pluginId: String, val result: PluginRuntimeResult)

internal data class MediaRef(val base: String, val season: Int?, val episode: Int?)

private val NUMERIC_ID = Regex("\\d{1,10}")
private val IMDB_ID = Regex("tt\\d{1,10}")

internal fun parseMediaId(raw: String): MediaRef? {
    val parts = raw.split(':')
    return if (parts[0] == "tmdb") {
        val id = parts.getOrNull(1)?.takeIf { NUMERIC_ID.matches(it) } ?: return null
        MediaRef("tmdb:$id", parts.getOrNull(2)?.toIntOrNull(), parts.getOrNull(3)?.toIntOrNull())
    } else {
        if (!IMDB_ID.matches(parts[0])) return null
        MediaRef(parts[0], parts.getOrNull(1)?.toIntOrNull(), parts.getOrNull(2)?.toIntOrNull())
    }
}

class StreamResolver(
    private val plugins: PluginRepository,
    private val tmdb: TmdbClient,
    private val settings: SettingsRepository,
) {
    suspend fun resolve(type: String, rawId: String): List<ResolvedStream> {
        val ref = parseMediaId(rawId) ?: return emptyList()
        val isTv = type == "series"
        val tmdbId = if (ref.base.startsWith("tmdb:")) {
            ref.base.removePrefix("tmdb:")
        } else {
            tmdb.findTmdbId(ref.base, isTv) ?: return emptyList()
        }
        val mediaType = if (isTv) "tv" else "movie"
        return runAll(plugins.enabledSources(mediaType), tmdbId, mediaType, ref.season, ref.episode)
    }

    suspend fun runAll(
        sources: List<PluginSource>,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): List<ResolvedStream> = coroutineScope {
        val config = settings.current()

        val gate = Semaphore(config.concurrency.coerceIn(AppSettings.MIN_CONCURRENCY, AppSettings.MAX_CONCURRENCY))
        val maxPerProvider = config.maxStreamsPerProvider
        val finished = ConcurrentHashMap<String, List<PluginRuntimeResult>>()
        val jobs = sources.map { source ->
            launch {
                val results = gate.withPermit { execute(source, tmdbId, mediaType, season, episode, config.tmdbKey) }
                val kept = results.filter { Resolution.of(it).id in config.enabledResolutions }
                finished[source.id] = if (maxPerProvider > 0) kept.take(maxPerProvider) else kept
            }
        }

        withTimeoutOrNull(config.globalTimeoutSec * 1000L) { jobs.joinAll() }
        jobs.forEach { it.cancel() }

        val seen = HashSet<String>()
        sources
            .flatMap { source -> finished[source.id].orEmpty().map { ResolvedStream(source.id, it) } }
            .filter { it.result.url.isNotBlank() && seen.add(it.result.url) }
    }

    private suspend fun execute(
        source: PluginSource,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        tmdbKey: String,
    ): List<PluginRuntimeResult> = try {
        PluginRuntime.execute(source.code, source.id, tmdbId, mediaType, season, episode, tmdbKey, source.settingsJson)
    } catch (_: TimeoutCancellationException) {
        emptyList()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
}
