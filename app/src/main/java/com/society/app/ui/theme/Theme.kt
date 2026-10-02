package com.society.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceWhite,
    secondary = SecondaryTeal,
    onSecondary = SurfaceWhite,
    background = BackgroundLight,
    surface = SurfaceWhite,
    onSurface = TextPrimary
)

@Composable
fun SocietyAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
