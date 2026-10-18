package org.jisr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object EcColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1A1A1A)
    val Card = Color(0xFF222222)

    val TextPrimary = Color(0xFFF5F7F8)
    val TextSecondary = Color(0xFFB8BEC5)
    val TextMuted = Color(0xFF969CA3)

    val Accent = Color(0xFFF5F5F5)
    val AccentStrong = Color(0xFFE0E0E0)
    val OnAccent = Color(0xFF111111)

    val FocusRing = Color(0xFFFFFFFF)
    val FocusBackground = Color(0xFF303030)

    val BorderSubtle = Color(0x8C252A2A)
    val BorderDefault = Color(0xFF252A2A)
    val BorderStrong = Color(0xFF3A4040)

    val Divider = BorderDefault

    val Success = Color(0xFF66BB6A)
    val Warning = Color(0xFFFFC857)
    val Danger = Color(0xFFE36A8A)
    val Info = Color(0xFF42A5F5)

    val Glass = Color(0xFF1C1C1E)
}

object EcRadius {
    val Compact = 12.dp
    val Card = 24.dp
    val Button = 16.dp
    val Dialog = 28.dp
}

object EcSpace {
    val ScreenHorizontal = 16.dp
    val ScreenTop = 10.dp
    val ListGap = 12.dp
}

object EcType {
    private val font = FontFamily.SansSerif

    val Headline = TextStyle(fontFamily = font, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
    val Title = TextStyle(fontFamily = font, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(fontFamily = font, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val BodyMedium = TextStyle(fontFamily = font, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val Small = TextStyle(fontFamily = font, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val Caption = TextStyle(fontFamily = font, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    val Tab = TextStyle(fontFamily = font, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun JisrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EcColors.Accent,
            onPrimary = EcColors.OnAccent,
            primaryContainer = EcColors.FocusBackground,
            onPrimaryContainer = EcColors.TextPrimary,
            secondary = EcColors.AccentStrong,
            onSecondary = EcColors.OnAccent,
            background = EcColors.Background,
            onBackground = EcColors.TextPrimary,
            surface = EcColors.Surface,
            onSurface = EcColors.TextPrimary,
            surfaceVariant = EcColors.Card,
            onSurfaceVariant = EcColors.TextMuted,
            outline = EcColors.BorderDefault,
            error = EcColors.Danger,
            onError = Color(0xFFFCE5EC),
        ),
        typography = Typography(
            headlineLarge = EcType.Headline,
            titleLarge = EcType.Title,
            titleMedium = EcType.Label,
            bodyLarge = EcType.Body,
            bodyMedium = EcType.BodyMedium,
            bodySmall = EcType.Small,
            labelLarge = EcType.Label,
            labelMedium = EcType.Caption,
        ),
        content = content,
    )
}
