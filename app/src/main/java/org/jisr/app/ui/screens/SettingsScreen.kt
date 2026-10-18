package org.jisr.app.ui.screens

import androidx.activity.compose.BackHandler
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jisr.app.data.AppSettings
import org.jisr.app.data.Resolution
import org.jisr.app.net.DohProvider
import org.jisr.app.net.isValidDohUrl
import org.jisr.app.ui.components.EcButton
import org.jisr.app.ui.components.EcButtonStyle
import org.jisr.app.ui.components.EcCard
import org.jisr.app.ui.components.EcChip
import org.jisr.app.ui.components.EcChipFlow
import org.jisr.app.ui.components.EcDialog
import org.jisr.app.ui.components.EcDivider
import org.jisr.app.ui.components.EcFootnote
import org.jisr.app.ui.components.EcHeader
import org.jisr.app.ui.components.EcIconInset
import org.jisr.app.ui.components.EcNavRow
import org.jisr.app.ui.components.EcRow
import org.jisr.app.ui.components.EcScreen
import org.jisr.app.ui.components.EcStepperRow
import org.jisr.app.ui.components.EcSwitchRow
import org.jisr.app.ui.components.EcTextField
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcType

private enum class SettingsPage(val title: String) {
    Root("Settings"),
    Tmdb("TMDB"),
    Server("Server"),
    Plugins("Plugins"),
    Resolution("Resolution filter"),
    Dns("DNS over HTTPS"),
    Data("Data"),
    About("About"),
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    serverRunning: Boolean,
    onTmdbKey: (String) -> Unit,
    tmdbKeyError: Boolean = false,
    onDismissTmdbKeyError: () -> Unit = {},
    onPort: (Int) -> Unit,
    onLocalNetwork: (Boolean) -> Unit,
    onBackgroundRun: (Boolean) -> Unit,
    onConcurrency: (Int) -> Unit,
    onResolution: (String, Boolean) -> Unit,
    onGlobalTimeout: (Int) -> Unit,
    onMaxStreams: (Int) -> Unit,
    onDohProvider: (String) -> Unit,
    onDohCustomUrl: (String) -> Unit,
    onWipe: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.Root.name) }
    val page = SettingsPage.valueOf(pageName)
    fun goTo(target: SettingsPage) { pageName = target.name }
    val back = { pageName = SettingsPage.Root.name }

    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var dohUrl by remember { mutableStateOf(settings.dohCustomUrl) }
    var confirmWipe by remember { mutableStateOf(false) }

    LaunchedEffect(settings.port) { port = settings.port.toString() }

    BackHandler(enabled = page != SettingsPage.Root, onBack = back)

    run {
        val current = page
        when (current) {
            SettingsPage.Root -> RootPage(settings, listState, ::goTo)

            SettingsPage.Tmdb -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item {
                    EcCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("API key", style = EcType.Body, fontWeight = FontWeight.Medium, color = EcColors.TextPrimary)
                            Spacer(Modifier.height(10.dp))
                            EcTextField(
                                value = key,
                                onValueChange = { key = it; if (tmdbKeyError) onDismissTmdbKeyError() },
                                placeholder = if (settings.customTmdbKey) "Saved (hidden)" else "Using built-in key",
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                onDone = { if (key.isNotBlank()) { onTmdbKey(key); key = "" } },
                            )
                        }
                    }
                }
                if (tmdbKeyError) {
                    item {
                        Text(
                            "Unable to save the key. Secure key storage failed to encrypt it. The previous key remains active.",
                            style = EcType.Small,
                            color = EcColors.Danger,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                item {
                    EcFootnote(
                        "Jisr includes a built-in key and uses your own when one is saved.\nPersonal TMDb key is recommended",
                    )
                }
                if (settings.customTmdbKey) {
                    item { EcButton("Remove my key (use built-in)", onClick = { onTmdbKey("") }, style = EcButtonStyle.Danger) }
                } else if (key.isNotBlank()) {
                    item { EcButton("Save key", onClick = { onTmdbKey(key); key = "" }) }
                }
            }

            SettingsPage.Server -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item {
                    EcCard {
                        EcSwitchRow(
                            title = "Local network access",
                            description = if (settings.localNetwork) "Accessible to devices on your network" else "Enable to use plugins on other devices",
                            icon = Icons.Rounded.Wifi,
                            checked = settings.localNetwork,
                            onCheckedChange = onLocalNetwork,
                        )
                        EcDivider()
                        EcSwitchRow(
                            title = "Run in background",
                            description = if (settings.backgroundRun) "Server keeps running when the app is closed" else "Server stops when the app is closed",
                            icon = Icons.Rounded.Bolt,
                            checked = settings.backgroundRun,
                            onCheckedChange = onBackgroundRun,
                        )
                        if (settings.backgroundRun) {
                            EcDivider()
                            EcNavRow(
                                title = "Battery optimization",
                                description = "Allow unrestricted background activity",
                                icon = Icons.Rounded.Timer,
                                onClick = {
                                    val pm = context.getSystemService(PowerManager::class.java)
                                    val intent = if (pm.isIgnoringBatteryOptimizations(context.packageName))
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                },
                            )
                        }
                        EcDivider()
                        Column(Modifier.padding(16.dp)) {
                            Text("Port", style = EcType.Body, fontWeight = FontWeight.Medium, color = EcColors.TextPrimary)
                            Spacer(Modifier.height(10.dp))
                            EcTextField(
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                                placeholder = "Port",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                onDone = { port.toIntOrNull()?.let(onPort) },
                            )
                        }
                    }
                }
                item {
                    EcFootnote(
                            "Changing the network setting or port restarts a running server.",
                    )
                }
            }

            SettingsPage.Plugins -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item {
                    EcCard {
                        EcStepperRow(
                            title = "Concurrency",
                            description = "Higher value is faster but increases memory use",
                            icon = Icons.Rounded.Bolt,
                            value = settings.concurrency.toString(),
                            onMinus = { onConcurrency(settings.concurrency - AppSettings.CONCURRENCY_STEP) },
                            onPlus = { onConcurrency(settings.concurrency + AppSettings.CONCURRENCY_STEP) },
                            minusEnabled = settings.concurrency > AppSettings.MIN_CONCURRENCY,
                            plusEnabled = settings.concurrency < AppSettings.MAX_CONCURRENCY,
                        )
                        EcDivider(EcIconInset)
                        EcStepperRow(
                            title = "Timeout",
                            description = "Maximum wait for plugins",
                            icon = Icons.Rounded.Timer,
                            value = "${settings.globalTimeoutSec} s",
                            onMinus = { onGlobalTimeout(settings.globalTimeoutSec - AppSettings.TIMEOUT_STEP_SEC) },
                            onPlus = { onGlobalTimeout(settings.globalTimeoutSec + AppSettings.TIMEOUT_STEP_SEC) },
                            minusEnabled = settings.globalTimeoutSec > AppSettings.MIN_TIMEOUT_SEC,
                            plusEnabled = settings.globalTimeoutSec < AppSettings.MAX_TIMEOUT_SEC,
                        )
                        EcDivider(EcIconInset)
                        EcStepperRow(
                            title = "Streams",
                            description = "Maximum number of streams kept per plugin",
                            icon = Icons.Rounded.FilterList,
                            value = if (settings.maxStreamsPerProvider == AppSettings.UNLIMITED_STREAMS) "No cap" else settings.maxStreamsPerProvider.toString(),
                            onMinus = { onMaxStreams(AppSettings.stepStreams(settings.maxStreamsPerProvider, -1)) },
                            onPlus = { onMaxStreams(AppSettings.stepStreams(settings.maxStreamsPerProvider, +1)) },
                            minusEnabled = settings.maxStreamsPerProvider == AppSettings.UNLIMITED_STREAMS || settings.maxStreamsPerProvider > AppSettings.MIN_STREAMS,
                            plusEnabled = settings.maxStreamsPerProvider != AppSettings.UNLIMITED_STREAMS,
                        )
                    }
                }
            }

            SettingsPage.Resolution -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item {
                    EcCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("Resolutions", style = EcType.Body, fontWeight = FontWeight.Medium, color = EcColors.TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Only selected resolutions are kept.",
                                style = EcType.BodyMedium,
                                color = EcColors.TextMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            EcChipFlow {
                                Resolution.entries.forEach { res ->
                                    val selected = res.id in settings.enabledResolutions
                                    EcChip(text = res.label, selected = selected, onClick = { onResolution(res.id, !selected) })
                                }
                            }
                        }
                    }
                }
                item {
                    EcFootnote(
                        "All resolutions are enabled by default. “Others” includes streams with an undetected resolution"
                    )
                }
            }

            SettingsPage.Dns -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item {
                    EcCard {
                        DohProvider.entries.forEachIndexed { index, provider ->
                            if (index > 0) EcDivider()
                            EcRow(
                                title = provider.label,
                                onClick = { onDohProvider(provider.id) },
                                trailing = {
                                    if (settings.dohProvider == provider.id) {
                                        Icon(Icons.Rounded.Check, null, tint = EcColors.Accent, modifier = Modifier.size(20.dp))
                                    }
                                },
                            )
                        }
                        if (settings.dohProvider == DohProvider.Custom.id) {
                            EcDivider()
                            Column(Modifier.padding(16.dp)) {
                                EcTextField(
                                    value = dohUrl,
                                    onValueChange = { dohUrl = it },
                                    placeholder = "https://host/dns-query",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                                    onDone = { onDohCustomUrl(dohUrl) },
                                )
                            }
                        }
                    }
                }
                item {
                    val invalid = settings.dohProvider == DohProvider.Custom.id && !isValidDohUrl(settings.dohCustomUrl.trim())
                    EcFootnote(
                        "Enter an https:// DoH server address and press Done. Until a valid address is set, lookups fail and do not fall back to system DNS."
                    )
                }
            }

            SettingsPage.About -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item {
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                    }
                    val links = buildList {
                        add(AboutLink("Website", WEBSITE_URL.removePrefix("https://"), Icons.Rounded.Public, WEBSITE_URL))
                        if (SOURCE_CODE_URL.isNotBlank()) add(AboutLink("Source code", "View the project repository", Icons.Rounded.Code, SOURCE_CODE_URL))
                        add(AboutLink("Plugin repository", "Eclipsia plugin repository", Icons.Rounded.Extension, PLUGIN_REPOSITORY_URL))
                        if (DONATION_URL.isNotBlank()) add(AboutLink("Donate", "Support the development of Eclipsia", Icons.Rounded.Favorite, DONATION_URL))
                    }
                    EcCard {
                        EcRow(title = "Jisr", description = "Version $version", icon = Icons.Rounded.Info)
                        links.forEach { link ->
                            EcDivider(EcIconInset)
                            EcNavRow(
                                title = link.title,
                                description = link.description,
                                icon = link.icon,
                                onClick = { openUrl(context, link.url) },
                            )
                        }
                    }
                }
                item {
                    EcCard {
                        EcNavRow(
                            title = "Credits to Nuvio",
                            description = "The plugin runtime, sandbox and interface design are derived from NuvioMobile, licensed under GPL-3.0.",
                            icon = Icons.Rounded.Favorite,
                            onClick = { openUrl(context, NUVIO_URL) },
                        )
                    }
                }
            }

            SettingsPage.Data -> EcScreen {
                item { EcHeader(current.title, onBack = back) }
                item { EcButton("Erase all data", onClick = { confirmWipe = true }, style = EcButtonStyle.Danger) }
                item { EcFootnote("Stops the server and deletes installed plugins, settings and the encryption key.") }
            }
        }
    }

    if (confirmWipe) {
        EcDialog(
            title = "Erase all data?",
            message = "This action cannot be undone.",
            confirmText = "Erase",
            destructive = true,
            onDismiss = { confirmWipe = false },
            onConfirm = { confirmWipe = false; onWipe() },
        )
    }
}

