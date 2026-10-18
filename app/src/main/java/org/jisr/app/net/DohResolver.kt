package org.jisr.app.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.Proxy

enum class DohProvider(val id: String, val label: String, val url: String?, val bootstrap: List<String>) {
    Off("off", "System DNS", null, emptyList()),
    Cloudflare("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    Quad9("quad9", "Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    Custom("custom", "Custom", null, emptyList());

    companion object {
        fun fromId(id: String?): DohProvider = entries.firstOrNull { it.id == id } ?: Cloudflare
    }
}

fun isValidDohUrl(url: String): Boolean =
    url.toHttpUrlOrNull()?.let { it.isHttps && it.host.isNotBlank() } == true

object DohResolver {

    private val bootstrapClient = OkHttpClient.Builder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .cache(null)
        .proxy(Proxy.NO_PROXY)
        .build()

    fun configure(provider: DohProvider, customUrl: String) {
        NetGuard.upstream = when (provider) {
            DohProvider.Off -> Dns.SYSTEM
            DohProvider.Custom -> customUrl.trim().takeIf(::isValidDohUrl)?.let { build(it, emptyList()) } ?: FAIL_CLOSED
            else -> build(provider.url!!, provider.bootstrap)
        }
    }

    private fun build(url: String, bootstrap: List<String>): Dns =
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrlOrNull()!!)
            .includeIPv6(true)
            .apply { if (bootstrap.isNotEmpty()) bootstrapDnsHosts(bootstrap.map { InetAddress.getByName(it) }) }
            .build()

    private val FAIL_CLOSED = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            throw java.net.UnknownHostException("DNS over HTTPS is misconfigured")
    }
}
