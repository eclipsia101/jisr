package org.jisr.app.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jisr.app.server.ServerState
import org.jisr.app.ui.components.EcButton
import org.jisr.app.ui.components.EcButtonStyle
import org.jisr.app.ui.components.EcCard
import org.jisr.app.ui.components.EcDivider
import org.jisr.app.ui.components.EcFootnote
import org.jisr.app.ui.components.EcIconInset
import org.jisr.app.ui.components.EcRow
import org.jisr.app.ui.components.EcScreen
import org.jisr.app.ui.components.EcSectionLabel
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcSpace
import org.jisr.app.ui.theme.EcType
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: ServerState,
    pluginCount: Int,
    hasTmdbKey: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val running = state as? ServerState.Running
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_500); copied = false } }

    val statusColor = when {
        running != null -> EcColors.Success
        state is ServerState.Failed -> EcColors.Danger
        state == ServerState.MissingKey || !hasTmdbKey -> EcColors.Warning
        state == ServerState.Starting -> EcColors.Warning
        else -> EcColors.TextMuted
    }
    val statusIcon = when {
        running != null -> Icons.Rounded.Check
        state is ServerState.Failed -> Icons.Rounded.ErrorOutline
        state == ServerState.MissingKey || !hasTmdbKey -> Icons.Rounded.Key
        else -> Icons.Rounded.PowerSettingsNew
    }
    val statusTitle = when (state) {
        is ServerState.Running -> "Running"
        ServerState.Starting -> "Starting\u2026"
        ServerState.MissingKey -> "TMDB key needed"
        is ServerState.Failed -> if (state.portInUse) "Port already in use" else "Couldn\u2019t start"
        ServerState.Stopped -> if (hasTmdbKey) "Stopped" else "TMDB key needed"
    }
    val statusMessage = when {
        running == null && !hasTmdbKey -> "Add your TMDB API key in Settings before starting the server."
        running == null -> "Start the server, then add the manifest to Nuvio."
        running.localNetwork -> "Reachable from your local network."
        else -> "Only this device can connect."
    }
    val cardShape = RoundedCornerShape(EcRadius.Card - 4.dp)

    EcScreen(state = listState, itemGap = 8.dp) {

        item {
            Text(
                text = "Jisr",
                style = EcType.Headline.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
                color = EcColors.TextPrimary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).background(statusColor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = statusTitle,
                    style = EcType.Headline.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                    color = EcColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusMessage,
                    style = EcType.Body,
                    color = EcColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(28.dp))
                when {
                    running != null -> EcButton(text = "Stop server", onClick = onStop, style = EcButtonStyle.Danger)
                    hasTmdbKey -> EcButton(
                        text = if (state == ServerState.Starting) "Starting\u2026" else "Start server",
                        onClick = onStart,
                        enabled = state != ServerState.Starting,
                    )
                    else -> EcButton(text = "Add TMDB key", onClick = onOpenSettings)
                }
            }
        }

        if (running != null) {
            item { EcSectionLabel("Manifest URL") }
            item {
                EcCard(shape = cardShape) {
                    EcRow(title = running.url)
                    EcDivider()
                    EcRow(
                        title = if (copied) "Copied" else "Copy URL",
                        icon = Icons.Rounded.ContentCopy,
                        onClick = {
                            copyPrivately(context, running.url)
                            copied = true
                        },
                    )
                }
            }
            item { EcFootnote("Add this URL in Nuvio [must start with http://]") }
        }

        item { EcSectionLabel("Status") }
        item {
            EcCard(shape = cardShape) {
                EcRow(
                    title = "Active plugins",
                    icon = Icons.Rounded.Extension,
                    trailing = { ValueText(pluginCount.toString()) },
                )
                EcDivider(EcIconInset)
                EcRow(
                    title = "TMDb key",
                    icon = Icons.Rounded.Key,
                    onClick = if (!hasTmdbKey) onOpenSettings else null,
                    trailing = {
                        ValueText(if (hasTmdbKey) "Set" else "Missing", if (hasTmdbKey) EcColors.TextMuted else EcColors.Warning)
                    },
                )
                if (running != null) {
                    EcDivider(EcIconInset)
                    EcRow(
                        title = "Address",
                        icon = Icons.Rounded.Wifi,
                        trailing = { ValueText("${running.host}:${running.port}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueText(text: String, color: Color = EcColors.TextMuted) {
    Text(text, style = EcType.BodyMedium, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
}

private fun copyPrivately(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("Jisr manifest", text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    manager.setPrimaryClip(clip)
}
