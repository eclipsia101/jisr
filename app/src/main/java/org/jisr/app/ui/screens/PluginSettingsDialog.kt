package org.jisr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jisr.app.sandbox.PluginSettingField
import org.jisr.app.ui.components.EcChip
import org.jisr.app.ui.components.EcChipFlow
import org.jisr.app.ui.components.EcDialog
import org.jisr.app.ui.components.EcDivider
import org.jisr.app.ui.components.EcSwitchRow
import org.jisr.app.ui.components.EcTextField
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Composable
fun PluginSettingsDialog(
    pluginName: String,
    fields: List<PluginSettingField>?,
    saved: Map<String, JsonPrimitive>,
    onSave: (Map<String, JsonPrimitive>) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = remember(saved) { mutableStateMapOf<String, JsonPrimitive>().apply { putAll(saved) } }
    val editable = !fields.isNullOrEmpty()

    EcDialog(
        title = "$pluginName settings",
        confirmText = if (editable) "Save" else "Close",
        dismissText = if (editable) "Cancel" else null,
        onDismiss = onDismiss,
        onConfirm = {
            if (editable) {
                onSave(draft.filterValues { !(it.isString && it.contentOrNull.isNullOrBlank()) })
            }
            onDismiss()
        },
    ) {
        when {
            fields == null -> Text("Loading settings…", style = EcType.BodyMedium, color = EcColors.TextMuted)
            fields.isEmpty() -> Text(
                "This plugin has no settings, or they could not be loaded.",
                style = EcType.BodyMedium,
                color = EcColors.TextMuted,
            )
            else -> Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                fields.forEachIndexed { index, field -> SettingField(field, draft, dividerAbove = index > 0) }
            }
        }
    }
}

@Composable
private fun SettingField(field: PluginSettingField, draft: MutableMap<String, JsonPrimitive>, dividerAbove: Boolean) {
    when (field.type) {

        "header" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (dividerAbove) EcDivider(start = 0.dp, end = 0.dp)
            Text(field.label, style = EcType.Label, color = EcColors.TextPrimary)
        }

        "info" -> Text(field.label, style = EcType.BodyMedium, color = EcColors.TextMuted)

        "text" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(field.label, style = EcType.BodyMedium, fontWeight = FontWeight.Medium, color = EcColors.TextPrimary)
            EcTextField(
                value = draft[field.key]?.contentOrNull.orEmpty(),
                onValueChange = { draft[field.key] = JsonPrimitive(it) },
                placeholder = field.placeholder,
                visualTransformation = if (field.isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions.Default,
            )
            if (field.description.isNotBlank()) {
                Text(field.description, style = EcType.Small, color = EcColors.TextMuted)
            }
        }

        "toggle" -> Box(Modifier.background(EcColors.Card, RoundedCornerShape(EcRadius.Compact))) {
            EcSwitchRow(
                title = field.label,
                description = field.description.ifBlank { null },
                checked = draft[field.key]?.booleanOrNull ?: field.defaultBool,
                onCheckedChange = { draft[field.key] = JsonPrimitive(it) },
            )
        }

        "select" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(field.label, style = EcType.BodyMedium, fontWeight = FontWeight.Medium, color = EcColors.TextPrimary)
            if (field.description.isNotBlank()) {
                Text(field.description, style = EcType.Small, color = EcColors.TextMuted)
            }
            val current = draft[field.key]?.contentOrNull ?: field.defaultText
            EcChipFlow {
                field.options.forEach { option ->
                    EcChip(
                        text = option.label,
                        selected = option.value == current,
                        onClick = { draft[field.key] = JsonPrimitive(option.value) },
                    )
                }
            }
        }
    }
}
