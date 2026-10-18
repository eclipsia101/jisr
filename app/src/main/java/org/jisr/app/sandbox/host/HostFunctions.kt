package org.jisr.app.sandbox.host

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.delay

private const val MAX_PLUGIN_TIMER_DELAY_MS = 60_000L

internal class HostFunctions(
    private val scraperId: String,
    private val scraperSettingsJson: String,
    private val callArgsJson: String = "{}",

    private val tmdbApiKey: String,
    private val onResult: (String) -> Unit,
) : HostModule {
    override fun register(runtime: QuickJs) {
        runtime.asyncFunction("__plugin_sleep") { args: Array<Any?> ->
            val durationMs = (args.getOrNull(0) as? Number)
                ?.toLong()
                ?.coerceIn(0L, MAX_PLUGIN_TIMER_DELAY_MS)
                ?: 0L
            delay(durationMs)
            null
        }

        runtime.define("console") {
            function("log") { null }
            function("error") { null }
            function("warn") { null }
            function("info") { null }
            function("debug") { null }
        }

        runtime.function("__get_scraper_id") { scraperId }
        runtime.function("__get_scraper_settings") { scraperSettingsJson }
        runtime.function("__get_tmdb_api_key") { tmdbApiKey }
        runtime.function("__get_call_args") { callArgsJson }
        runtime.function("__capture_result") { args ->
            onResult(args.getOrNull(0)?.toString() ?: "[]")
            null
        }
    }
}
