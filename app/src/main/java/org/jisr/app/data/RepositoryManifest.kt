package org.jisr.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ManifestScraper(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val filename: String,
    val supportedTypes: List<String>,

    val enabled: Boolean = true,

    val hasSettings: Boolean = false,
)

data class RepositoryManifest(
    val name: String,
    val version: String,
    val description: String,
    val scrapers: List<ManifestScraper>,
)

internal const val MAX_MANIFEST_SCRAPERS = 100

internal fun normalizeManifestUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        "://" in trimmed -> return null
        else -> "https://$trimmed"
    }
    val noFragment = withScheme.substringBefore('#')
    val query = noFragment.substringAfter('?', "")
    val beforeQuery = noFragment.substringBefore('?')
    val afterScheme = beforeQuery.substringAfter("://")
    if (afterScheme.substringBefore('/').isBlank()) return null
    val path = beforeQuery.trimEnd('/')
    val withManifest = if (path.endsWith("/manifest.json")) path else "$path/manifest.json"
    return if (query.isEmpty()) withManifest else "$withManifest?$query"
}

internal fun resolveScriptUrl(manifestUrl: String, filename: String): String {
    if (filename.startsWith("http://", ignoreCase = true) || filename.startsWith("https://", ignoreCase = true)) return filename
    val base = manifestUrl.substringBefore('?').removeSuffix("/manifest.json")
    return "$base/${filename.trimStart('/')}"
}

internal fun normalizePluginType(value: String): String =
    when (val v = value.lowercase()) {
        "series", "show", "other" -> "tv"
        else -> v
    }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.strings(key: String): List<String>? =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }

internal fun parseRepositoryManifest(payload: String): RepositoryManifest? {
    val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    val list = runCatching { root["scrapers"]?.jsonArray }.getOrNull() ?: return null
    val scrapers = list.take(MAX_MANIFEST_SCRAPERS * 4).mapNotNull { element ->
        val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val id = o.string("id")?.trim().orEmpty()
        val filename = o.string("filename")?.trim().orEmpty()
        if (id.isEmpty() || filename.isEmpty()) return@mapNotNull null
        val supported = o.strings("supportedPlatforms")
        if (!supported.isNullOrEmpty() && supported.none { it.equals("android", true) }) return@mapNotNull null
        if (o.strings("disabledPlatforms").orEmpty().any { it.equals("android", true) }) return@mapNotNull null
        ManifestScraper(
            id = id.take(128),
            name = (o.string("name") ?: id).take(80),
            description = o.string("description").orEmpty().take(300),
            version = o.string("version").orEmpty().take(32),
            filename = filename,
            supportedTypes = (o.strings("supportedTypes") ?: listOf("movie", "tv")).map(::normalizePluginType).distinct(),
            enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
            hasSettings = (o["hasSettings"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
    return RepositoryManifest(
        scrapers = scrapers.take(MAX_MANIFEST_SCRAPERS),
        name = root.string("name")?.take(80)?.ifBlank { null } ?: "Repository",
        version = root.string("version").orEmpty().take(32),
        description = root.string("description").orEmpty().take(300),
    )
}
