package org.jisr.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import org.jisr.app.data.AddRepositoryResult
import org.jisr.app.data.PluginInfo
import org.jisr.app.data.RepositoryInfo
import org.jisr.app.sandbox.PluginSettingField
import org.jisr.app.ui.theme.EcColors
import kotlinx.serialization.json.JsonPrimitive
import org.jisr.app.ui.components.EcButton
import org.jisr.app.ui.components.EcButtonStyle
import org.jisr.app.ui.components.EcCard
import org.jisr.app.ui.components.EcChip
import org.jisr.app.ui.components.EcCircleButton
import org.jisr.app.ui.components.EcDialog
import org.jisr.app.ui.components.EcDivider
import org.jisr.app.ui.components.EcEmptyState
import org.jisr.app.ui.components.EcFootnote
import org.jisr.app.ui.components.EcListCard
import org.jisr.app.ui.components.EcHeader
import org.jisr.app.ui.components.EcIconInset
import org.jisr.app.ui.components.EcRow
import org.jisr.app.ui.components.EcScreen
import org.jisr.app.ui.components.EcSectionLabel
import org.jisr.app.ui.components.EcSwitchRow
import org.jisr.app.ui.components.EcTextField

@Composable
fun PluginsScreen(
    plugins: List<PluginInfo>,
    repositories: List<RepositoryInfo>,
    working: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onSetManyEnabled: (List<String>, Boolean) -> Unit,
    onAddRepository: (String, (AddRepositoryResult) -> Unit) -> Unit,
    onRefreshAll: () -> Unit,
    onRemoveRepository: (String) -> Unit,
    onLoadSettings: (String, (List<PluginSettingField>, Map<String, JsonPrimitive>) -> Unit) -> Unit,
    onSaveSettings: (String, Map<String, JsonPrimitive>) -> Unit,

    expandedRepos: SnapshotStateList<String>,
    listState: LazyListState = rememberLazyListState(),
) {
    var showPicker by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<RepositoryInfo?>(null) }
    var settingsFor by remember { mutableStateOf<PluginInfo?>(null) }
    var settingsLayout by remember { mutableStateOf<List<PluginSettingField>?>(null) }
    var settingsSaved by remember { mutableStateOf<Map<String, JsonPrimitive>>(emptyMap()) }

    fun openSettings(plugin: PluginInfo) {
        settingsFor = plugin
        settingsLayout = null
        settingsSaved = emptyMap()
        onLoadSettings(plugin.id) { layout, saved ->

            if (settingsFor?.id == plugin.id) {
                settingsSaved = saved
                settingsLayout = layout
            }
        }
    }

    val sortedPlugins = remember(plugins) {
        plugins.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { pluginLabel(it).title.trim() })
    }
    val groupByRepository = repositories.size > 1
    val enableTargets = plugins.filter { it.manifestEnabled && !it.enabled }.map { it.id }
    val disableTargets = plugins.filter { it.enabled }.map { it.id }

    fun install(entered: String) {
        onAddRepository(entered) { result ->
            message = when (result) {
                is AddRepositoryResult.Added -> null
                AddRepositoryResult.InvalidUrl -> "That doesn’t look like a valid link."
                AddRepositoryResult.AlreadyAdded -> "This repository is already installed."
                AddRepositoryResult.Unreachable -> "Couldn’t download the manifest. Check the link and your connection."
                AddRepositoryResult.NotAManifest -> "The link didn’t return a plugin manifest."
                AddRepositoryResult.NoPlugins -> "The manifest has no usable plugins for this app."
            }
        }
    }

    fun submit() {
        val entered = url
        showAdd = false
        url = ""
        install(entered)
    }

    EcScreen(state = listState, itemGap = 8.dp) {
        item {
            EcHeader(
                title = "Plugins",
                actions = { EcCircleButton(Icons.Rounded.Add, "Add repository", { showPicker = true }) },
            )
        }

        if (repositories.isEmpty()) {
            item {
                EcEmptyState(
                    "No plugins",
                    if (working) "Installing…" else "Tap + to add plugins",
                )
            }
        } else {
            item { EcSectionLabel("Repositories") }
            item {
                EcCard {
                    repositories.forEachIndexed { index, repo ->
                        if (index > 0) EcDivider(EcIconInset)
                        EcRow(
                            title = repo.name,
                            description = "${repo.pluginCount} plugin${if (repo.pluginCount == 1) "" else "s"}" +
                                (if (repo.version.isNotBlank()) " • v${repo.version}" else "") +
                                "\n${repo.manifestUrl}",
                            icon = Icons.Rounded.Extension,
                        ) {
                            EcCircleButton(
                                Icons.Rounded.DeleteOutline,
                                "Remove ${repo.name}",
                                { pendingRemove = repo },
                                size = 36.dp,
                                container = EcColors.Card,
                                tint = EcColors.Danger,
                            )
                        }
                    }
                }
            }
            item { EcFootnote("Removing a repository also deletes its plugins") }
            item {
                EcButton(
                    text = if (working) "Working…" else "Refresh all",
                    onClick = onRefreshAll,
                    style = EcButtonStyle.Secondary,
                    enabled = !working,
                    height = 46.dp,
                )
            }

            item { EcSectionLabel("Installed") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EcButton(
                        text = "Enable all",
                        onClick = { onSetManyEnabled(enableTargets, true) },
                        modifier = Modifier.weight(1f),
                        style = EcButtonStyle.Secondary,
                        enabled = !working && enableTargets.isNotEmpty(),
                        height = 46.dp,
                    )
                    EcButton(
                        text = "Disable all",
                        onClick = { onSetManyEnabled(disableTargets, false) },
                        modifier = Modifier.weight(1f),
                        style = EcButtonStyle.Secondary,
                        enabled = !working && disableTargets.isNotEmpty(),
                        height = 46.dp,
                    )
                }
            }
            if (!groupByRepository) {

                itemsIndexed(sortedPlugins, key = { index, plugin -> "${plugin.id}#$index" }, contentType = { _, _ -> "plugin" }) { _, plugin ->
                    EcListCard(Modifier.animateItem()) {
                        PluginSwitchRow(plugin, onToggle, ::openSettings)
                    }
                }
            } else {

                val byRepository = sortedPlugins.groupBy { it.repositoryUrl }
                repositories.forEach { repo ->
                    val list = byRepository[repo.manifestUrl].orEmpty()
                    val open = repo.manifestUrl in expandedRepos
                    item(key = "repo:${repo.manifestUrl}", contentType = "repo") {
                        EcCard(Modifier.animateItem()) {
                            val rotation by animateFloatAsState(if (open) 180f else 0f, label = "repoChevron")
                            EcRow(
                                title = repo.name,
                                description = "${list.count { it.enabled }} of ${list.size} enabled",
                                icon = Icons.Rounded.Extension,
                                onClick = {
                                    if (open) expandedRepos.remove(repo.manifestUrl) else expandedRepos.add(repo.manifestUrl)
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.ExpandMore,
                                    if (open) "Collapse" else "Expand",
                                    tint = EcColors.TextMuted,
                                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
                                )
                            }
                        }
                    }
                    if (open) {
                        item(key = "repo-actions:${repo.manifestUrl}", contentType = "repo-actions") {
                            Row(
                                Modifier.animateItem().repoGuide().padding(start = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                EcChip("Enable all", selected = false, onClick = {
                                    onSetManyEnabled(list.filter { it.manifestEnabled && !it.enabled }.map { it.id }, true)
                                })
                                EcChip("Disable all", selected = false, onClick = {
                                    onSetManyEnabled(list.filter { it.enabled }.map { it.id }, false)
                                })
                            }
                        }
                        itemsIndexed(list, key = { index, plugin -> "${plugin.id}#$index" }, contentType = { _, _ -> "plugin" }) { _, plugin ->

                            EcListCard(Modifier.animateItem().repoGuide().padding(start = 12.dp)) {
                                PluginSwitchRow(plugin, onToggle, ::openSettings)
                            }
                        }
                    }
                }
            }
            item {
                EcFootnote(
                    "Plugins run in an isolated JavaScript sandbox with NO file, device or LAN access, " +
                        "Only add repositories you trust.",
                )
            }
        }
    }

    if (showPicker) {
        AddRepositoryPicker(
            eclipsiaInstalled = repositories.any { it.manifestUrl.equals(ECLIPSIA_MANIFEST_URL, ignoreCase = true) },
            onEclipsia = { showPicker = false; install(ECLIPSIA_MANIFEST_URL) },
            onOthers = { showPicker = false; showAdd = true },
            onDismiss = { showPicker = false },
        )
    }
    if (showAdd) {
        EcDialog(
            title = "Add repository",
            message = "Paste a manifest link",
            confirmText = "Install",
            onDismiss = { showAdd = false; url = "" },
            onConfirm = { if (url.isNotBlank()) submit() },
        ) {
            EcTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = "https://example.com/manifest.json",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                onDone = { if (url.isNotBlank()) submit() },
            )
        }
    }
    message?.let {
        EcDialog(
            title = "Couldn’t install",
            message = it,
            onDismiss = { message = null },
            onConfirm = { message = null },
            confirmText = "OK",
            dismissText = null,
        )
    }
    settingsFor?.let { plugin ->
        PluginSettingsDialog(
            pluginName = pluginLabel(plugin).title,
            fields = settingsLayout,
            saved = settingsSaved,
            onSave = { values -> onSaveSettings(plugin.id, values) },
            onDismiss = { settingsFor = null; settingsLayout = null },
        )
    }
    pendingRemove?.let { repo ->
        EcDialog(
            title = "Remove ${repo.name}?",
            message = "Its plugins will be deleted from this device.",
            confirmText = "Remove",
            destructive = true,
            onDismiss = { pendingRemove = null },
            onConfirm = { onRemoveRepository(repo.manifestUrl); pendingRemove = null },
        )
    }
}

