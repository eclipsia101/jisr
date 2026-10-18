package org.jisr.app.net

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

internal object NetGuard {
    fun isForbidden(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val b = address.address
        return when (address) {
            is Inet4Address -> {
                val a = b[0].toInt() and 0xFF
                val c = b[1].toInt() and 0xFF
                val d = b[2].toInt() and 0xFF
                a == 0 ||
                    (a == 100 && c in 64..127) ||
                    (a == 192 && c == 0 && d == 0) ||
                    (a == 198 && (c == 18 || c == 19)) ||
                    a >= 240
            }
            is Inet6Address -> (b[0].toInt() and 0xFE) == 0xFC
            else -> true
        }
    }

    private class CachedAnswer(val addresses: List<InetAddress>, val expiresAtNanos: Long)

    private val answers = ConcurrentHashMap<String, CachedAnswer>()
    private const val ANSWER_TTL_NANOS = 60L * 1_000_000_000L
    private const val MAX_CACHED_HOSTS = 512

    @Volatile
    var upstream: Dns = Dns.SYSTEM
        set(value) {
            field = value
            answers.clear()
        }

    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val resolver = upstream
            val cacheable = resolver !== Dns.SYSTEM
            val key = hostname.lowercase()
            if (cacheable) {
                val hit = answers[key]
                if (hit != null && System.nanoTime() < hit.expiresAtNanos) return hit.addresses
            }
            val allowed = resolver.lookup(hostname)
                .filterNot(::isForbidden)
                .sortedBy { if (it is Inet4Address) 0 else 1 }
            if (allowed.isEmpty()) throw UnknownHostException("Blocked destination")
            if (cacheable && resolver === upstream) {
                if (answers.size >= MAX_CACHED_HOSTS) answers.clear()
                answers[key] = CachedAnswer(allowed, System.nanoTime() + ANSWER_TTL_NANOS)
            }
            return allowed
        }
    }

    val interceptor: Interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val connected = chain.connection()?.socket()?.inetAddress
            if (connected != null && isForbidden(connected)) {
                chain.call().cancel()
                throw IOException("Blocked destination")
            }
            return chain.proceed(chain.request())
        }
    }
}
