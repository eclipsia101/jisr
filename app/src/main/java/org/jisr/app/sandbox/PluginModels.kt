package org.jisr.app.sandbox

data class PluginRuntimeResult(
    val title: String,
    val name: String? = null,
    val url: String,
    val quality: String? = null,
    val size: String? = null,
    val language: String? = null,
    val provider: String? = null,
    val type: String? = null,
    val seeders: Int? = null,
    val peers: Int? = null,
    val infoHash: String? = null,
    val headers: Map<String, String>? = null,
    val subtitles: List<PluginSubtitleResult>? = null,
)

data class PluginSubtitleResult(
    val url: String,
    val language: String,
    val name: String? = null,
    val headers: Map<String, String>? = null,
)

data class PluginSettingOption(val value: String, val label: String)

data class PluginSettingField(
    val type: String,
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val placeholder: String = "",
    val isPassword: Boolean = false,

    val defaultText: String = "",
    val defaultBool: Boolean = false,
    val options: List<PluginSettingOption> = emptyList(),
)
