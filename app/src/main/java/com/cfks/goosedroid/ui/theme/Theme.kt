package com.cfks.goosedroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = TdsmBackground,
    surface = TdsmSurface,
    surfaceVariant = TdsmSurfaceElevated,
    primary = TdsmAccent,
    secondary = TdsmTextSecondary,
    onBackground = TdsmTextPrimary,
    onSurface = TdsmTextPrimary,
    outline = TdsmBorder
)

@Composable
fun GooseDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

