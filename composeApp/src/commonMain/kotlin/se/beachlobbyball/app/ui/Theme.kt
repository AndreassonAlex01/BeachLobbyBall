package se.beachlobbyball.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sea blue for actions, sand for surfaces, and a warm orange accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF8B5000),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF006A60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9EF2E4),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFFFFBF6),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF0E0CF),
    onSurfaceVariant = Color(0xFF50453A),
    outline = Color(0xFF827568),
    outlineVariant = Color(0xFFD4C4B5),
    surfaceBright = Color(0xFFFFFBF6),
    surfaceDim = Color(0xFFE1D8CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2E9),
    surfaceContainer = Color(0xFFF5ECE3),
    surfaceContainerHigh = Color(0xFFEFE6DD),
    surfaceContainerHighest = Color(0xFFE9E1D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFFFB86F),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3C00),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFF82D5C8),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFF9EF2E4),
    background = Color(0xFF1A1714),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF1A1714),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF50453A),
    onSurfaceVariant = Color(0xFFD4C4B5),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF50453A),
    surfaceBright = Color(0xFF403B37),
    surfaceDim = Color(0xFF1A1714),
    surfaceContainerLowest = Color(0xFF14110E),
    surfaceContainerLow = Color(0xFF221E1A),
    surfaceContainer = Color(0xFF26221E),
    surfaceContainerHigh = Color(0xFF312C28),
    surfaceContainerHighest = Color(0xFF3C3733),
)

/** The app's colors, following the phone's light or dark setting. */
@Composable
fun BeachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
