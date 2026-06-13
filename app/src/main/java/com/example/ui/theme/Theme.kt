package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElegantPrimary,
    onPrimary = ElegantPrimaryVariant,
    primaryContainer = ElegantPrimaryContainer,
    onPrimaryContainer = ElegantOnPrimary,
    secondary = ElegantSecondary,
    background = ElegantBg,
    surface = ElegantCardBg,
    surfaceVariant = ElegantHeaderBg,
    onBackground = ElegantTextPrimary,
    onSurface = ElegantTextPrimary,
    onSurfaceVariant = ElegantTextSecondary,
    outline = ElegantBorder
)

// We fallback to dark color scheme for light mode too to ensure the "Elegant Dark" design is consistently applied
private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to avoid overriding our handcrafted palette with dynamic background accent colors
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
