package org.jisr.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcSpace
import org.jisr.app.ui.theme.EcType

private val FloatingBarClearance = 96.dp

@Composable
fun EcScreen(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemGap: Dp = EcSpace.ListGap,
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        state = state,
        modifier = modifier
            .background(EcColors.Background)
            .fillMaxWidth()
            .fillMaxHeight(),
        contentPadding = PaddingValues(
            start = EcSpace.ScreenHorizontal,
            end = EcSpace.ScreenHorizontal,
            top = top + EcSpace.ScreenTop,
            bottom = bottom + FloatingBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(itemGap),
        content = content,
    )
}

@Composable
fun EcHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) EcCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Text(
            text = title,
            style = EcType.Headline,
            color = EcColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun EcCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = EcColors.Surface,
    tint: Color = EcColors.TextPrimary,
) {
    Box(
        modifier
            .size(size)
            .background(container, CircleShape)
            .border(0.5.dp, EcColors.BorderDefault, CircleShape)
            .ecClickable(shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(if (size < 40.dp) 18.dp else 20.dp))
    }
}

@Composable
fun EcSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = EcType.Caption,
        color = EcColors.TextMuted,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@Composable
fun EcFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = EcType.Small,
        color = EcColors.TextMuted,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
fun EcEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = EcType.Title, color = EcColors.TextPrimary, textAlign = TextAlign.Center)
        Text(message, style = EcType.BodyMedium, color = EcColors.TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
fun EcCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(EcRadius.Compact),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = EcColors.Surface,
        shape = shape,
        border = BorderStroke(0.5.dp, EcColors.BorderSubtle),
    ) {
        Column(content = content)
    }
}

@Composable
fun EcDivider(start: Dp = 16.dp, end: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = end),
        thickness = 0.5.dp,
        color = EcColors.Divider,
    )
}

@Composable
fun EcListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(EcRadius.Compact)
    Box(
        modifier
            .fillMaxWidth()
            .background(EcColors.Surface, shape)
            .border(0.5.dp, EcColors.BorderSubtle, shape)
            .clip(shape),
    ) { content() }
}
