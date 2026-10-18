package org.jisr.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jisr.app.ui.theme.EcColors
import org.jisr.app.ui.theme.EcRadius
import org.jisr.app.ui.theme.EcType

@Composable
fun EcDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    dismissText: String? = "Cancel",
    content: (@Composable () -> Unit)? = null,
) {
    val body: (@Composable () -> Unit)? = if (message != null || content != null) {
        @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message, style = EcType.BodyMedium, color = EcColors.TextSecondary)
                content?.invoke()
            }
        }
    } else null
    val dismissButton: (@Composable () -> Unit)? = if (dismissText != null) {
        @Composable {
            TextButton(onClick = onDismiss) { Text(dismissText, style = EcType.Label, color = EcColors.TextMuted) }
        }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EcColors.Surface,
        shape = RoundedCornerShape(EcRadius.Dialog),
        title = {
            EcWindowBlur()
            Text(title, style = EcType.Title, color = EcColors.TextPrimary)
        },
        text = body,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, style = EcType.Label, color = if (destructive) EcColors.Danger else EcColors.Accent)
            }
        },
        dismissButton = dismissButton,
    )
}
