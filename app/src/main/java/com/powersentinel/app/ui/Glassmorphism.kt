package com.powersentinel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.powersentinel.app.ui.theme.GlassDark
import com.powersentinel.app.ui.theme.GlassLight

fun Modifier.glassmorphism(shape: Shape): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val glassColor = if (isDark) GlassDark else GlassLight
    this.clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    glassColor.copy(alpha = if (isDark) 0.62f else 0.82f),
                    glassColor.copy(alpha = if (isDark) 0.36f else 0.48f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.22f else 0.74f),
                    Color.White.copy(alpha = 0.10f)
                )
            ),
            shape = shape
        )
}
