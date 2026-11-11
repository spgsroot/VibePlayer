package ru.spgsroot.vibeplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = InkText,
    primaryContainer = AmberContainer,
    onPrimaryContainer = Foam,
    secondary = Mist,
    onSecondary = Ink,
    background = Ink,
    onBackground = Foam,
    surface = InkSurface,
    onSurface = Foam,
    surfaceContainer = InkElevated,
    surfaceContainerHigh = InkElevated,
    outline = Mist
)

@Composable
fun VibePlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
