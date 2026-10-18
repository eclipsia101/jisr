package org.jisr.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcType

@Composable
fun EcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(EcRadius.Compact),
        placeholder = { Text(placeholder, color = EcColors.TextMuted, style = EcType.Body, maxLines = 1) },
        textStyle = EcType.Body.copy(color = EcColors.TextPrimary),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EcColors.FocusRing,
            unfocusedBorderColor = EcColors.BorderDefault,
            focusedContainerColor = EcColors.Card,
            unfocusedContainerColor = EcColors.Card,
            cursorColor = EcColors.Accent,
        ),
    )
}

enum class EcButtonStyle { Primary, Secondary, Danger }

@Composable
fun EcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EcButtonStyle = EcButtonStyle.Primary,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(EcRadius.Button)
    val container = when (style) {
        EcButtonStyle.Primary -> EcColors.Accent
        EcButtonStyle.Secondary -> EcColors.Surface
        EcButtonStyle.Danger -> EcColors.Danger.copy(alpha = 0.14f)
    }
    val content = when (style) {
        EcButtonStyle.Primary -> EcColors.OnAccent
        EcButtonStyle.Secondary -> EcColors.TextPrimary
        EcButtonStyle.Danger -> EcColors.Danger
    }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height).ecFocusRing(shape),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.38f),
            disabledContentColor = content.copy(alpha = 0.38f),
        ),
        border = if (style == EcButtonStyle.Secondary) BorderStroke(0.5.dp, EcColors.BorderDefault) else null,
    ) {
        Text(text, style = EcType.Label, textAlign = TextAlign.Center)
    }
}

@Composable
fun EcChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    val background by animateColorAsState(if (selected) EcColors.Accent.copy(alpha = 0.15f) else EcColors.Card, label = "chipBg")
    val borderColor by animateColorAsState(if (selected) EcColors.Accent.copy(alpha = 0.75f) else EcColors.BorderDefault, label = "chipBorder")
    Box(
        modifier
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .ecClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = EcType.BodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) EcColors.TextPrimary else EcColors.TextMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
