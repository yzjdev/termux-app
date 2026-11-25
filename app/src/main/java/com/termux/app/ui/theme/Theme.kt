package com.termux.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Termux brand red (#FF0000), reused as the Compose primary colour. */
val TermuxRed = Color(0xFFFF0000)
val TermuxRedDark = Color(0xFFC4001D)

private val LightColors = lightColorScheme(
    primary = TermuxRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF4A4A4F),
    error = Color(0xFFDC143C),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TermuxRedDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF930008),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFB0B0B5),
    error = Color(0xFFFF8A80),
    onError = Color.Black,
)

/**
 * Material 3 Expressive theme used by every Compose screen in the app module.
 *
 * Upgraded from the plain MaterialTheme to MaterialExpressiveTheme with the expressive motion
 * scheme (same as the reference Compose demo, material3 1.5.0). The brand colour scheme below is
 * kept instead of demo's expressiveLightColorScheme() so Termux keeps its red identity; only the
 * theme container and motion are expressive.
 *
 * The dark/light decision follows the system; the app's pre-Compose code drives
 * night mode through AppCompatDelegate, which in turn flips isSystemInDarkTheme().
 */
@Composable
fun TermuxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