@Composable
private fun PluginSwitchRow(plugin: PluginInfo, onToggle: (String, Boolean) -> Unit, onOpenSettings: (PluginInfo) -> Unit) {
    val label = pluginLabel(plugin)
    EcSwitchRow(
        title = label.title,
        description = label.subtitle,
        icon = Icons.Rounded.Extension,
        checked = plugin.enabled,
        onCheckedChange = { onToggle(plugin.id, it) },
        beforeSwitch = {
            if (plugin.hasSettings) {
                EcCircleButton(
                    Icons.Rounded.Settings,
                    "${label.title} settings",
                    { onOpenSettings(plugin) },
                    size = 36.dp,
                    container = EcColors.Card,
                    tint = EcColors.TextSecondary,
                )
                Spacer(Modifier.width(10.dp))
            }
        },
    )
}

private fun Modifier.repoGuide(): Modifier = drawBehind {
    val x = 4.dp.toPx()
    drawLine(
        color = EcColors.Divider,
        start = Offset(x, 0f),
        end = Offset(x, size.height + 8.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
    )
}

private data class PluginLabel(val title: String, val subtitle: String)

private fun pluginLabel(plugin: PluginInfo): PluginLabel {
    if (plugin.repositoryUrl.equals(ECLIPSIA_MANIFEST_URL, ignoreCase = true)) {
        val parts = plugin.description.split('•').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) return PluginLabel(parts.first(), parts.drop(1).joinToString(" • "))
    }
    return PluginLabel(plugin.name, plugin.description.ifBlank { fallbackInfo(plugin) })
}

private fun fallbackInfo(plugin: PluginInfo): String = buildString {
    if (plugin.version.isNotBlank()) append("v${plugin.version} • ")
    append(plugin.supportedTypes.joinToString(", ") { if (it == "tv") "TV" else "Movies" }.ifBlank { "Any" })
}
