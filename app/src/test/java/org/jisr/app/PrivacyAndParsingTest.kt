package org.jisr.app

import org.jisr.app.data.normalizeManifestUrl
import org.jisr.app.data.parseMediaId
import org.jisr.app.data.parseRepositoryManifest
import org.jisr.app.data.resolveScriptUrl
import org.jisr.app.data.AppSettings
import org.jisr.app.net.NetGuard
import org.jisr.app.sandbox.crypto.MAX_PBKDF2_ITERATIONS
import org.jisr.app.sandbox.crypto.MAX_RANDOM_BYTES
import org.jisr.app.sandbox.crypto.pluginGetRandomValues
import org.jisr.app.sandbox.crypto.pluginPbkdf2
import org.jisr.app.data.MAX_MANIFEST_SCRAPERS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PrivacyAndParsingTest {
    private fun forbidden(ip: String) = NetGuard.isForbidden(InetAddress.getByName(ip))

    @Test fun blocksLoopbackAndPrivateRanges() {
        listOf(
            "127.0.0.1", "127.1.2.3", "0.0.0.0", "10.0.0.5", "172.16.4.4", "192.168.1.10",
            "169.254.169.254", "100.64.1.1", "100.127.255.255", "198.18.0.1", "224.0.0.1",
            "::1", "fe80::1", "fd00::1", "::ffff:192.168.0.1",
        ).forEach { assertTrue("$it should be blocked", forbidden(it)) }
    }

    @Test fun allowsPublicAddresses() {
        listOf("1.1.1.1", "8.8.8.8", "93.184.216.34", "100.63.255.255", "2606:4700:4700::1111")
            .forEach { assertFalse("$it should be allowed", forbidden(it)) }
    }

    @Test fun parsesImdbAndTmdbIds() {
        assertEquals("tt0133093", parseMediaId("tt0133093")?.base)
        val ep = parseMediaId("tt0944947:2:5")!!
        assertEquals(2, ep.season); assertEquals(5, ep.episode)
        assertEquals("tmdb:603", parseMediaId("tmdb:603")?.base)
        assertEquals(3, parseMediaId("tmdb:1399:1:3")?.episode)
    }

    @Test fun rejectsMalformedIds() {
        assertNull(parseMediaId(""))
        assertNull(parseMediaId("../../etc/passwd"))
        assertNull(parseMediaId("tt"))
        assertNull(parseMediaId("tmdb:abc"))
        assertNull(parseMediaId("tt12345678901"))
    }

    @Test fun normalizesManifestUrls() {
        val raw = "https://codeberg.org/api/v1/repos/eclipsia-plug/nuvio-plugin/raw/manifest.json"
        assertEquals(raw, normalizeManifestUrl(raw))
        assertEquals("https://example.com/repo/manifest.json", normalizeManifestUrl(" example.com/repo/ "))
        assertEquals("https://example.com/manifest.json?a=1", normalizeManifestUrl("https://example.com?a=1#x"))
        assertNull(normalizeManifestUrl("ftp://example.com/manifest.json"))
        assertNull(normalizeManifestUrl("https:///manifest.json"))
        assertNull(normalizeManifestUrl("not a url"))
    }

    @Test fun resolvesScriptUrls() {
        val m = "https://host/raw/manifest.json?token=1"
        assertEquals("https://host/raw/providers/a.js", resolveScriptUrl(m, "providers/a.js"))
        assertEquals("https://host/raw/a.js", resolveScriptUrl(m, "/a.js"))
        assertEquals("https://other/a.js", resolveScriptUrl(m, "https://other/a.js"))
    }

    @Test fun parsesNuvioManifest() {
        val manifest = parseRepositoryManifest(
            """{"name":"R","version":"1.0","scrapers":[
              {"id":"a","name":"A","version":"1","filename":"a.js","supportedTypes":["movie","series"]},
              {"id":"off","name":"Off","version":"1","filename":"o.js","enabled":false},
              {"id":"ios","name":"Ios","version":"1","filename":"i.js","supportedPlatforms":["ios"]},
              {"id":"","name":"NoId","version":"1","filename":"n.js"}]}""",
        )!!
        assertEquals("R", manifest.name)

        assertEquals(listOf("a", "off"), manifest.scrapers.map { it.id })
        assertEquals(listOf(true, false), manifest.scrapers.map { it.enabled })
        assertEquals(listOf("movie", "tv"), manifest.scrapers[0].supportedTypes)
        assertNull(parseRepositoryManifest("[]"))
        assertNull(parseRepositoryManifest("{\"name\":\"x\"}"))
    }

    @Test fun concurrencyBoundsAndStep() {
        assertEquals(27, AppSettings().concurrency)
        assertEquals(64, AppSettings.MAX_CONCURRENCY)
        assertEquals(3, AppSettings.CONCURRENCY_STEP)

        assertEquals(64, (63 + AppSettings.CONCURRENCY_STEP).coerceIn(AppSettings.MIN_CONCURRENCY, AppSettings.MAX_CONCURRENCY))
        assertEquals(3, (3 - AppSettings.CONCURRENCY_STEP).coerceIn(AppSettings.MIN_CONCURRENCY, AppSettings.MAX_CONCURRENCY))
    }

    @Test fun streamLimitRange() {
        assertEquals(AppSettings.UNLIMITED_STREAMS, AppSettings().maxStreamsPerProvider)
        assertEquals(1, AppSettings.stepStreams(1, -1))
        assertEquals(9, AppSettings.stepStreams(10, -1))
        assertEquals(0, AppSettings.stepStreams(10, +1))
        assertEquals(10, AppSettings.stepStreams(0, -1))
        assertEquals(0, AppSettings.stepStreams(0, +1))
        assertEquals(15, AppSettings().globalTimeoutSec)
    }

    @Test fun detectsResolutions() {
        assertEquals(org.jisr.app.data.Resolution.P2160, org.jisr.app.data.Resolution.fromText("2160p HDR"))
        assertEquals(org.jisr.app.data.Resolution.P2160, org.jisr.app.data.Resolution.fromText("4K"))
        assertEquals(org.jisr.app.data.Resolution.P1440, org.jisr.app.data.Resolution.fromText("1440p"))
        assertEquals(org.jisr.app.data.Resolution.P1080, org.jisr.app.data.Resolution.fromText("Movie.1080p.WEB"))
        assertEquals(org.jisr.app.data.Resolution.P720, org.jisr.app.data.Resolution.fromText("720p"))
        assertEquals(org.jisr.app.data.Resolution.P480, org.jisr.app.data.Resolution.fromText("480p"))
        assertEquals(org.jisr.app.data.Resolution.P360, org.jisr.app.data.Resolution.fromText("360p"))
        assertNull(org.jisr.app.data.Resolution.fromText("Auto"))
        assertNull(org.jisr.app.data.Resolution.fromText("11080p"))
        assertEquals(org.jisr.app.data.Resolution.entries.map { it.id }.toSet(), AppSettings().enabledResolutions)
    }

    @Test fun readsHasSettingsFromManifest() {
        val manifest = parseRepositoryManifest(
            """{"name":"R","scrapers":[
                {"id":"a","filename":"a.js","hasSettings":true},
                {"id":"b","filename":"b.js"}
            ]}""",
        )!!
        assertEquals(listOf(true, false), manifest.scrapers.map { it.hasSettings })
    }

    @Test fun savedSettingsKeepOnlyStringsAndBooleans() {
        val parsed = org.jisr.app.data.parseSavedSettings("""{"a":"x","b":true,"c":5,"d":null,"e":[1]}""")
        assertEquals(setOf("a", "b"), parsed.keys)
        assertTrue(org.jisr.app.data.parseSavedSettings(null).isEmpty())
        assertTrue(org.jisr.app.data.parseSavedSettings("not json").isEmpty())
    }

    private fun assertRejected(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("expected IllegalArgumentException")
    }

    @Test fun randomValuesAreCapped() {
        assertEquals(MAX_RANDOM_BYTES, pluginGetRandomValues(MAX_RANDOM_BYTES).size)
        assertRejected { pluginGetRandomValues(MAX_RANDOM_BYTES + 1) }
        assertRejected { pluginGetRandomValues(2_000_000_000) }
        assertRejected { pluginGetRandomValues(-1) }
    }

    @Test fun pbkdf2WorkIsCapped() {
        val pw = "pw".toByteArray(); val salt = "salt".toByteArray()
        assertEquals(32, pluginPbkdf2(pw, salt, 1_000, 256, "SHA256").size)
        assertRejected { pluginPbkdf2(pw, salt, Int.MAX_VALUE, 256, "SHA256") }
        assertRejected { pluginPbkdf2(pw, salt, MAX_PBKDF2_ITERATIONS + 1, 256, "SHA256") }
        assertRejected { pluginPbkdf2(pw, salt, 1_000, Int.MAX_VALUE - 7, "SHA256") }
        assertRejected { pluginPbkdf2(pw, salt, MAX_PBKDF2_ITERATIONS, 4_096, "SHA1") }
    }

    @Test fun manifestPluginCountIsCapped() {
        val items = (1..(MAX_MANIFEST_SCRAPERS + 50)).joinToString(",") { """{"id":"p$it","filename":"p$it.js"}""" }
        val manifest = parseRepositoryManifest("""{"name":"r","scrapers":[$items]}""")!!
        assertEquals(MAX_MANIFEST_SCRAPERS, manifest.scrapers.size)
    }
}
