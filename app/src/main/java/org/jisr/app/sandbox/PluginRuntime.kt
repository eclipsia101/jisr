package org.jisr.app.sandbox

import android.os.Process
import com.dokar.quickjs.QuickJs
import org.jisr.app.sandbox.crypto.CryptoBridge
import org.jisr.app.sandbox.dom.DomBridge
import org.jisr.app.sandbox.host.HostApiRegistry
import org.jisr.app.sandbox.host.HostFunctions
import org.jisr.app.sandbox.js.JsBindings
import org.jisr.app.sandbox.js.JsRuntime
import org.jisr.app.sandbox.network.FetchBridge
import org.jisr.app.sandbox.network.UrlBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.Executors

internal const val MAX_CONCURRENT_PLUGINS = 64

internal const val PLUGIN_TIMEOUT_MS = 60_000L

internal val pluginDispatcher: CoroutineDispatcher =
    Executors.newFixedThreadPool(MAX_CONCURRENT_PLUGINS) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "sandbox-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

internal const val PLUGIN_MEMORY_LIMIT_BYTES = 128L * 1024 * 1024

internal const val PLUGIN_MAX_STACK_BYTES = 1L * 1024 * 1024

internal fun QuickJs.configurePluginRuntime() {
    evaluationTimeoutMillis = PLUGIN_TIMEOUT_MS
    memoryLimit = PLUGIN_MEMORY_LIMIT_BYTES
    maxStackSize = PLUGIN_MAX_STACK_BYTES
}

internal object PluginRuntime {
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Semaphore(MAX_CONCURRENT_PLUGINS)

    suspend fun execute(
        code: String,
        pluginId: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        tmdbKey: String,
        settingsJson: String = "{}",
    ): List<PluginRuntimeResult> = gate.withPermit {
        withContext(pluginDispatcher) {
            withTimeout(PLUGIN_TIMEOUT_MS) {
                run(code, pluginId, tmdbId, mediaType, season, episode, tmdbKey, settingsJson)
            }
        }
    }

    suspend fun settingsLayout(code: String, pluginId: String, tmdbKey: String): List<PluginSettingField> = gate.withPermit {
        withContext(pluginDispatcher) {
            withTimeout(PLUGIN_TIMEOUT_MS) {
                runSettings(code, pluginId, tmdbKey)
            }
        }
    }

    private suspend fun runSettings(code: String, pluginId: String, tmdbKey: String): List<PluginSettingField> {
        val deferred = CompletableDeferred<String>()
        val registry = HostApiRegistry().apply {
            addModule(HostFunctions(pluginId, "{}", "{}", tmdbApiKey = tmdbKey) { deferred.complete(it) })
            addModule(FetchBridge(tmdbKey))
            addModule(UrlBridge())
            addModule(CryptoBridge())
        }
        JsRuntime().use {
            registry.registerAll(this)
            evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
            evaluate<Any?>(wrap(code))
            evaluateCached({ JsRuntime.settingsCallBytecode(this) }, JsBindings.staticSettingsCallCode)
            deferred.await()
        }
        return parseSettingsLayout(deferred.await())
    }

    private val settingTypes = setOf("header", "info", "text", "toggle", "select")

