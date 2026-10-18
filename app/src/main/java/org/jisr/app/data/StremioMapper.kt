package org.jisr.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object StremioMapper {
    private val invisible = Regex("[\\u200B\\uFEFF]")

    fun manifest(): String = buildJsonObject {
        put("id", "org.jisr.app")
        put("version", "1.0.0")
        put("name", "Jisr")
        put("description", "Private, on-device stream resolver")
        putJsonArray("resources") { add("stream") }
        putJsonArray("types") {
            add("movie")
            add("series")
        }
        putJsonArray("idPrefixes") {
            add("tt")
            add("tmdb:")
        }
        putJsonArray("catalogs") {}
    }.toString()

    fun streams(items: List<ResolvedStream>): String = buildJsonObject {
        putJsonArray("streams") { items.forEach { add(stream(it)) } }
    }.toString()

    private fun stream(item: ResolvedStream): JsonObject {
        val r = item.result
        val label = (r.name ?: r.title).replace(invisible, "").trim()
        return buildJsonObject {
            put("name", label.ifBlank { "Stream" })
            put(
                "description",
                listOfNotNull(r.quality, r.size, r.language).filter { it.isNotBlank() }.joinToString(" \u2022 "),
            )
            putJsonObject("behaviorHints") {
                put("notWebReady", true)
                r.headers?.let { headers ->
                    putJsonObject("proxyHeaders") {
                        putJsonObject("request") { headers.forEach { (k, v) -> put(k, v) } }
                    }
                }
            }
            val hash = r.infoHash
            if (r.url.startsWith("magnet:") && hash != null) put("infoHash", hash) else put("url", r.url)
            r.subtitles?.let { subs ->
                putJsonArray("subtitles") {
                    subs.forEachIndexed { index, sub ->
                        add(
                            buildJsonObject {
                                put("id", "sub$index")
                                put("url", sub.url)
                                put("lang", sub.language)
                            },
                        )
                    }
                }
            }
        }
    }
}
