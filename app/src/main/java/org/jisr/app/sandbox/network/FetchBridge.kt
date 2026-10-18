package org.jisr.app.sandbox.network

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import org.jisr.app.net.httpRequestRaw
import org.jisr.app.sandbox.host.HostModule
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val MAX_FETCH_HEADER_VALUE_CHARS = 8 * 1024
private const val FETCH_TRUNCATION_SUFFIX = "\n...[truncated]"
private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

internal class FetchBridge(private val tmdbKey: String) : HostModule {
    private val json = Json { ignoreUnknownKeys = true }

    override fun register(runtime: QuickJs) {
        runtime.asyncFunction("__native_fetch") { args ->
            val url = args.getOrNull(0)?.toString() ?: ""
            val method = args.getOrNull(1)?.toString() ?: "GET"
            val headersJson = args.getOrNull(2)?.toString() ?: "{}"
            val bodyKind = args.getOrNull(3)?.toString() ?: "none"
            val body = args.getOrNull(4)?.toString() ?: ""
            val followRedirects = args.getOrNull(5) as? Boolean ?: true
            try {
                performNativeFetch(url, method, headersJson, bodyKind, body, followRedirects)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                JsonObject(
                    mapOf(
                        "ok" to JsonPrimitive(false),
                        "status" to JsonPrimitive(0),
                        "statusText" to JsonPrimitive("Fetch failed"),
                        "url" to JsonPrimitive(url),
                        "body" to JsonPrimitive(""),
                        "bodyBase64" to JsonPrimitive(""),
                        "headers" to JsonObject(emptyMap()),
                    ),
                ).toString()
            }
        }
    }

    private suspend fun performNativeFetch(
        url: String,
        method: String,
        headersJson: String,
        bodyKind: String,
        body: String,
        followRedirects: Boolean,
    ): String {
        val parsedUrl = url.toHttpUrlOrNull() ?: error("Blocked: only http(s) URLs are allowed")
        val isTmdb = parsedUrl.host == "api.themoviedb.org" || parsedUrl.host == "api.tmdb.org"
        val headers = parseHeaders(headersJson).toMutableMap()

        var effectiveUrl = url
        if (isTmdb && tmdbKey.length > 40) {
            val clean = parsedUrl.newBuilder().apply {
                if (parsedUrl.queryParameter("api_key") == tmdbKey) removeAllQueryParameters("api_key")
            }.build()
            effectiveUrl = clean.toString()
            if (headers.keys.none { it.equals("Authorization", true) }) headers["Authorization"] = "Bearer $tmdbKey"
        }

        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers["User-Agent"] = DEFAULT_USER_AGENT
        }

        val response = httpRequestRaw(
            method = method,
            url = effectiveUrl,
            headers = headers,
            body = if (bodyKind == "text") body else "",
            followRedirects = followRedirects,
            bodyBytes = decodeBinaryBody(bodyKind, body),
        )

        val responseHeaders = response.headers.mapKeys { (key, _) -> key.lowercase() }
            .mapValues { (_, value) -> truncateString(value, MAX_FETCH_HEADER_VALUE_CHARS) }
        val result = JsonObject(
            mapOf(
                "ok" to JsonPrimitive(response.status in 200..299),
                "status" to JsonPrimitive(response.status),
                "url" to JsonPrimitive(response.url),
                "statusText" to JsonPrimitive(response.statusText),
                "body" to JsonPrimitive(response.body),
                "bodyBase64" to JsonPrimitive(encodeBase64(response.bodyBytes)),
                "headers" to JsonObject(responseHeaders.mapValues { JsonPrimitive(it.value) }),
            ),
        )
        return result.toString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBinaryBody(bodyKind: String, body: String): ByteArray? = when (bodyKind) {
        "base64" -> Base64.decode(body)
        "none", "text" -> null
        else -> error("Unsupported fetch body kind: $bodyKind")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

    private fun parseHeaders(headersJson: String): Map<String, String> {
        return runCatching {
            val obj = json.parseToJsonElement(headersJson) as? JsonObject ?: JsonObject(emptyMap())
            obj.entries
                .mapNotNull { (key, value) ->
                    value.jsonPrimitive.contentOrNull?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun truncateString(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        val end = maxChars - FETCH_TRUNCATION_SUFFIX.length
        if (end <= 0) return FETCH_TRUNCATION_SUFFIX.take(maxChars)
        return value.substring(0, end) + FETCH_TRUNCATION_SUFFIX
    }
}
