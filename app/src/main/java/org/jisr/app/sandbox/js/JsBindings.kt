package org.jisr.app.sandbox.js

import org.jisr.app.sandbox.js.polyfill.timerPolyfill
import org.jisr.app.sandbox.js.polyfill.abortControllerPolyfill
import org.jisr.app.sandbox.js.polyfill.fetchPolyfill
import org.jisr.app.sandbox.js.polyfill.base64Polyfill
import org.jisr.app.sandbox.js.polyfill.textEncoderPolyfill
import org.jisr.app.sandbox.js.polyfill.urlPolyfill
import org.jisr.app.sandbox.js.polyfill.cryptoPolyfill
import org.jisr.app.sandbox.js.polyfill.cheerioPolyfill
import org.jisr.app.sandbox.js.polyfill.requirePolyfill
import org.jisr.app.sandbox.js.polyfill.arrayPolyfill
import org.jisr.app.sandbox.js.polyfill.objectPolyfill
import org.jisr.app.sandbox.js.polyfill.stringPolyfill

internal object JsBindings {
    val staticPolyfillCode: String = """
            globalThis.SCRAPER_ID = __get_scraper_id();
            globalThis.SCRAPER_SETTINGS = JSON.parse(__get_scraper_settings());
            if (typeof TMDB_API_KEY === 'undefined') {
                globalThis.TMDB_API_KEY = __get_tmdb_api_key();
            }
            if (typeof globalThis.global === 'undefined') globalThis.global = globalThis;
            if (typeof globalThis.window === 'undefined') globalThis.window = globalThis;
            if (typeof globalThis.self === 'undefined') globalThis.self = globalThis;

            ${timerPolyfill()}
            ${fetchPolyfill()}
            ${abortControllerPolyfill()}
            ${base64Polyfill()}
            ${urlPolyfill()}
            ${cryptoPolyfill()}
            ${textEncoderPolyfill()}
            ${cheerioPolyfill()}
            ${requirePolyfill()}
            ${arrayPolyfill()}
            ${objectPolyfill()}
            ${stringPolyfill()}
        """.trimIndent()

    val staticCallCode: String = """
            (async function() {
                try {
                    var getStreams = module.exports.getStreams || globalThis.getStreams;
                    if (!getStreams) {
                        __capture_result(JSON.stringify([]));
                        return;
                    }
                    var args = JSON.parse(__get_call_args());
                    var season = args.season == null ? undefined : args.season;
                    var episode = args.episode == null ? undefined : args.episode;
                    var result = await getStreams(args.tmdbId, args.mediaType, season, episode);
                    __capture_result(JSON.stringify(result || []));
                } catch (e) {
                    __capture_result(JSON.stringify([]));
                }
            })();
        """.trimIndent()

    val staticSettingsCallCode: String = """
            (async function() {
                try {
                    var onSettings = (typeof module !== 'undefined' && module.exports && module.exports.onSettings) || globalThis.onSettings;
                    if (typeof onSettings === 'function') {
                        var layout = await onSettings();
                        __capture_result(JSON.stringify(layout || []));
                    } else {
                        __capture_result("[]");
                    }
                } catch (e) {
                    __capture_result("[]");
                }
            })();
        """.trimIndent()
}