private data class AboutLink(val title: String, val description: String, val icon: ImageVector, val url: String)

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun RootPage(settings: AppSettings, listState: LazyListState, open: (SettingsPage) -> Unit) {
    val dohLabel = DohProvider.entries.firstOrNull { it.id == settings.dohProvider }?.label.orEmpty()
    EcScreen(state = listState) {
        item { EcHeader(SettingsPage.Root.title) }
        item {
            EcCard {
                EcNavRow(
                    title = "TMDB",
                    description = if (settings.customTmdbKey) "Custom API key saved" else "Using built-in key",
                    icon = Icons.Rounded.Key,
                    onClick = { open(SettingsPage.Tmdb) },
                )
                EcDivider(EcIconInset)
                EcNavRow(
                    title = "Server",
                    description = "Port ${settings.port} • " + if (settings.localNetwork) "Local network" else "This device only",
                    icon = Icons.Rounded.Wifi,
                    onClick = { open(SettingsPage.Server) },
                )
                EcDivider(EcIconInset)
                EcNavRow(
                    title = "Plugins",
                    description = "${settings.concurrency} at once • ${settings.globalTimeoutSec} s timeout",
                    icon = Icons.Rounded.Extension,
                    onClick = { open(SettingsPage.Plugins) },
                )
                EcDivider(EcIconInset)
                EcNavRow(
                    title = "Resolution filter",
                    description = "${settings.enabledResolutions.size} selected",
                    icon = Icons.Rounded.FilterList,
                    onClick = { open(SettingsPage.Resolution) },
                )
                EcDivider(EcIconInset)
                EcNavRow(
                    title = "DNS over HTTPS",
                    description = dohLabel,
                    icon = Icons.Rounded.Language,
                    onClick = { open(SettingsPage.Dns) },
                )
            }
        }
        item {
            EcCard {
                EcNavRow(
                    title = "Data",
                    description = "Erase plugins, settings and keys",
                    icon = Icons.Rounded.Delete,
                    onClick = { open(SettingsPage.Data) },
                )
                EcDivider(EcIconInset)
                EcNavRow(
                    title = "About",
                    description = "Version, links and support",
                    icon = Icons.Rounded.Info,
                    onClick = { open(SettingsPage.About) },
                )
            }
        }
    }
}
