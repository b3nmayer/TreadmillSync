package com.example.treadmillsync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = DeepCyan,
    tertiary = White,
    background = Black,
    surface = SurfaceGray,
    onPrimary = Black,
    onSecondary = White,
    onBackground = NeonCyan,
    onSurface = NeonCyan
)

@Composable
fun TreadmillSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}