    private fun parseSettingsLayout(raw: String): List<PluginSettingField> = runCatching {
        val array = json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
        array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String): String =
                (o[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }.orEmpty()
            val type = (o["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.ifBlank { null } ?: "info"
            if (type !in settingTypes) return@mapNotNull null
            val key = text("key")
            if ((type == "text" || type == "toggle" || type == "select") && key.isBlank()) return@mapNotNull null
            val default = o["defaultValue"] as? JsonPrimitive
            PluginSettingField(
                type = type,
                key = key,
                label = text("label"),
                description = text("description"),
                placeholder = text("placeholder"),
                isPassword = (o["isPassword"] as? JsonPrimitive)?.booleanOrNull == true,
                defaultText = default?.contentOrNull?.takeIf { it != "null" }.orEmpty(),
                defaultBool = default?.booleanOrNull == true,
                options = (o["options"] as? JsonArray).orEmpty().mapNotNull { opt ->
                    val oo = opt as? JsonObject ?: return@mapNotNull null
                    val value = (oo["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    PluginSettingOption(value, (oo["label"] as? JsonPrimitive)?.contentOrNull?.ifBlank { null } ?: value)
                },
            )
        }
    }.getOrElse { emptyList() }

    private suspend fun run(
        code: String,
        pluginId: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        tmdbKey: String,
        settingsJson: String,
    ): List<PluginRuntimeResult> {
        val deferred = CompletableDeferred<String>()
        val callArgs = JsonObject(
            mapOf(
                "tmdbId" to JsonPrimitive(tmdbId),
                "mediaType" to JsonPrimitive(mediaType),
                "season" to (season?.let(::JsonPrimitive) ?: JsonNull),
                "episode" to (episode?.let(::JsonPrimitive) ?: JsonNull),
            ),
        ).toString()

        val dom = DomBridge()
        val registry = HostApiRegistry().apply {
            addModule(
                HostFunctions(pluginId, settingsJson, callArgs, tmdbApiKey = tmdbKey) {
                    deferred.complete(it)
                },
            )
            addModule(FetchBridge(tmdbKey))
            addModule(UrlBridge())
            addModule(CryptoBridge())
            addModule(dom)
        }

        try {
            JsRuntime().use {
                registry.registerAll(this)
                evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
                evaluate<Any?>(wrap(code))
                evaluateCached({ JsRuntime.callBytecode(this) }, JsBindings.staticCallCode)
                deferred.await()
            }
            return parse(deferred.await())
        } finally {
            dom.clear()
        }
    }

    private fun wrap(code: String): String = """
        var module = { exports: {} };
        var exports = module.exports;
        (function() {
            $code
        })();
    """.trimIndent()

    private suspend fun QuickJs.evaluateCached(bytecode: QuickJs.() -> ByteArray, source: String) {
        val compiled = runCatching { bytecode() }.getOrNull()
        if (compiled != null) evaluate<Any?>(compiled) else evaluate<Any?>(source)
    }

    private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonObject.str(key: String): String? =
        this[key].prim()?.contentOrNull?.takeIf { it.isNotBlank() && !it.contains("[object") }

    private fun headersOf(obj: JsonObject?): Map<String, String>? =
        obj?.mapNotNull { (k, v) -> v.prim()?.contentOrNull?.let { k to it } }
            ?.toMap()?.takeIf { it.isNotEmpty() }

    private fun parse(raw: String): List<PluginRuntimeResult> {
        val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()

        return array.mapNotNull { element -> runCatching { parseResult(element) }.getOrNull() }
    }

    private fun parseResult(element: JsonElement): PluginRuntimeResult? {
        val item = element as? JsonObject ?: return null
        val url = when (val u = item["url"]) {
            is JsonPrimitive -> u.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> u.str("url")
            else -> null
        } ?: return null

        val subtitles = (item["subtitles"] as? JsonArray)?.mapNotNull { s ->
            runCatching {
                val o = s as? JsonObject ?: return@runCatching null
                val su = o["url"].prim()?.contentOrNull ?: return@runCatching null
                PluginSubtitleResult(
                    url = su,
                    language = o["language"].prim()?.contentOrNull ?: "Unknown",
                    name = o["name"].prim()?.contentOrNull,
                    headers = headersOf(o["headers"] as? JsonObject),
                )
            }.getOrNull()
        }?.takeIf { it.isNotEmpty() }

        return PluginRuntimeResult(
            title = item.str("title") ?: item.str("name") ?: "Unknown",
            name = item.str("name"),
            url = url,
            quality = item.str("quality"),
            size = item.str("size"),
            language = item.str("language"),
            provider = item.str("provider"),
            type = item.str("type"),
            seeders = item["seeders"].prim()?.intOrNull,
            peers = item["peers"].prim()?.intOrNull,
            infoHash = item.str("infoHash"),
            headers = headersOf(item["headers"] as? JsonObject),
            subtitles = subtitles,
        )
    }
}
