package org.jisr.app.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HttpReply(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val contentType: String = "application/json; charset=utf-8",
)

private class ParsedRequest(val method: String, val path: String)

class LocalHttpServer(
    private val address: InetAddress,
    private val port: Int,
    private val handler: suspend (method: String, path: String) -> HttpReply,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private val active = AtomicInteger(0)
    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    @Throws(IOException::class)
    fun start() {
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(address, port), BACKLOG)
        }
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverSocket = socket
        scope = serverScope
        serverScope.launch { acceptLoop(socket, serverScope) }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun acceptLoop(socket: ServerSocket, serverScope: CoroutineScope) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            if (active.incrementAndGet() > MAX_CONNECTIONS) {
                active.decrementAndGet()
                runCatching { client.close() }
                continue
            }
            clients.add(client)
            serverScope.launch {
                try {
                    serve(client)
                } finally {
                    active.decrementAndGet()
                    clients.remove(client)
                    runCatching { client.close() }
                }
            }
        }
    }

    private suspend fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val parsed = try {
            readRequest(BufferedInputStream(client.getInputStream(), 4096))
        } catch (_: IOException) {
            return
        }
        val reply = when {
            parsed == null -> HttpReply(400)
            parsed.method != "GET" && parsed.method != "HEAD" && parsed.method != "OPTIONS" -> HttpReply(405)
            else -> try {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    handler(if (parsed.method == "HEAD") "GET" else parsed.method, parsed.path)
                } ?: HttpReply(504)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                HttpReply(500)
            }
        }
        runCatching { write(client, if (parsed?.method == "HEAD") HttpReply(reply.status, ByteArray(0), reply.contentType) else reply) }
    }

    private fun readRequest(input: InputStream): ParsedRequest? {
        val buffer = ByteArray(MAX_HEAD_BYTES)
        var length = 0
        var terminated = false
        val deadline = System.nanoTime() + HEAD_TIMEOUT_NANOS
        while (length < buffer.size) {
            if (System.nanoTime() > deadline) throw IOException("head timeout")
            val next = input.read()
            if (next < 0) {
                if (length == 0) throw IOException("closed")
                return null
            }
            buffer[length++] = next.toByte()
            val crlf = length >= 4 &&
                buffer[length - 4] == CR && buffer[length - 3] == LF &&
                buffer[length - 2] == CR && buffer[length - 1] == LF
            val bareLf = length >= 2 && buffer[length - 2] == LF && buffer[length - 1] == LF
            if (crlf || bareLf) {
                terminated = true
                break
            }
        }
        if (!terminated) return null

        val head = String(buffer, 0, length, Charsets.ISO_8859_1)
        val requestLine = head.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val parts = requestLine.split(Regex("\\s+"))
        if (parts.size < 2) return null
        var target = parts[1]

        if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
            target = "/" + target.substringAfter("://").substringAfter('/', "")
        }
        target = target.substringBefore('?').substringBefore('#')
        if (!target.startsWith("/")) return null
        val path = runCatching { URLDecoder.decode(target.replace("+", "%2B"), "UTF-8") }.getOrDefault(target)
        return ParsedRequest(parts[0].uppercase(), path)
    }

    private fun write(client: Socket, reply: HttpReply) {
        val statusText = when (reply.status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            504 -> "Gateway Timeout"
            else -> "Error"
        }
        val head = buildString {
            append("HTTP/1.1 ").append(reply.status).append(' ').append(statusText).append("\r\n")
            append("Content-Type: ").append(reply.contentType).append("\r\n")
            append("Content-Length: ").append(reply.body.size).append("\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("Access-Control-Allow-Methods: GET, OPTIONS\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(reply.body)
            flush()
        }
    }

    private companion object {
        const val BACKLOG = 32
        const val MAX_CONNECTIONS = 32
        const val MAX_HEAD_BYTES = 64 * 1024
        const val READ_TIMEOUT_MS = 10_000
        const val HEAD_TIMEOUT_NANOS = 15_000_000_000L
        const val REQUEST_TIMEOUT_MS = 330_000L
        const val CR: Byte = 13
        const val LF: Byte = 10
    }
}
