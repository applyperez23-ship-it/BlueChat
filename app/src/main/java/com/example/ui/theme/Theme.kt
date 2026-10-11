package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GhostCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = GhostPurple,
    onSecondary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4F378B),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = GhostGreen,
    onTertiary = Color(0xFF003920),
    background = GhostDarkBackground,
    onBackground = Color(0xFFE2E8F0),
    surface = GhostDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = GhostDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = GhostDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = GhostCyanDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF004D40),
    secondary = GhostViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = GhostGreen,
    onTertiary = Color.White,
    background = GhostLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = GhostLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = GhostLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = GhostLightBorder
)

@Composable
fun GhostMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep distinctive cyber mesh palette by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for compatibility with template
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    GhostMeshTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
