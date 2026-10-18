package org.jisr.app.data

import org.jisr.app.sandbox.PluginRuntimeResult

enum class Resolution(val id: String, val label: String) {
    P2160("2160p", "2160p (4K)"),
    P1440("1440p", "1440p (2K)"),
    P1080("1080p", "1080p"),
    P720("720p", "720p"),
    P480("480p", "480p"),
    P360("360p", "360p"),
    OTHERS("others", "Others");

    companion object {

        val DEFAULT_IDS: Set<String> = entries.map { it.id }.toSet()

        private val rules = listOf(
            P2160 to Regex("(?<![0-9])(2160|4320)\\s*p?(?![0-9])|\\b(4k|uhd)\\b", RegexOption.IGNORE_CASE),
            P1440 to Regex("(?<![0-9])1440\\s*p?(?![0-9])|\\b(2k|qhd)\\b", RegexOption.IGNORE_CASE),
            P1080 to Regex("(?<![0-9])1080\\s*[pi]?(?![0-9])|\\b(fhd|full\\s*hd)\\b", RegexOption.IGNORE_CASE),
            P720 to Regex("(?<![0-9])720\\s*p?(?![0-9])|\\bhd\\b", RegexOption.IGNORE_CASE),
            P480 to Regex("(?<![0-9])(480|576)\\s*p?(?![0-9])|\\bsd\\b", RegexOption.IGNORE_CASE),
            P360 to Regex("(?<![0-9])360\\s*p?(?![0-9])", RegexOption.IGNORE_CASE),
        )

        fun fromText(text: String?): Resolution? =
            text?.takeIf { it.isNotBlank() }?.let { t -> rules.firstOrNull { (_, rx) -> rx.containsMatchIn(t) }?.first }

        fun of(result: PluginRuntimeResult): Resolution =
            fromText(result.quality) ?: fromText(result.name) ?: fromText(result.title) ?: OTHERS
    }
}
