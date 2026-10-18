package org.jisr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcType

val EcIconInset: Dp = 66.dp

@Composable
fun EcIconChip(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = EcColors.Accent, size: Dp = 36.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(EcRadius.Compact))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun EcRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val base = if (onClick != null) modifier.fillMaxWidth().ecClickable(enabled = enabled, onClick = onClick)
    else modifier.fillMaxWidth()
    Row(
        base
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.52f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            EcIconChip(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = EcType.Body, fontWeight = FontWeight.Medium, color = EcColors.TextPrimary)
            if (!description.isNullOrBlank()) {
                Text(description, style = EcType.BodyMedium, color = EcColors.TextMuted)
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun EcNavRow(
    title: String,
    description: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EcRow(title = title, modifier = modifier, description = description, icon = icon, onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = EcColors.TextMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun EcSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,

    beforeSwitch: (@Composable RowScope.() -> Unit)? = null,
) {
    EcRow(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
    ) {
        beforeSwitch?.invoke(this)
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EcColors.OnAccent,
                checkedTrackColor = EcColors.Accent,
                checkedBorderColor = EcColors.Accent,
                uncheckedThumbColor = EcColors.TextMuted,
                uncheckedTrackColor = EcColors.BorderDefault,
                uncheckedBorderColor = EcColors.BorderStrong,
            ),
        )
    }
}

@Composable
fun EcStepperRow(
    title: String,
    description: String,
    icon: ImageVector,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EcIconChip(icon)
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = EcType.Body,
                fontWeight = FontWeight.Medium,
                color = EcColors.TextPrimary,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            StepButton(Icons.Rounded.Remove, "Decrease", minusEnabled, onMinus)
            Text(
                value,
                style = EcType.Label,
                color = EcColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 56.dp),
            )
            StepButton(Icons.Rounded.Add, "Increase", plusEnabled, onPlus)
        }
        Text(description, style = EcType.BodyMedium, color = EcColors.TextMuted)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(EcColors.Accent.copy(alpha = if (enabled) 0.12f else 0.05f), CircleShape)
            .ecClickable(shape = CircleShape, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (enabled) EcColors.Accent else EcColors.TextMuted.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}
