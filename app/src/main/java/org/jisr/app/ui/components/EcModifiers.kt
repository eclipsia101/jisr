package org.jisr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius

@Composable
fun Modifier.ecFocusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .then(
            if (focused) Modifier.background(EcColors.FocusBackground, shape).border(2.dp, EcColors.FocusRing, shape)
            else Modifier,
        )
}

@Composable
fun Modifier.ecClickable(
    shape: Shape = RoundedCornerShape(EcRadius.Compact),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this
    .ecFocusRing(shape)
    .clip(shape)
    .clickable(enabled = enabled, onClick = onClick)
