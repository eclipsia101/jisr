package org.jisr.app.data

import org.jisr.app.net.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class TmdbClient(private val settings: SettingsRepository) {
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHE
    }

    suspend fun findTmdbId(imdbId: String, isTv: Boolean): String? {
        if (!IMDB_ID.matches(imdbId)) return null
        val cacheKey = "${if (isTv) "tv" else "movie"}:$imdbId"
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val key = settings.current().tmdbKey
        if (key.isEmpty()) return null
        val bearer = key.length > 40
        val url = buildString {
            append("https://api.themoviedb.org/3/find/").append(imdbId).append("?external_source=imdb_id")
            if (!bearer) append("&api_key=").append(key)
        }
        val headers = buildMap {
            put("Accept", "application/json")
            if (bearer) put("Authorization", "Bearer $key")
        }
        val response = httpRequestRaw("GET", url, headers, "")
        if (response.status !in 200..299) return null

        val id = runCatching {
            Json.parseToJsonElement(response.body)
                .jsonObject[if (isTv) "tv_results" else "movie_results"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
        }.getOrNull()?.toString() ?: return null

        synchronized(cache) { cache[cacheKey] = id }
        return id
    }

    private companion object {
        const val MAX_CACHE = 256
        val IMDB_ID = Regex("^tt\\d{1,10}$")
    }
}
