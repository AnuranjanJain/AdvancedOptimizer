package com.powersentinel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.powersentinel.app.ui.theme.GlassDark
import com.powersentinel.app.ui.theme.GlassLight

fun Modifier.glassmorphism(shape: Shape): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val glassColor = if (isDark) GlassDark else GlassLight
    this.clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    glassColor.copy(alpha = 0.5f),
                    glassColor.copy(alpha = 0.2f)
                )
            )
        )
}
