package org.jisr.app.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class RawHttpResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String,
    val headers: Map<String, String>,

    val bodyBytes: ByteArray = body.encodeToByteArray(),
)

internal const val DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024

private val client: OkHttpClient = OkHttpClient.Builder()
    .dns(NetGuard.dns)
    .addNetworkInterceptor(NetGuard.interceptor)
    .cookieJar(CookieJar.NO_COOKIES)
    .cache(null)

    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .proxy(Proxy.NO_PROXY)
    .build()

private class Limited(val bytes: ByteArray, val truncated: Boolean)

private fun readAtMost(stream: InputStream, max: Int): Limited {
    val out = ByteArrayOutputStream(minOf(max, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = max
    while (remaining > 0) {
        val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
        if (read <= 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return Limited(out.toByteArray(), remaining == 0 && stream.read() != -1)
}

private fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean = true,
    maxResponseBodyBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    bodyBytes: ByteArray? = null,
): RawHttpResponse = withContext(Dispatchers.IO) {
    val m = method.uppercase()
    val sanitized = headers.filterKeys { !it.equals("Accept-Encoding", ignoreCase = true) }
    val request = try {
        val builder = Request.Builder().url(url)
        sanitized.forEach { (k, v) -> builder.header(k, v) }
        if (m in WITH_BODY) {
            val ct = sanitized.header("Content-Type")
                ?: if (m == "POST") "application/x-www-form-urlencoded" else "application/json"
            builder.method(m, (bodyBytes ?: body.toByteArray(Charsets.UTF_8)).toRequestBody(ct.toMediaType()))
        } else {
            builder.method(m, null)
        }.build()
    } catch (_: IllegalArgumentException) {
        return@withContext failed(url, "Invalid request")
    }

    val http = if (followRedirects) client else client.newBuilder().followRedirects(false).build()
    val call = http.newCall(request)
    val watcher = coroutineContext[Job]?.invokeOnCompletion { if (it is CancellationException) call.cancel() }
    try {
        call.execute().use { response ->
            val contentType = response.body?.contentType()
            val read = response.body?.byteStream()?.use { readAtMost(it, maxResponseBodyBytes.coerceAtLeast(0)) }
                ?: Limited(ByteArray(0), false)
            val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val decoded = runCatching { String(read.bytes, charset) }.getOrElse { String(read.bytes, Charsets.UTF_8) }
            RawHttpResponse(
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                body = if (read.truncated) "$decoded\n...[truncated]" else decoded,
                bodyBytes = read.bytes,
                headers = response.headers.toMultimap()
                    .mapValues { (_, v) -> v.joinToString(",") }
                    .mapKeys { (k, _) -> k.lowercase() },
            )
        }
    } catch (_: IOException) {
        failed(url, "Network error")
    } finally {
        watcher?.dispose()
    }
}

private fun failed(url: String, reason: String) =
    RawHttpResponse(status = 0, statusText = reason, url = url, body = "", headers = emptyMap())

private val WITH_BODY = setOf("POST", "PUT", "PATCH", "DELETE")
