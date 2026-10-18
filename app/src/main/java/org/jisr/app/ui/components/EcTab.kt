package org.jisr.app.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

data class EcTab(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)
