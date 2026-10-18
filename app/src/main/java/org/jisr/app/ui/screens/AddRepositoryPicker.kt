package org.jisr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jisr.app.ui.components.EcCard
import org.jisr.app.ui.components.EcDivider
import org.jisr.app.ui.components.EcIconInset
import org.jisr.app.ui.components.EcRow
import org.jisr.app.ui.components.EcWindowBlur
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcType

const val ECLIPSIA_MANIFEST_URL = "https://eclipsia.dpdns.org/manifest.json"

@Composable
fun AddRepositoryPicker(
    eclipsiaInstalled: Boolean,
    onEclipsia: () -> Unit,
    onOthers: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        EcWindowBlur()
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val noRipple = remember { MutableInteractionSource() }

        Box(
            Modifier.fillMaxSize().clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(initialScale = 0.94f)) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 380.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    color = EcColors.Surface,
                    shape = RoundedCornerShape(EcRadius.Dialog),
                    border = BorderStroke(0.5.dp, EcColors.BorderDefault),
                    shadowElevation = 24.dp,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Add repository", style = EcType.Title, color = EcColors.TextPrimary)
                            Text("Choose where to get plugins from.", style = EcType.BodyMedium, color = EcColors.TextMuted)
                        }
                        EcCard(shape = RoundedCornerShape(EcRadius.Compact)) {
                            EcRow(
                                title = "Eclipsia",
                                description = if (eclipsiaInstalled) "Already installed" else "by App Developer",
                                icon = Icons.Rounded.Verified,
                                enabled = !eclipsiaInstalled,
                                onClick = onEclipsia,
                            ) {
                                Icon(
                                    if (eclipsiaInstalled) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    null,
                                    tint = EcColors.TextMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            EcDivider(EcIconInset)
                            EcRow(
                                title = "Others",
                                description = "Paste a manifest link",
                                icon = Icons.Rounded.Link,
                                onClick = onOthers,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    null,
                                    tint = EcColors.TextMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
